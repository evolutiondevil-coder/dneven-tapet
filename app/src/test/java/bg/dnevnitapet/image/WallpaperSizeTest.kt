package bg.dnevnitapet.image

import bg.dnevnitapet.model.Settings
import bg.dnevnitapet.model.WallpaperTarget
import bg.dnevnitapet.wallpaper.Panorama
import org.junit.Assert.assertEquals
import org.junit.Test

class WallpaperSizeTest {
    private val w = 1080
    private val h = 2340

    @Test
    fun withoutPanoramaTheFileIsScreenWide() {
        assertEquals(w, WallpaperSize.width(w, h, w, 3840, 2160))
    }

    @Test
    fun widePictureFillsThePanorama() {
        // Bing 16:9 at the screen's height would be 4160 px: limited to two screens.
        assertEquals(2 * w, WallpaperSize.width(w, h, 2 * w, 3840, 2160))
        assertEquals(3 * w, WallpaperSize.width(w, h, 3 * w, 3840, 2160))
    }

    @Test
    fun squarishPictureIsAsWideAsItIs() {
        // Square photo: 2340 px at the screen height, below the 3-screen limit.
        assertEquals(2340, WallpaperSize.width(w, h, 3 * w, 2000, 2000))
    }

    @Test
    fun tallOrBarelyWiderPicturesStayScreenWide() {
        assertEquals(w, WallpaperSize.width(w, h, 2 * w, 1080, 2400))
        // 9:16 is still a little wider than this 9:19.5 screen.
        assertEquals(1316, WallpaperSize.width(w, h, 2 * w, 1080, 1920))
        assertEquals(w, WallpaperSize.width(w, h, 2 * w, 1100, 2340))
        assertEquals(w, WallpaperSize.width(w, h, 2 * w, 0, 0))
    }

    @Test
    fun maxWidthFollowsTheSettings() {
        assertEquals(w, Panorama.maxWidth(w, Settings()))
        assertEquals(2 * w, Panorama.maxWidth(w, Settings(panorama = true)))
        assertEquals(1620, Panorama.maxWidth(w, Settings(panorama = true, panoramaScreens = 1.5f)))
        // The lock screen does not slide.
        assertEquals(w, Panorama.maxWidth(w, Settings(panorama = true, target = WallpaperTarget.LOCK)))
        assertEquals(2 * w, Panorama.maxWidth(w, Settings(panorama = true, target = WallpaperTarget.BOTH)))
    }

    @Test
    fun placementSlidesAcrossThePicture() {
        // 2160 x 2340 file on a 1080 x 2340 screen: one screen of extra width.
        val left = WallpaperSize.placement(2160, 2340, w, h, 0f)
        val middle = WallpaperSize.placement(2160, 2340, w, h, 0.5f)
        val right = WallpaperSize.placement(2160, 2340, w, h, 1f)
        assertEquals(1f, left.scale, 1e-4f)
        assertEquals(0f, left.left, 0.01f)
        assertEquals(-540f, middle.left, 0.01f)
        assertEquals(-1080f, right.left, 0.01f)
        assertEquals(0f, middle.top, 0.01f)
    }

    @Test
    fun placementScalesAReducedPictureToTheScreen() {
        // Decoded at half size: drawn twice as big, still fills the screen's height.
        val p = WallpaperSize.placement(1080, 1170, w, h, 1f)
        assertEquals(2f, p.scale, 1e-4f)
        assertEquals(-1080f, p.left, 0.01f)
        // A screen-sized picture does not move.
        assertEquals(0f, WallpaperSize.placement(w, h, w, h, 1f).left, 0.01f)
    }
}
