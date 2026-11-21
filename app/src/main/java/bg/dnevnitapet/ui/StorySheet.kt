package bg.dnevnitapet.ui

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import bg.dnevnitapet.Graph
import bg.dnevnitapet.model.StoredImage
import bg.dnevnitapet.sources.SourceRegistry
import bg.dnevnitapet.story.MapTiles
import bg.dnevnitapet.story.StoryText
import bg.dnevnitapet.wallpaper.MyPhotos
import java.net.URLEncoder
import bg.dnevnitapet.i18n.tr

private sealed class Translation {
    data object Off : Translation()
    data object Working : Translation()
    data class Done(val story: StoryText.Story) : Translation()
    /** No language pack yet and no Wi-Fi. */
    data object NeedsWifi : Translation()
}

/**
 * "Историята на снимката": title, place and story in Bulgarian (translated on the phone), a small
 * map, "Покажи на картата" and "Прочети в Уикипедия". The original text is one tap away.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun InfoSheet(image: StoredImage, onDismiss: () -> Unit, onSave: () -> Unit) {
    val context = LocalContext.current
    val graph = remember { Graph.get(context) }
    val c = image.candidate
    val mine = c.sourceId == MyPhotos.SOURCE_ID
    val original = remember(c.key) { StoryText.original(c) }
    var retry by remember { mutableIntStateOf(0) }
    var anyNetwork by remember { mutableStateOf(false) }
    val translation by produceState<Translation>(Translation.Working, c.key, retry) {
        value = Translation.Working
        if (!StoryText.translatable(c) || !graph.settings.value.translateStories) {
            value = Translation.Off
            return@produceState
        }
        // Already translated before: at once.
        graph.stories.story(c)?.let {
            value = Translation.Done(it)
            return@produceState
        }
        value = if (graph.stories.prepare(anyNetwork)) {
            graph.stories.story(c)?.let { Translation.Done(it) } ?: Translation.Off
        } else {
            Translation.NeedsWifi
        }
    }
    var showOriginal by remember { mutableStateOf(false) }
    val story = (translation as? Translation.Done)?.story?.takeIf { !showOriginal } ?: original
    val place = original.location
    val map by produceState<Pair<Bitmap, MapTiles.View>?>(null, place) {
        if (place.isBlank() || mine) return@produceState
        val at = graph.places.locate(place) ?: return@produceState
        value = graph.places.map(at.first, at.second, MAP_W, MAP_H)
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp)
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(shape = RoundedCornerShape(12.dp), color = SelectedOrange.copy(alpha = 0.16f)) {
                Text(
                    if (mine) tr("Твоя снимка") else tr("Историята на снимката"),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
            Text(story.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            if (story.location.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Place, contentDescription = null, tint = SelectedOrange, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(story.location, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            if (story.description.isNotBlank()) {
                Text(story.description, style = MaterialTheme.typography.bodyLarge)
            }
            TranslationLine(
                translation,
                showOriginal,
                onToggle = { showOriginal = !showOriginal },
                onDownloadNow = {
                    anyNetwork = true
                    retry++
                },
            )

            map?.let { (bitmap, view) ->
                MapPreview(bitmap, view) { openMap(context, place) }
            }
            if (!mine) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (place.isNotBlank()) {
                        Button(
                            onClick = { openMap(context, place) },
                            colors = ButtonDefaults.buttonColors(containerColor = SelectedOrange, contentColor = OnSelectedOrange),
                        ) { Text(tr("Покажи на картата")) }
                    }
                    OutlinedButton(onClick = {
                        val q = URLEncoder.encode(StoryText.wikipediaQuery(story), "UTF-8")
                        open(context, "https://bg.wikipedia.org/w/index.php?search=$q")
                    }) { Text(tr("Прочети в Уикипедия")) }
                }
            }
            val credit = listOf(c.author.takeIf { it.isNotBlank() }?.let { tr("Снимка: {0}", it) }, SourceRegistry.name(c.sourceId).takeIf { !mine })
                .filterNotNull().joinToString(" · ") + if (c.date.isNotBlank()) " · " + Format.date(c.date) else ""
            if (credit.isNotBlank()) {
                Text(credit, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Text(
                tr("Размер {0} × {1}", image.width, image.height) +
                    if (c.copyright.isNotBlank() && c.copyright.removePrefix("©").trim() != c.author) " · ${c.copyright}" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (c.infoUrl.isNotBlank()) TextButton(onClick = { open(context, c.infoUrl) }) { Text(tr("Научи повече")) }
                if (!mine) TextButton(onClick = onSave) { Text(tr("Запази в галерията")) }
            }
            if (!mine) {
                Text(
                    tr("Снимката е собственост на автора си. Използва се само като личен тапет."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TranslationLine(t: Translation, showOriginal: Boolean, onToggle: () -> Unit, onDownloadNow: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    when (t) {
        Translation.Off -> Unit
        Translation.Working -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text(tr("Превеждам на български…"), style = MaterialTheme.typography.bodySmall, color = muted)
        }
        is Translation.Done -> Text(
            if (showOriginal) tr("Оригиналният текст · Покажи на български") else tr("Преведено автоматично · Покажи оригинала"),
            style = MaterialTheme.typography.bodySmall.copy(textDecoration = TextDecoration.Underline),
            color = muted,
            modifier = Modifier.clickable(onClick = onToggle).padding(vertical = 4.dp),
        )
        Translation.NeedsWifi -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                tr("Преводът на български ще се изтегли при Wi-Fi – еднократно, около 30 MB."),
                style = MaterialTheme.typography.bodySmall,
                color = muted,
            )
            TextButton(onClick = onDownloadNow, contentPadding = ButtonDefaults.TextButtonContentPadding) { Text(tr("Изтегли сега")) }
        }
    }
}

@Composable
private fun MapPreview(bitmap: Bitmap, view: MapTiles.View, onClick: () -> Unit) {
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick),
    ) {
        Image(image, contentDescription = tr("Карта"), contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(120.dp))
        // The pin where the place is (the map is cropped to the box from the middle).
        val density = LocalDensity.current
        val boxW = with(density) { maxWidth.toPx() }
        val boxH = with(density) { 120.dp.toPx() }
        val scale = maxOf(boxW / bitmap.width, boxH / bitmap.height)
        val x = (view.pinX - bitmap.width / 2f) * scale + boxW / 2f
        val y = (view.pinY - bitmap.height / 2f) * scale + boxH / 2f
        Icon(
            Icons.Filled.Place,
            contentDescription = null,
            tint = Color(0xFFE53935),
            modifier = Modifier.size(32.dp).offset(
                x = with(density) { x.toDp() } - 16.dp,
                y = with(density) { y.toDp() } - 30.dp,
            ),
        )
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color.Black.copy(alpha = 0.55f),
            modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
        ) {
            Text("© OpenStreetMap", color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
        }
    }
}

private fun open(context: android.content.Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** Opens the phone's map app at the place (or a map in the browser). */
private fun openMap(context: android.content.Context, place: String) {
    val geo = Uri.parse("geo:0,0?q=" + Uri.encode(place))
    val ok = runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, geo).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
    if (!ok) open(context, "https://www.openstreetmap.org/search?query=" + Uri.encode(place))
}

/** Size of the map drawn behind the 120 dp box (cropped from the middle). */
private const val MAP_W = 900
private const val MAP_H = 300
