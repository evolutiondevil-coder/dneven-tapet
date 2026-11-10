package bg.dnevnitapet.image

import bg.dnevnitapet.model.ImageVariant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VariantChooserTest {
    private val bing = listOf(
        ImageVariant("portrait", 1080, 1920, portrait = true),
        ImageVariant("uhd", 3840, 2160),
        ImageVariant("hd", 1920, 1080),
    )

    @Test
    fun `prefers the source's portrait rendition on a 1080p phone`() {
        assertEquals("portrait", VariantChooser.choose(bing, 1080, 2340)!!.url)
    }

    @Test
    fun `uses the sharper UHD picture on a 1440p phone`() {
        // Portrait 1080x1920 would need 1.6x enlarging on 1440x3120; UHD cropped needs 1.44x.
        assertEquals("uhd", VariantChooser.choose(bing, 1440, 3120)!!.url)
    }

    @Test
    fun `smallest rendition that is still sharp wins`() {
        val wiki = listOf(
            ImageVariant("1920", 1920, 1280),
            ImageVariant("3840", 3840, 2560),
            ImageVariant("original", 6231, 4154),
        )
        assertEquals("3840", VariantChooser.choose(wiki, 1080, 2340)!!.url)
    }

    @Test
    fun `too small pictures are rejected`() {
        assertNull(VariantChooser.choose(listOf(ImageVariant("tiny", 982, 575)), 1080, 2340))
    }

    @Test
    fun `upscale accounts for the crop`() {
        // A 3840x2160 picture cropped to 9:19.5 keeps the full 2160 px height.
        assertEquals(2340.0 / 2160, VariantChooser.upscale(ImageVariant("x", 3840, 2160), 1080, 2340), 1e-9)
        // A tall narrow picture is limited by its width instead.
        assertEquals(2340.0 / (900 * 2340.0 / 1080), VariantChooser.upscale(ImageVariant("x", 900, 4000), 1080, 2340), 1e-9)
    }

    @Test
    fun `server crop never asks for more pixels than the original has`() {
        val v = ImageVariant("u?w={w}&h={h}", 1200, 1800, sizedTemplate = true)
        // 1800 / 2340 → needs 1.3x; ask the server for the original resolution instead.
        assertEquals("u?w=831&h=1800", VariantChooser.choose(listOf(v), 1080, 2340)!!.url)
    }
}
