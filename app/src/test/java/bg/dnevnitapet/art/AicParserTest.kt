package bg.dnevnitapet.art

import org.junit.Assert.assertEquals
import org.junit.Test

class AicParserTest {
    private val json = """
        {"data":[
          {"id":28560,"title":"The Bedroom","artist_display":"Vincent van Gogh\nDutch, 1853–1890","date_display":"1889",
           "image_id":"25c31d8d-21a4-9ea1-1d73-6a2eca4dda7e","is_public_domain":true,"classification_title":"oil on canvas painting",
           "short_description":"Van Gogh painted his bedroom three times.","description":"<p>Long text</p>",
           "thumbnail":{"width":3000,"height":2378}},
          {"id":1,"title":"A modern work","artist_display":"Someone","date_display":"1990","image_id":"x","is_public_domain":false,
           "classification_title":"painting","thumbnail":{"width":10,"height":10}},
          {"id":2,"title":"A vase","artist_display":"Unknown","date_display":"500 BC","image_id":"y","is_public_domain":true,
           "classification_title":"vessel","thumbnail":{"width":10,"height":10}}
        ]}
    """.trimIndent()

    @Test
    fun `only public domain paintings are kept`() {
        val list = AicParser.parse(json)
        assertEquals(1, list.size)
        val a = list.single()
        assertEquals("The Bedroom", a.title)
        assertEquals("Vincent van Gogh", a.artist)
        assertEquals("1889", a.date)
        assertEquals("Van Gogh painted his bedroom three times.", a.story)
        assertEquals("https://www.artic.edu/iiif/2/25c31d8d-21a4-9ea1-1d73-6a2eca4dda7e/full/843,/0/default.jpg", a.imageUrl)
        val c = a.candidate()
        assertEquals("art:aic:28560", c.key)
        assertEquals(1686, c.variants.single().width)
        assertEquals(1336, c.variants.single().height)
    }
}
