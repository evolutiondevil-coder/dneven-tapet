package bg.dnevnitapet.archive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebSearchTest {
    @Test
    fun `commons results in their order, large pictures only, small renditions`() {
        val json = """
            {"query":{"pages":[
              {"title":"File:Rila lakes.jpg","index":2,"imageinfo":[{"url":"https://upload.wikimedia.org/wikipedia/commons/a/ab/Rila_lakes.jpg?utm_source=x","width":4000,"height":3000,"mime":"image/jpeg",
                "thumburl":"https://upload.wikimedia.org/wikipedia/commons/thumb/a/ab/Rila_lakes.jpg/500px-Rila_lakes.jpg","descriptionurl":"https://commons.wikimedia.org/wiki/File:Rila_lakes.jpg",
                "extmetadata":{"ImageDescription":{"value":"<p>The Seven Rila Lakes, Bulgaria</p>"},"Artist":{"value":"Someone"},"LicenseShortName":{"value":"CC BY-SA 4.0"}}}]},
              {"title":"File:Sofia.jpg","index":1,"imageinfo":[{"url":"https://upload.wikimedia.org/wikipedia/commons/c/cd/Sofia.jpg","width":2400,"height":1600,"mime":"image/jpeg",
                "thumburl":"https://upload.wikimedia.org/wikipedia/commons/thumb/c/cd/Sofia.jpg/500px-Sofia.jpg","extmetadata":{"ObjectName":{"value":"Alexander Nevsky Cathedral"}}}]},
              {"title":"File:Map.svg","index":3,"imageinfo":[{"url":"https://upload.wikimedia.org/x/Map.svg","width":3000,"height":2000,"mime":"image/svg+xml"}]},
              {"title":"File:Tiny.jpg","index":4,"imageinfo":[{"url":"https://upload.wikimedia.org/x/Tiny.jpg","width":400,"height":300,"mime":"image/jpeg"}]}
            ]}}
        """.trimIndent()
        val list = CommonsSearchParser.parse(json)
        assertEquals(listOf("Alexander Nevsky Cathedral", "Rila lakes"), list.map { it.candidate.title })
        val rila = list[1]
        assertEquals("The Seven Rila Lakes, Bulgaria", rila.candidate.description)
        assertEquals("https://upload.wikimedia.org/wikipedia/commons/thumb/a/ab/Rila_lakes.jpg/500px-Rila_lakes.jpg", rila.thumbUrl)
        assertEquals(listOf(3840, 1280), rila.candidate.variants.map { it.width })
        assertTrue(rila.candidate.variants[0].url.endsWith("/3840px-Rila_lakes.jpg"))
        assertEquals(1920, list[0].candidate.variants[0].width)
    }

    @Test
    fun `nasa results with their renditions`() {
        val json = """
            {"collection":{"items":[
              {"data":[{"nasa_id":"iss040e081005","title":"Bulgaria at Night","description":"Lights of Sofia.","date_created":"2014-07-25T00:00:00Z","center":"JSC"}],
               "links":[{"href":"https://images-assets.nasa.gov/image/iss040e081005/iss040e081005~thumb.jpg","rel":"preview"}]},
              {"data":[{"nasa_id":"x","title":"No picture"}],"links":[]}
            ]}}
        """.trimIndent()
        val list = NasaSearchParser.parse(json)
        assertEquals(1, list.size)
        val c = list.single().candidate
        assertEquals("nasa:iss040e081005", c.key)
        assertEquals("2014-07-25", c.date)
        assertEquals("https://images-assets.nasa.gov/image/iss040e081005/iss040e081005~large.jpg", c.variants.first().url)
        assertEquals("https://images.nasa.gov/details/iss040e081005", c.infoUrl)
    }

    @Test
    fun `both sources are interleaved`() {
        assertEquals(listOf(1, 10, 2, 20, 3), WebSearch.interleave(listOf(listOf(1, 2, 3), listOf(10, 20))))
    }
}
