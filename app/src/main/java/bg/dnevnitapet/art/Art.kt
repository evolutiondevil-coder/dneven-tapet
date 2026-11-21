package bg.dnevnitapet.art

import android.content.Context
import bg.dnevnitapet.data.JsonFileStore
import bg.dnevnitapet.model.Candidate
import bg.dnevnitapet.model.ImageVariant
import bg.dnevnitapet.net.Http
import bg.dnevnitapet.sources.Text
import bg.dnevnitapet.sources.arr
import bg.dnevnitapet.sources.child
import bg.dnevnitapet.sources.int
import bg.dnevnitapet.sources.obj
import bg.dnevnitapet.sources.parseJson
import bg.dnevnitapet.sources.str
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import java.io.File

/** A famous painting in the public domain (Art Institute of Chicago). */
@Serializable
data class Artwork(
    val id: Int,
    val title: String,
    val artist: String,
    val date: String,
    val imageId: String,
    val width: Int,
    val height: Int,
    val story: String,
) {
    /** The painting for the screen (843 px wide, the size the museum recommends). */
    val imageUrl: String get() = "https://www.artic.edu/iiif/2/$imageId/full/843,/0/default.jpg"

    val pageUrl: String get() = "https://www.artic.edu/artworks/$id"

    /** As a picture of the app ("Сложи за тапет"): the largest size the museum serves for everyone. */
    fun candidate() = Candidate(
        key = "art:aic:$id",
        sourceId = SOURCE_ID,
        title = title,
        description = story,
        author = listOf(artist, date).filter { it.isNotBlank() }.joinToString(", "),
        copyright = "Public domain · Art Institute of Chicago",
        infoUrl = pageUrl,
        variants = listOf(
            ImageVariant(
                "https://www.artic.edu/iiif/2/$imageId/full/1686,/0/default.jpg",
                1686,
                if (width > 0) (height.toLong() * 1686 / width).toInt() else 0,
            ),
        ),
    )

    companion object {
        const val SOURCE_ID = "art"
        const val MUSEUM = "Институт по изкуствата, Чикаго"
    }
}

/** Reads the museum's search answer. Pure logic, unit-tested. */
object AicParser {
    fun parse(json: String): List<Artwork> {
        val data = parseJson(json).obj()?.get("data").arr() ?: return emptyList()
        return data.mapNotNull { el ->
            val o = el.obj() ?: return@mapNotNull null
            if (o["is_public_domain"]?.toString() != "true") return@mapNotNull null
            if (o.str("classification_title")?.lowercase()?.contains("painting") != true) return@mapNotNull null
            val imageId = o.str("image_id")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val story = o.str("short_description")?.takeIf { it.isNotBlank() }
                ?: o.str("description")?.let(Text::stripHtml).orEmpty()
            Artwork(
                id = o.int("id") ?: return@mapNotNull null,
                title = Text.decodeEntities(o.str("title").orEmpty()).trim(),
                artist = o.str("artist_display").orEmpty().lineSequence().firstOrNull().orEmpty().trim(),
                date = o.str("date_display").orEmpty().trim(),
                imageId = imageId,
                width = o.child("thumbnail").int("width") ?: 0,
                height = o.child("thumbnail").int("height") ?: 0,
                story = Text.clip(Text.stripHtml(story), 700),
            )
        }
    }

    fun url(page: Int) =
        "https://api.artic.edu/api/v1/artworks/search?query%5Bterm%5D%5Bis_boosted%5D=true" +
            "&fields=id,title,artist_display,date_display,image_id,is_public_domain,classification_title,short_description,description,thumbnail" +
            "&limit=40&page=$page"
}

@Serializable
data class ArtState(val pool: List<Artwork> = emptyList(), val shown: List<Int> = emptyList(), val page: Int = 1)

/** "Пауза за изкуство": famous paintings, each shown once, a few fetched at a time. */
class ArtCollection(context: Context, private val http: Http) {
    private val store = JsonFileStore(File(context.filesDir, "art.json"), ArtState.serializer()) { ArtState() }
    private val mutex = Mutex()

    /** The next painting not shown yet (fetches more from the museum when few are left). */
    suspend fun next(): Artwork? = mutex.withLock {
        var st = store.value
        var tries = 0
        while (st.pool.count { it.id !in st.shown } < 3 && tries < 3) {
            tries++
            val page = st.page
            val fetched = runCatching { AicParser.parse(http.getText(AicParser.url(page))) }.getOrNull() ?: break
            st = store.update { s ->
                val known = s.pool.map { it.id }.toSet()
                // After the last page the museum's list starts again.
                s.copy(pool = (s.pool + fetched.filter { it.id !in known }).takeLast(120), page = if (fetched.isEmpty()) 1 else page + 1)
            }
            if (fetched.isEmpty() && page == 1) break
        }
        val unseen = st.pool.filter { it.id !in st.shown }
        unseen.firstOrNull() ?: st.pool.firstOrNull()?.also { store.update { s -> s.copy(shown = emptyList()) } }
    }

    suspend fun markShown(id: Int) {
        store.update { it.copy(shown = (it.shown + id).distinct().takeLast(400)) }
    }
}
