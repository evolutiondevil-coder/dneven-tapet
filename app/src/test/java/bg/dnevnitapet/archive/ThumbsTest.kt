package bg.dnevnitapet.archive

import bg.dnevnitapet.model.Candidate
import bg.dnevnitapet.model.ImageVariant
import org.junit.Assert.assertEquals
import org.junit.Test

class ThumbsTest {
    private fun c(vararg v: ImageVariant) = Candidate(key = "k", sourceId = "s", title = "t", variants = v.toList())

    @Test
    fun `small renditions are asked from the servers`() {
        assertEquals(
            "https://upload.wikimedia.org/wikipedia/commons/thumb/a/ab/X.jpg/500px-X.jpg",
            Thumbs.url(
                c(
                    ImageVariant("https://upload.wikimedia.org/wikipedia/commons/thumb/a/ab/X.jpg/3840px-X.jpg", 3840, 2560),
                    ImageVariant("https://upload.wikimedia.org/wikipedia/commons/thumb/a/ab/X.jpg/1920px-X.jpg", 1920, 1280),
                ),
            ),
        )
        assertEquals(
            "https://upload.wikimedia.org/wikipedia/commons/thumb/c/cd/Y.jpg/500px-Y.jpg",
            Thumbs.url(c(ImageVariant("https://upload.wikimedia.org/wikipedia/commons/c/cd/Y.jpg?utm_source=x", 1600, 1200))),
        )
        assertEquals(
            "https://assets.science.nasa.gov/dynamicimage/assets/science/psd/apod/2025/a.jpg?w=480&h=480&fit=clip",
            Thumbs.url(c(ImageVariant("https://assets.science.nasa.gov/content/dam/science/psd/apod/2025/a.jpg", 4000, 3000))),
        )
        assertEquals(
            "https://images.unsplash.com/p?w=480&h=640",
            Thumbs.url(c(ImageVariant("https://images.unsplash.com/p?w={w}&h={h}", 5000, 3000, sizedTemplate = true))),
        )
    }
}
