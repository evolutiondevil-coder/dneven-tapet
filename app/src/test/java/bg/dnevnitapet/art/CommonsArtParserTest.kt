package bg.dnevnitapet.art

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommonsArtParserTest {
    private val json = """
        {"continue":{"gcmcontinue":"file|NEXT|123","continue":"gcmcontinue||"},
         "query":{"pages":[
          {"pageid":1,"title":"File:Vincent van Gogh - The Starry Night - Google Art Project.jpg","imageinfo":[{
            "url":"https://upload.wikimedia.org/wikipedia/commons/e/ea/Starry.jpg","width":12000,"height":9500,"mime":"image/jpeg",
            "thumburl":"https://upload.wikimedia.org/wikipedia/commons/thumb/e/ea/Starry.jpg/3840px-Starry.jpg?utm_source=commons.wikimedia.org&utm_campaign=x","thumbwidth":3840,"thumbheight":3040,
            "descriptionurl":"https://commons.wikimedia.org/wiki/File:Starry.jpg",
            "extmetadata":{"ObjectName":{"value":"The Starry Night"},"Artist":{"value":"<a href=\"x\">Vincent van Gogh</a> (1853–1890)"},
              "DateTimeOriginal":{"value":"1889"},"ImageDescription":{"value":"<p>The view from his window.</p>"},"LicenseShortName":{"value":"Public domain"}}}]},
          {"pageid":2,"title":"File:Modern.jpg","imageinfo":[{"url":"https://upload.wikimedia.org/wikipedia/commons/a/aa/Modern.jpg","width":3000,"height":2000,
            "mime":"image/jpeg","extmetadata":{"LicenseShortName":{"value":"CC BY-SA 4.0"}}}]},
          {"pageid":3,"title":"File:Small portrait.jpg","imageinfo":[{"url":"https://upload.wikimedia.org/wikipedia/commons/b/bb/Small_portrait.jpg","width":2400,"height":3000,
            "mime":"image/jpeg","thumburl":"https://upload.wikimedia.org/wikipedia/commons/b/bb/Small_portrait.jpg","thumbwidth":2400,"thumbheight":3000,
            "extmetadata":{"LicenseShortName":{"value":"Public domain"},"Artist":{"value":"Rembrandt"}}}]}
         ]}}
    """.trimIndent()

    @Test
    fun `public domain paintings with their texts and sizes`() {
        val (list, cont) = CommonsArtParser.parse(json)
        assertEquals("file|NEXT|123", cont)
        assertEquals(2, list.size)
        val starry = list[0]
        assertEquals("The Starry Night", starry.title)
        assertEquals("Vincent van Gogh", starry.artist)
        assertEquals("1889", starry.date)
        assertEquals("The view from his window.", starry.story)
        assertEquals("https://upload.wikimedia.org/wikipedia/commons/thumb/e/ea/Starry.jpg/3840px-Starry.jpg", starry.fullUrl)
        assertEquals("https://upload.wikimedia.org/wikipedia/commons/thumb/e/ea/Starry.jpg/1280px-Starry.jpg", starry.imageUrl)
        assertEquals(3840, starry.width)
        // No title: from the file name.
        val small = list[1]
        assertEquals("Small portrait", small.title)
        // Never the original file (Wikimedia limits those): a standard 1920 px rendition.
        assertEquals("https://upload.wikimedia.org/wikipedia/commons/thumb/b/bb/Small_portrait.jpg/1920px-Small_portrait.jpg", small.fullUrl)
        assertEquals("https://upload.wikimedia.org/wikipedia/commons/thumb/b/bb/Small_portrait.jpg/1280px-Small_portrait.jpg", small.imageUrl)
        val c = starry.candidate()
        assertTrue(c.key.startsWith("art:"))
        assertEquals(listOf(3840, 1280), c.variants.map { it.width })
    }
}
