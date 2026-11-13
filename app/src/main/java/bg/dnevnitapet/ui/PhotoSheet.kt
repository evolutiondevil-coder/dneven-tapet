package bg.dnevnitapet.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import bg.dnevnitapet.model.StoredImage
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.io.File

/** One action under the picture: an icon in a circle with a short label below it. */
class SheetAction(
    val icon: ImageVector,
    val label: String,
    val tint: Color? = null,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

/**
 * A picture opened from the history, the favourites or "Моите снимки": a large preview in the
 * screen's shape, the title, a row of actions (icons with one-word labels, never wrapped) and
 * one big "Сложи като тапет". Swipe down or tap outside to close.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoSheet(
    image: StoredImage,
    title: String,
    subtitle: String,
    actions: List<SheetAction>,
    isCurrent: Boolean,
    busy: Boolean,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 24.dp, end = 24.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // The preview keeps the wallpaper's own shape and is at most half the screen tall.
            BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                val aspect = image.width.toFloat() / image.height.coerceAtLeast(1)
                val maxH = screenHeight * 0.46f
                val w = min(maxWidth, maxH * aspect)
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(File(image.file)).size(720, 1560).crossfade(true).build(),
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.width(w).height(w / aspect).clip(RoundedCornerShape(20.dp)),
                )
            }

            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp),
            )
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 18.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                actions.forEach { a -> ActionTile(a, Modifier.weight(1f)) }
            }

            Button(
                onClick = onApply,
                enabled = !busy && !isCurrent,
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp).height(52.dp),
            ) {
                if (isCurrent) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Това е тапетът ти", maxLines = 1, softWrap = false)
                } else {
                    Text("Сложи като тапет", style = MaterialTheme.typography.titleSmall, maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

@Composable
private fun ActionTile(action: SheetAction, modifier: Modifier) {
    Surface(
        onClick = action.onClick,
        enabled = action.enabled,
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent,
        modifier = modifier,
    ) {
        Column(
            Modifier.padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.size(48.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    val tint = action.tint ?: MaterialTheme.colorScheme.onSurface
                    Icon(action.icon, contentDescription = null, tint = if (action.enabled) tint else tint.copy(alpha = 0.38f))
                }
            }
            Text(
                action.label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                color = if (action.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            )
        }
    }
}
