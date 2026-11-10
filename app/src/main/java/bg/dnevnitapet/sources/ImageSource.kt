package bg.dnevnitapet.sources

import bg.dnevnitapet.model.Candidate
import bg.dnevnitapet.model.Settings
import bg.dnevnitapet.net.Http
import java.time.LocalDate
import kotlin.random.Random

/**
 * A provider of pictures. Every source is independent: if one fails, the others keep working.
 * To add a new source, implement this interface and register it in [SourceRegistry].
 */
interface ImageSource {
    /** Stable id stored in settings, e.g. "bing". */
    val id: String
    val displayName: String
    /** True if the source needs a personal API key entered in the settings. */
    val needsKey: Boolean get() = false

    fun isConfigured(settings: Settings): Boolean = true

    /**
     * Fetches a batch of candidates.
     * @param request.cursor opaque paging state returned by the previous call (null on first call).
     * @param request.wantFresh true when the newest pictures (today's) should be included.
     */
    suspend fun fetch(request: FetchRequest): FetchResult
}

data class FetchRequest(
    val http: Http,
    val settings: Settings,
    val cursor: String?,
    val wantFresh: Boolean,
    val today: LocalDate,
    val random: Random = Random.Default,
)

data class FetchResult(val candidates: List<Candidate>, val cursor: String?)

/** Thrown when a source answered but the answer is unusable (format changed, empty, ...). */
class SourceFormatException(message: String) : Exception(message)
