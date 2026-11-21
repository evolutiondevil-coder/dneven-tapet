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

    @Test
    fun `only own photos - the gallery can show the internet pictures`() {
        val online = listOf("o1", "o2")
        val mine = listOf("m1", "m2")
        assertEquals(online, MyPhotos.upcomingShown(PhotoMode.MINE, online, mine, currentIsMine = true, browseOnline = true))
        assertEquals(mine, MyPhotos.upcomingShown(PhotoMode.MINE, online, mine, currentIsMine = true, browseOnline = false))
        // The switch only exists with own photos alone; the other modes ignore it.
        assertEquals(online, MyPhotos.upcomingShown(PhotoMode.ONLINE, online, mine, currentIsMine = false, browseOnline = false))
        assertEquals(listOf("m1", "o1", "m2", "o2"), MyPhotos.upcomingShown(PhotoMode.MIXED, online, mine, currentIsMine = false, browseOnline = true))
    }

    @Test
    fun `albums choose the photos of a screen`() {
        val all = listOf("a", "b", "c", "d")
        val albums = listOf(bg.dnevnitapet.model.Album("trips", "Пътувания", listOf("b", "d")), bg.dnevnitapet.model.Album("empty", "Празен"))
        assertEquals(listOf("b", "d"), MyPhotos.inAlbum(all, { it }, albums, albumsOn = true, albumId = "trips"))
        // Albums off, no album chosen, an empty or deleted album: all photos.
        assertEquals(all, MyPhotos.inAlbum(all, { it }, albums, albumsOn = false, albumId = "trips"))
        assertEquals(all, MyPhotos.inAlbum(all, { it }, albums, albumsOn = true, albumId = ""))
        assertEquals(all, MyPhotos.inAlbum(all, { it }, albums, albumsOn = true, albumId = "empty"))
        assertEquals(all, MyPhotos.inAlbum(all, { it }, albums, albumsOn = true, albumId = "gone"))
    }

    @Test
    fun galleryShowsEveryPictureOnce() {
        // Own photo m1 was shown before (history) and comes again (upcoming): it must not appear twice,
        // the main screen's gallery crashes on a repeated picture.
        val order = MyPhotos.browseOrder(listOf("o2", "m1", "o1"), "m2", listOf("m1", "o3")) { it }
        assertEquals(listOf("o1", "o2", "m2", "m1", "o3"), order)
        assertEquals(order.size, order.distinct().size)
        assertEquals(listOf("b", "a"), MyPhotos.browseOrder(listOf("b", "a", "b"), null, emptyList<String>()) { it })
    }
}
