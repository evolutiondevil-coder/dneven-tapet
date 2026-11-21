package bg.dnevnitapet.net

import java.io.File
import java.io.IOException
import bg.dnevnitapet.i18n.tr

/** Minimal HTTP abstraction so that sources can be tested without the network. */
interface Http {
    suspend fun getText(url: String, headers: Map<String, String> = emptyMap()): String

    /** Downloads [url] into [dest]. Fails if the answer is not an image or is larger than [maxBytes]. */
    suspend fun downloadImage(url: String, dest: File, maxBytes: Long = 60L * 1024 * 1024, headers: Map<String, String> = emptyMap())

    /** Fire-and-forget GET whose body is ignored. */
    suspend fun ping(url: String, headers: Map<String, String> = emptyMap())
}

class HttpStatusException(val code: Int, url: String) : IOException(tr("HTTP {0} за {1}", code, url.substringBefore('?')))
