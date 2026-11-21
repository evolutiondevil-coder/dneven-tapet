package bg.dnevnitapet.art

import android.content.Context
import bg.dnevnitapet.data.JsonFileStore
import bg.dnevnitapet.i18n.tr
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
import java.net.URLEncoder

/** A famous painting in the public domain (Wikimedia Commons' featured paintings). */
@Serializable
data class Artwork(
    /** The file's page title on Wikimedia Commons, e.g. "File:Vincent van Gogh - The Starry Night.jpg". */
    val id: String,
    val title: String,
    val artist: String,
    val date: String,
    /** About 1280 px wide, for the screen. */
    val imageUrl: String,
    /** The largest rendition (up to 3840 px) for the wallpaper. */
    val fullUrl: String,
    val width: Int,
    val height: Int,
    val story: String,
    val pageUrl: String,
) {
    /** As a picture of the app ("Сложи за тапет"): the largest rendition, the screen one as a fallback. */
    fun candidate() = Candidate(
        key = "art:" + id.removePrefix("File:").replace(' ', '_'),
        sourceId = SOURCE_ID,
        title = title,
        description = story,
        author = listOf(artist, date).filter { it.isNotBlank() }.joinToString(", "),
        copyright = "Public domain · Wikimedia Commons",
        infoUrl = pageUrl,
        variants = listOf(
            ImageVariant(fullUrl, width, height),
            ImageVariant(imageUrl, 1280, if (width > 0) (height.toLong() * 1280 / width).toInt() else 0),
        ).distinctBy { it.url },
    )

    companion object {
        const val SOURCE_ID = "art"
        val MUSEUM: String get() = "Wikimedia Commons"
    }
}

/** Reads the featured paintings of Wikimedia Commons (MediaWiki API). Pure logic, unit-tested. */
object CommonsArtParser {
    private const val API = "https://commons.wikimedia.org/w/api.php"
    private val px = Regex("/\\d+px-")

    /** Categories tried in this order (the first that answers with paintings is used). */
    val categories = listOf("Category:Featured_pictures_of_paintings", "Category:Quality_images_of_paintings")

    /** World-famous paintings, shown first (names on Wikimedia Commons; a missing one is skipped). */
    val famous = listOf(
        "File:Van Gogh - Starry Night - Google Art Project.jpg",
        "File:Mona Lisa, by Leonardo da Vinci, from C2RMF retouched.jpg",
        "File:1665 Girl with a Pearl Earring.jpg",
        "File:Tsunami by hokusai 19th century.jpg",
        "File:Sandro Botticelli - La nascita di Venere - Google Art Project - edited.jpg",
        "File:Claude Monet, Impression, soleil levant.jpg",
        "File:Caspar David Friedrich - Wanderer above the sea of fog.jpg",
        "File:The Kiss - Gustav Klimt - Google Cultural Institute.jpg",
        "File:Pieter Bruegel the Elder - Hunters in the Snow (Winter) - Google Art Project.jpg",
        "File:Red Fuji southern wind clear morning.jpg",
        "File:A Sunday on La Grande Jatte, Georges Seurat, 1884.jpg",
        "File:Vincent Willem van Gogh - Cafe Terrace at Night (Yorck).jpg",
        "File:Hovhannes Aivazovsky - The Ninth Wave - Google Art Project.jpg",
        "File:Vincent van Gogh - Wheatfield with crows - Google Art Project.jpg",
        "File:Johannes Vermeer - Het melkmeisje - Google Art Project.jpg",
        "File:Edvard Munch, 1893, The Scream, oil, tempera and pastel on cardboard, 91 x 73 cm, National Gallery of Norway.jpg",
        "File:Claude Monet - Water Lilies - Google Art Project.jpg",
        "File:Vincent van Gogh - Almond blossom - Google Art Project.jpg",
        "File:Pierre-Auguste Renoir, Le Moulin de la Galette.jpg",
        "File:Rembrandt van Rijn-De Nachtwacht-1642.jpg",
        "File:Ivan Shishkin - Morning in a Pine Forest.jpg",
        "File:Van Gogh - Sunflowers (1888, National Gallery London).jpg",
        "File:Vincent van Gogh - De slaapkamer - Google Art Project.jpg",
        "File:Claude Monet - Woman with a Parasol - Madame Monet and Her Son - Google Art Project.jpg",
        "File:Turner, J. M. W. - The Fighting Téméraire tugged to her last Berth to be broken.jpg",
    )

    /** Pictures that do not suit a light "pause" (e.g. depictions some find offensive) are left out. */
    private val leftOut = listOf("muhammad", "prophet")

    fun titlesUrl(titles: List<String>): String =
        "$API?action=query&format=json&formatversion=2&redirects=1&titles=" + enc(titles.joinToString("|")) +
            "&prop=imageinfo&iiprop=" + enc("url|size|mime|extmetadata") + "&iiurlwidth=3840" +
            "&iiextmetadatafilter=" + enc("ImageDescription|Artist|ObjectName|DateTimeOriginal|LicenseShortName") +
            "&iiextmetadatalanguage=en"

    fun url(category: String, cont: String?): String =
        "$API?action=query&format=json&formatversion=2&generator=categorymembers&gcmtype=file&gcmlimit=30" +
            "&gcmtitle=" + enc(category) + (if (cont != null) "&gcmcontinue=" + enc(cont) else "") +
            "&prop=imageinfo&iiprop=" + enc("url|size|mime|extmetadata") + "&iiurlwidth=3840" +
            "&iiextmetadatafilter=" + enc("ImageDescription|Artist|ObjectName|DateTimeOriginal|LicenseShortName") +
            "&iiextmetadatalanguage=en"

    /** @return the paintings and where the list goes on (null at its end). */
    fun parse(json: String): Pair<List<Artwork>, String?> {
        val root = parseJson(json).obj() ?: return emptyList<Artwork>() to null
        val cont = root.child("continue").str("gcmcontinue")
        val pages = root.child("query")?.get("pages").arr() ?: return emptyList<Artwork>() to cont
        val list = pages.mapNotNull { el ->
            val page = el.obj() ?: return@mapNotNull null
            val title = page.str("title") ?: return@mapNotNull null
            if (leftOut.any { title.lowercase().contains(it) }) return@mapNotNull null
            val ii = page["imageinfo"].arr()?.firstOrNull().obj() ?: return@mapNotNull null
            val meta = ii.child("extmetadata")
            fun value(name: String) = Text.stripHtml(meta.child(name).str("value").orEmpty()).trim()
            if (!value("LicenseShortName").lowercase().contains("public domain")) return@mapNotNull null
            val mime = ii.str("mime").orEmpty()
            val width = ii.int("width") ?: 0
            val height = ii.int("height") ?: 0
            if (width < 1200 || height < 900) return@mapNotNull null
            // Without the tracking ending the API adds ("?utm_source=…").
            val original = clean(ii.str("url") ?: return@mapNotNull null)
            val thumb = ii.str("thumburl")?.let(::clean)?.takeIf { px.containsMatchIn(it) }
            if (thumb == null && mime != "image/jpeg" && mime != "image/png") return@mapNotNull null
            // Always a standard-size rendition: Wikimedia limits downloads of the original files.
            val fullW = when {
                thumb != null -> ii.int("thumbwidth") ?: 3840
                width >= 1920 -> 1920
                else -> 1280
            }
            val full = thumb ?: thumbOf(original, fullW)
            val fullH = (height.toLong() * fullW / width).toInt()
            val screen = full.replace(px, "/1280px-")
            val fileName = title.removePrefix("File:").substringBeforeLast('.').replace(" - Google Art Project", "").trim()
            Artwork(
                id = title,
                title = Text.clip(value("ObjectName").lineSequence().firstOrNull().orEmpty().ifBlank { fileName }, 120),
                artist = Text.clip(value("Artist").lineSequence().firstOrNull().orEmpty().substringBefore(" (").trim(), 80),
                date = Text.clip(value("DateTimeOriginal").lineSequence().firstOrNull().orEmpty(), 40),
                imageUrl = screen,
                fullUrl = full,
                width = fullW,
                height = fullH,
                story = Text.clip(value("ImageDescription"), 700),
                pageUrl = ii.str("descriptionurl").orEmpty(),
            )
        }
        return list to cont
    }

    private fun clean(url: String) = url.substringBefore("?utm_").substringBefore("&utm_")

    /** "…/commons/a/ab/Name.jpg" → "…/commons/thumb/a/ab/Name.jpg/1280px-Name.jpg". */
    fun thumbOf(original: String, width: Int): String {
        val name = original.substringAfterLast('/')
        return original.replace("/commons/", "/commons/thumb/") + "/${width}px-$name"
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}

@Serializable
data class ArtState(
    val pool: List<Artwork> = emptyList(),
    val shown: List<String> = emptyList(),
    /** Which category and where its list goes on. */
    val category: Int = 0,
    val cont: String? = null,
    /** The famous paintings were fetched (they come first). */
    val famousDone: Boolean = false,
)

/** "Пауза за изкуство": famous paintings, each shown once, a few fetched at a time. */
class ArtCollection(context: Context, private val http: Http) {
    private val store = JsonFileStore(File(context.filesDir, "art-commons.json"), ArtState.serializer()) { ArtState() }
    private val mutex = Mutex()

    /** The next painting not shown yet (fetches more when few are left). */
    suspend fun next(): Artwork? = mutex.withLock {
        var st = store.value
        var tries = 0
        if (!st.famousDone) {
            val famous = runCatching {
                CommonsArtParser.famous.chunked(25).flatMap { CommonsArtParser.parse(http.getText(CommonsArtParser.titlesUrl(it))).first }
            }.getOrNull()
            if (famous != null) st = store.update { s -> s.copy(pool = famous.shuffled() + s.pool.filter { p -> famous.none { it.id == p.id } }, famousDone = true) }
        }
        while (st.pool.count { it.id !in st.shown } < 3 && tries < 4) {
            tries++
            val category = CommonsArtParser.categories.getOrElse(st.category) { CommonsArtParser.categories.first() }
            val (fetched, cont) = runCatching { CommonsArtParser.parse(http.getText(CommonsArtParser.url(category, st.cont))) }.getOrNull() ?: break
            st = store.update { s ->
                val known = s.pool.map { it.id }.toSet()
                when {
                    // This category has nothing usable: the next one.
                    fetched.isEmpty() && s.cont == null -> s.copy(category = (s.category + 1) % CommonsArtParser.categories.size)
                    // At the end of the list it starts again.
                    else -> s.copy(pool = (s.pool + fetched.filter { it.id !in known }).takeLast(150), cont = cont)
                }
            }
        }
        val unseen = st.pool.filter { it.id !in st.shown }
        unseen.firstOrNull() ?: st.pool.firstOrNull()?.also { store.update { s -> s.copy(shown = emptyList()) } }
    }

    suspend fun markShown(id: String) {
        store.update { it.copy(shown = (it.shown + id).distinct().takeLast(400)) }
    }
}
