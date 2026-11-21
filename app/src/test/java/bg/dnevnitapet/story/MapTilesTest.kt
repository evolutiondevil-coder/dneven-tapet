package bg.dnevnitapet.story

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MapTilesTest {
    @Test
    fun `the place is in the middle of the map`() {
        // Sofia.
        val v = MapTiles.view(42.6977, 23.3219, zoom = 6, width = 720, height = 240)
        assertEquals(360, v.pinX)
        assertEquals(120, v.pinY)
        // Tiles cover the whole map: every pixel column and row.
        assertTrue(v.tiles.minOf { it.left } <= 0 && v.tiles.maxOf { it.left } + MapTiles.TILE >= 720)
        assertTrue(v.tiles.minOf { it.top } <= 0 && v.tiles.maxOf { it.top } + MapTiles.TILE >= 240)
        // Sofia is in tile 36/23 at zoom 6.
        assertTrue(v.tiles.any { it.x == 36 && it.y == 23 })
    }

    @Test
    fun `near the date line the tiles wrap around`() {
        val v = MapTiles.view(-17.7, 179.9, zoom = 3, width = 720, height = 240)
        assertTrue(v.tiles.all { it.x in 0 until 8 })
        assertTrue(v.tiles.any { it.x == 0 })
    }
}
