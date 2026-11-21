package bg.dnevnitapet.story

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.location.Geocoder
import bg.dnevnitapet.data.JsonFileStore
import bg.dnevnitapet.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.URLEncoder
import java.util.Locale

/** Coordinates found for place names: [lat, lon], or empty when the place was not found. */
@Serializable
data class PlaceCache(val entries: Map<String, List<Double>> = emptyMap())

/**
 * "Къде е": finds a picture's place on the map and draws a small map around it from
 * OpenStreetMap tiles (kept in the cache, so a place is downloaded once).
 */
class Places(private val context: Context, private val http: Http) {
    private val store = JsonFileStore(File(context.filesDir, "places.json"), PlaceCache.serializer()) { PlaceCache() }
    private val tilesDir = File(context.cacheDir, "tiles")

    /** @return latitude and longitude, or null when the place cannot be found. */
    suspend fun locate(place: String): Pair<Double, Double>? {
        val key = place.trim()
        if (key.isEmpty()) return null
        store.value.entries[key]?.let { return if (it.size == 2) it[0] to it[1] else null }
        val found = withContext(Dispatchers.IO) { geocoder(key) ?: nominatim(key) }
        // Only a real answer is remembered (a network error is tried again next time).
        if (found != null) store.update { it.copy(entries = it.entries + (key to listOf(found.first, found.second))) }
        return found
    }

    @Suppress("DEPRECATION")
    private fun geocoder(place: String): Pair<Double, Double>? = runCatching {
        if (!Geocoder.isPresent()) return null
        Geocoder(context, Locale.ENGLISH).getFromLocationName(place, 1)?.firstOrNull()?.let { it.latitude to it.longitude }
    }.getOrNull()

    private suspend fun nominatim(place: String): Pair<Double, Double>? = runCatching {
        val url = "https://nominatim.openstreetmap.org/search?format=json&limit=1&q=" + URLEncoder.encode(place, "UTF-8")
        val first = Json.parseToJsonElement(http.getText(url)).jsonArray.firstOrNull()?.jsonObject ?: return null
        first["lat"]!!.jsonPrimitive.content.toDouble() to first["lon"]!!.jsonPrimitive.content.toDouble()
    }.getOrNull()

    /** A [width] x [height] px map around the place, or null without the tiles. */
    suspend fun map(lat: Double, lon: Double, width: Int, height: Int, zoom: Int = 6): Pair<Bitmap, MapTiles.View>? =
        withContext(Dispatchers.IO) {
            val view = MapTiles.view(lat, lon, zoom, width, height)
            val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            canvas.drawColor(Color.rgb(170, 211, 223))
            var drawn = 0
            for (t in view.tiles) {
                val file = File(tilesDir, "${view.zoom}_${t.x}_${t.y}.png")
                if (!file.exists()) {
                    runCatching { http.downloadImage("https://tile.openstreetmap.org/${view.zoom}/${t.x}/${t.y}.png", file, maxBytes = 1_000_000) }
                }
                val tile = runCatching { BitmapFactory.decodeFile(file.path) }.getOrNull() ?: continue
                canvas.drawBitmap(tile, t.left.toFloat(), t.top.toFloat(), null)
                tile.recycle()
                drawn++
            }
            if (drawn == 0) null else out to view
        }
}
