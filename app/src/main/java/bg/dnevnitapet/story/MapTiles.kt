package bg.dnevnitapet.story

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.tan

/** Which map tiles (OpenStreetMap, 256 px) make a small map around a place. Pure logic, unit-tested. */
object MapTiles {
    const val TILE = 256

    data class Tile(val x: Int, val y: Int, val left: Int, val top: Int)
    data class View(val zoom: Int, val tiles: List<Tile>, val pinX: Int, val pinY: Int)

    /** The place's position in pixels of the whole world map at [zoom]. */
    fun worldPixel(lat: Double, lon: Double, zoom: Int): Pair<Double, Double> {
        val size = (1 shl zoom) * TILE.toDouble()
        val x = (lon + 180.0) / 360.0 * size
        val latR = Math.toRadians(lat.coerceIn(-85.0511, 85.0511))
        val y = (1.0 - ln(tan(latR) + 1.0 / cos(latR)) / PI) / 2.0 * size
        return x to y
    }

    /** A [width] x [height] px map centred on the place (moved down/up at the poles so it stays on the map). */
    fun view(lat: Double, lon: Double, zoom: Int, width: Int, height: Int): View {
        val count = 1 shl zoom
        val size = count * TILE.toDouble()
        val (px, py) = worldPixel(lat, lon, zoom)
        val left = px - width / 2.0
        val top = (py - height / 2.0).coerceIn(0.0, max(0.0, size - height))
        val tiles = mutableListOf<Tile>()
        val ty0 = floor(top / TILE).toInt()
        val ty1 = floor((top + height - 1) / TILE).toInt()
        val tx0 = floor(left / TILE).toInt()
        val tx1 = floor((left + width - 1) / TILE).toInt()
        for (ty in ty0..ty1) {
            if (ty !in 0 until count) continue
            for (tx in tx0..tx1) {
                tiles += Tile(
                    x = ((tx % count) + count) % count,
                    y = ty,
                    left = (tx * TILE - left).roundToInt(),
                    top = (ty * TILE - top).roundToInt(),
                )
            }
        }
        return View(zoom, tiles, (px - left).roundToInt(), (py - top).roundToInt())
    }
}
