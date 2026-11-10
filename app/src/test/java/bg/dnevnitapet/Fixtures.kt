package bg.dnevnitapet

import bg.dnevnitapet.net.Http
import java.io.File

object Fixtures {
    fun read(name: String): String =
        requireNotNull(javaClass.classLoader!!.getResource("fixtures/$name")) { "missing fixture $name" }.readText()
}

/** Answers requests from a table of URL fragments; records every request. */
class FakeHttp(private val routes: List<Pair<String, String>>) : Http {
    val requested = mutableListOf<String>()

    override suspend fun getText(url: String, headers: Map<String, String>): String {
        requested += url
        return routes.firstOrNull { url.contains(it.first) }?.second
            ?: throw java.io.IOException("no route for $url")
    }

    override suspend fun downloadImage(url: String, dest: File, maxBytes: Long, headers: Map<String, String>) =
        throw UnsupportedOperationException()

    override suspend fun ping(url: String, headers: Map<String, String>) {
        requested += url
    }
}
