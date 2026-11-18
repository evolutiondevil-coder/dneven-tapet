package bg.dnevnitapet.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import bg.dnevnitapet.update.ReleaseInfo

/**
 * "Има нова версия": shown every time the app starts while a newer version waits on GitHub.
 * "Обнови" downloads it and opens the phone's installer; "По-късно" asks again next time.
 */
@Composable
fun UpdateDialog(release: ReleaseInfo, downloading: Boolean, onUpdate: () -> Unit, onLater: () -> Unit) {
    AlertDialog(
        onDismissRequest = { if (!downloading) onLater() },
        icon = { Icon(Icons.Outlined.SystemUpdate, contentDescription = null) },
        title = { Text("Нова версия ${release.version}") },
        text = {
            Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                val notes = whatsNew(release.notes)
                if (notes.isNotBlank()) Text(notes, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Инсталира се върху сегашната – настройките, историята и любимите се запазват.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = if (notes.isNotBlank()) 12.dp else 0.dp),
                )
            }
        },
        confirmButton = {
            Button(onClick = onUpdate, enabled = !downloading) {
                if (downloading) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Изтеглям…")
                    }
                } else {
                    Text("Обнови")
                }
            }
        },
        dismissButton = { TextButton(onClick = onLater, enabled = !downloading) { Text("По-късно") } },
    )
}

/** The "what's new" part of the release notes, without Markdown marks and installation help. */
internal fun whatsNew(notes: String): String =
    notes.lineSequence()
        .takeWhile { !it.contains("Инсталиране") }
        .map { it.replace("**", "").removePrefix("## ").trim() }
        .filter { it.isNotBlank() && !it.startsWith("Какво ново") }
        .take(14)
        .joinToString("\n")
