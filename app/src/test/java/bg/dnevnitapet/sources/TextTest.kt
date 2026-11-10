package bg.dnevnitapet.sources

import org.junit.Assert.assertEquals
import org.junit.Test

class TextTest {
    @Test
    fun `strips html and decodes entities`() {
        assertEquals("APOD: 2026 – Rosetta’s Selfie & more", Text.stripHtml("<b>APOD:</b> 2026 &#8211; Rosetta&#8217;s Selfie &amp; more"))
    }

    @Test
    fun `strips wiki links and templates`() {
        assertEquals(
            "Panoramic view of the Kumaon Himalaya as seen from India.",
            Text.stripWiki("Panoramic view of the [[:en:Kumaon|Kumaon Himalaya]] as seen from [[India]].{{foo|bar}}"),
        )
    }
}
