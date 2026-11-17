package bg.dnevnitapet.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import bg.dnevnitapet.model.StoredImage
import bg.dnevnitapet.sources.SourceRegistry
import bg.dnevnitapet.wallpaper.MyPhotos
import java.io.File

/** "Сподели": sends the picture with its name and author to another app (Viber, Messenger, mail…). */
object Share {
    /** Text that goes with the picture: title, author, source and the link to read more. */
    fun text(image: StoredImage): String {
        val c = image.candidate
        if (MyPhotos.isMine(c.key)) return c.description.ifBlank { c.title }
        val who = listOf(c.author, SourceRegistry.name(c.sourceId)).filter { it.isNotBlank() }.distinct().joinToString(" · ")
        return listOf(
            c.title,
            c.location.takeIf { it.isNotBlank() && it != c.title },
            who.takeIf { it.isNotBlank() }?.let { "Снимка: $it" },
            c.infoUrl.takeIf { it.isNotBlank() },
        ).filterNotNull().joinToString("\n")
    }

    /**
     * Copies the picture under a readable name into a folder only meant for sharing and returns
     * the intent that opens the phone's "share" menu.
     */
    fun intent(context: Context, image: StoredImage): Intent {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val name = image.candidate.title.replace(Regex("[^\\p{L}\\p{N}]+"), "-").trim('-').take(40).ifBlank { "tapet" }
        val copy = File(dir, "$name.jpg")
        File(image.file).copyTo(copy, overwrite = true)
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", copy)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, text(image))
            putExtra(Intent.EXTRA_SUBJECT, image.candidate.title)
            clipData = ClipData.newRawUri(image.candidate.title, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, "Сподели снимката")
    }

    fun share(context: Context, image: StoredImage): Boolean = runCatching {
        context.startActivity(intent(context, image).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess
}
