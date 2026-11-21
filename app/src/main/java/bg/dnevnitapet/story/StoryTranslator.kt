package bg.dnevnitapet.story

import android.content.Context
import android.net.ConnectivityManager
import bg.dnevnitapet.data.JsonFileStore
import bg.dnevnitapet.model.Candidate
import bg.dnevnitapet.model.Settings
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Serializable
data class TranslationCache(val entries: Map<String, String> = emptyMap())

/**
 * Titles and stories of the pictures in Bulgarian: on-device translation from English (ML Kit).
 * The Bulgarian language pack (~30 MB) is downloaded once, over Wi-Fi unless the user asks;
 * after that everything works offline. Translations are kept, so each text is translated once.
 */
class StoryTranslator(private val context: Context, private val settings: JsonFileStore<Settings>) {
    private val store = JsonFileStore(File(context.filesDir, "translations.json"), TranslationCache.serializer()) { TranslationCache() }

    /** Every translation made so far (original text → Bulgarian). */
    val cache: StateFlow<TranslationCache> = store.state

    private val _ready = MutableStateFlow(false)

    /** The language pack is on the phone. */
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private val mutex = Mutex()
    private var client: Translator? = null

    private fun client(): Translator = client ?: Translation.getClient(
        TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)
            .setTargetLanguage(TranslateLanguage.BULGARIAN)
            .build(),
    ).also { client = it }

    fun cached(text: String): String? = store.value.entries[text.trim()]

    /** The title as shown in the app: Bulgarian when already translated. */
    fun title(c: Candidate): String = StoryText.title(c, store.value.entries)

    suspend fun isReady(): Boolean {
        val ok = runCatching {
            RemoteModelManager.getInstance()
                .isModelDownloaded(TranslateRemoteModel.Builder(TranslateLanguage.BULGARIAN).build()).await()
        }.getOrDefault(false)
        _ready.value = ok
        return ok
    }

    /**
     * Makes sure the language pack is there. Over mobile data only when [anyNetwork] (the user
     * tapped "Изтегли сега"); otherwise it waits for Wi-Fi. @return true when translating works.
     */
    suspend fun prepare(anyNetwork: Boolean = false): Boolean = mutex.withLock {
        if (!settings.value.translateStories || bg.dnevnitapet.i18n.I18n.language != bg.dnevnitapet.i18n.AppLanguage.BG) return false
        if (isReady()) return true
        if (!anyNetwork && !onUnmeteredNetwork()) return false
        val conditions = DownloadConditions.Builder().apply { if (!anyNetwork) requireWifi() }.build()
        withTimeoutOrNull(4 * 60_000L) { runCatching { client().downloadModelIfNeeded(conditions).await() } }
        isReady()
    }

    /** Translates [texts] (cached ones are not sent again). @return null when the language pack is missing. */
    suspend fun translate(texts: List<String>): List<String>? {
        if (!settings.value.translateStories || !isReady()) return null
        val fresh = LinkedHashMap<String, String>()
        val out = texts.map { raw ->
            val text = raw.trim()
            when {
                text.isEmpty() || StoryText.isCyrillic(text) -> text
                else -> cached(text) ?: fresh[text] ?: run {
                    val t = runCatching { client().translate(text).await() }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() } ?: return null
                    fresh[text] = t
                    t
                }
            }
        }
        if (fresh.isNotEmpty()) {
            store.update { c ->
                val all = LinkedHashMap(c.entries)
                fresh.forEach { (k, v) -> all.remove(k); all[k] = v }
                TranslationCache(all.entries.toList().takeLast(MAX_ENTRIES).associate { it.key to it.value })
            }
        }
        return out
    }

    /** The story of a picture in Bulgarian, or null (not translatable, switched off, no language pack). */
    suspend fun story(c: Candidate): StoryText.Story? {
        if (!StoryText.translatable(c)) return null
        val original = StoryText.original(c)
        val translated = translate(StoryText.parts(original)) ?: return null
        return StoryText.assemble(original, translated)
    }

    /** Translates the titles (and places) of these pictures in the background, a few at a time. */
    suspend fun translateTitles(candidates: List<Candidate>) {
        if (!isReady()) return
        val todo = candidates.filter { StoryText.translatable(it) }
            .flatMap { listOf(it.title, it.location) }
            .map { it.trim() }
            .filter { it.isNotEmpty() && cached(it) == null && !StoryText.isCyrillic(it) }
            .distinct()
            .take(80)
        todo.chunked(10).forEach { translate(it) ?: return }
    }

    private fun onUnmeteredNetwork(): Boolean = runCatching {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        cm.activeNetworkInfo?.isConnected == true && !cm.isActiveNetworkMetered
    }.getOrDefault(false)

    companion object {
        private const val MAX_ENTRIES = 1500
    }
}

/** Waits for a Google Play services task. */
internal suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}
