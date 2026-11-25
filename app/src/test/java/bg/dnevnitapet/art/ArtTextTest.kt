package bg.dnevnitapet.art

import org.junit.Assert.assertEquals
import org.junit.Test

class ArtTextTest {
    @Test
    fun machineTextLeavesTheTitle() {
        assertEquals(
            "A Sunday on La Grande Jatte",
            ArtText.title("A Sunday on La Grande Jatte -- 1884 label QS:Lfr,\"Un dimanche après-midi à l'Île de la Grande Jatte\" label QS:Len,\"A Sunday on La Grande Jatte\""),
        )
        assertEquals("The Starry Night", ArtText.title("The Starry Night"))
        assertEquals("Mona Lisa", ArtText.title("\"Mona Lisa\""))
        assertEquals("Impression, Sunrise", ArtText.title("Impression, Sunrise, 1872"))
    }

    @Test
    fun datesAreShort() {
        assertEquals("1884–1886", ArtText.date("between 1884 and 1886 date QS:P571,+1884-00-00T00:00:00Z/9,P1319,+1884-00-00T00:00:00Z/9", true))
        assertEquals("ок. 1665", ArtText.date("circa 1665", true))
        assertEquals("c. 1665", ArtText.date("circa 1665", false))
        assertEquals("1889", ArtText.date("1889-06-01", true))
        assertEquals("1503–1519", ArtText.date("1503-1519", true))
        assertEquals("1872", ArtText.date("1872", true))
    }

    @Test
    fun artistAndStoryLoseTheMachineText() {
        assertEquals("Georges Seurat", ArtText.artist("Georges Seurat (1859–1891) label QS:Len,\"Georges Seurat\""))
        assertEquals(
            "Painting by Georges Seurat. Art Institute of Chicago.",
            ArtText.story("Painting by Georges Seurat. label QS:Len,\"x\" Art Institute of Chicago."),
        )
    }
}
