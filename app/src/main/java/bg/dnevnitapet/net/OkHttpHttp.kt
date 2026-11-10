package bg.dnevnitapet.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class OkHttpHttp(
    private val client: OkHttpClient = defaultClient(),
) : Http {

    override suspend fun getText(url: String, headers: Map<String, String>): String = withContext(Dispatchers.IO) {
        client.newCall(request(url, headers)).execute().use { response ->
            if (!response.isSuccessful) throw HttpStatusException(response.code, url)
            response.body?.string() ?: throw IOException("Празен отговор")
        }
    }

    override suspend fun downloadImage(url: String, dest: File, maxBytes: Long, headers: Map<String, String>) =
        withContext(Dispatchers.IO) {
            client.newCall(request(url, headers)).execute().use { response ->
                if (!response.isSuccessful) throw HttpStatusException(response.code, url)
                val body = response.body ?: throw IOException("Празен отговор")
                val type = body.contentType()
                if (type != null && type.type != "image" && type.subtype != "octet-stream") {
                    throw IOException("Не е снимка ($type)")
                }
                if (body.contentLength() > maxBytes) throw IOException("Файлът е твърде голям")
                dest.parentFile?.mkdirs()
                val tmp = File(dest.path + ".part")
                var total = 0L
                body.byteStream().use { input ->
                    tmp.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            total += n
                            if (total > maxBytes) throw IOException("Файлът е твърде голям")
                            output.write(buffer, 0, n)
                        }
                    }
                }
                if (total == 0L) throw IOException("Празен файл")
                if (!tmp.renameTo(dest)) {
                    tmp.copyTo(dest, overwrite = true)
                    tmp.delete()
                }
                Unit
            }
        }

    override suspend fun ping(url: String, headers: Map<String, String>) {
        withContext(Dispatchers.IO) {
            runCatching { client.newCall(request(url, headers)).execute().close() }
        }
    }

    private fun request(url: String, headers: Map<String, String>): Request {
        val builder = Request.Builder().url(url).header("User-Agent", USER_AGENT)
        headers.forEach { (k, v) -> builder.header(k, v) }
        return builder.build()
    }

    companion object {
        /** Generic client name; Wikimedia asks every client to send a descriptive User-Agent. */
        const val USER_AGENT = "DnevenTapet/1.0 (Android; personal wallpaper app)"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(120, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}
