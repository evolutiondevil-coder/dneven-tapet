package bg.dnevnitapet.wallpaper

import bg.dnevnitapet.model.PhotoMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MyPhotosTest {
    private val album = listOf("mine:a", "mine:b", "mine:c", "mine:d")

    @Test
    fun `every photo comes once per round, then a new round starts`() {
        var rotation = emptyList<String>()
        var current: String? = null
        val shown = mutableListOf<String>()
        repeat(album.size) {
            val (key, rest) = MyPhotos.nextKey(album, rotation, current, Random(7))!!
            shown += key
            rotation = rest
            current = key
        }
        assertEquals(album.toSet(), shown.toSet())
        assertTrue(rotation.isEmpty())
        // The next round does not start with the photo on screen.
        repeat(20) { seed ->
            val (first, _) = MyPhotos.nextKey(album, emptyList(), current, Random(seed))!!
            assertNotEquals(current, first)
        }
    }

    @Test
    fun `removed photos drop out of the round`() {
        val (key, rest) = MyPhotos.nextKey(listOf("mine:b", "mine:c"), listOf("mine:a", "mine:b", "mine:c"), null)!!
        assertEquals("mine:b", key)
        assertEquals(listOf("mine:c"), rest)
        assertNull(MyPhotos.nextKey(emptyList(), listOf("mine:a"), null))
    }

    @Test
    fun `a single photo keeps coming`() {
        val (key, _) = MyPhotos.nextKey(listOf("mine:a"), emptyList(), "mine:a")!!
        assertEquals("mine:a", key)
    }

    @Test
    fun `upcoming lists the rest of the round first, without the current photo`() {
        assertEquals(
            listOf("mine:c", "mine:a", "mine:d"),
            MyPhotos.upcoming(album, rotation = listOf("mine:c", "mine:a"), currentKey = "mine:b"),
        )
    }

    @Test
    fun `mode decides whether the next change uses an own photo`() {
        assertFalse(MyPhotos.useMineNext(PhotoMode.ONLINE, "bing:x", hasMine = true))
        assertTrue(MyPhotos.useMineNext(PhotoMode.MINE, "mine:a", hasMine = true))
        // Mixed: take turns.
        assertTrue(MyPhotos.useMineNext(PhotoMode.MIXED, "bing:x", hasMine = true))
        assertFalse(MyPhotos.useMineNext(PhotoMode.MIXED, "mine:a", hasMine = true))
        // Mixed without own photos: internet pictures only.
        assertFalse(MyPhotos.useMineNext(PhotoMode.MIXED, "bing:x", hasMine = false))
    }

    @Test
    fun `mixed preview interleaves in the order of the changes`() {
        val online = listOf("o1", "o2", "o3")
        val mine = listOf("m1", "m2")
        assertEquals(listOf("m1", "o1", "m2", "o2", "o3"), MyPhotos.upcomingMixed(PhotoMode.MIXED, online, mine, currentIsMine = false))
        assertEquals(listOf("o1", "m1", "o2", "m2", "o3"), MyPhotos.upcomingMixed(PhotoMode.MIXED, online, mine, currentIsMine = true))
        assertEquals(online, MyPhotos.upcomingMixed(PhotoMode.ONLINE, online, mine, currentIsMine = false))
        assertEquals(mine, MyPhotos.upcomingMixed(PhotoMode.MINE, online, mine, currentIsMine = false))
    }
}
