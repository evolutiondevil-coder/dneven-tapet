package bg.dnevnitapet.update

import bg.dnevnitapet.ui.whatsNew
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdaterTest {
    @Test
    fun comparesReleaseVersions() {
        assertTrue(Versions.isNewer("v1.3.1", "1.3.0"))
        assertTrue(Versions.isNewer("v1.10.0", "1.9.9"))
        assertTrue(Versions.isNewer("v2.0.0", "1.13.4"))
        assertFalse(Versions.isNewer("v1.3.0", "1.3.0"))
        assertFalse(Versions.isNewer("v1.2.9", "1.3.0"))
    }

    @Test
    fun testBuildsNeverAskForUpdates() {
        assertFalse(Versions.isNewer("v1.3.0", "1.0.31-dev"))
        assertFalse(Versions.isNewer("v1.3.0", "0.0.0-local"))
        assertFalse(Versions.isNewer("nightly", "1.3.0"))
    }

    private val sample = """
        {"tag_name":"v1.4.0","html_url":"https://github.com/o/r/releases/tag/v1.4.0","draft":false,"prerelease":false,
         "body":"## Какво ново във v1.4.0\n\n- **Едно:** нещо\n\n**Инсталиране:** докосни файла",
         "assets":[{"name":"notes.txt","browser_download_url":"https://github.com/o/r/notes.txt","size":10},
                   {"name":"DnevenTapet-1.4.0.apk","browser_download_url":"https://github.com/o/r/releases/download/v1.4.0/DnevenTapet-1.4.0.apk","size":5242880}],
         "author":{"login":"x"}}
    """.trimIndent()

    @Test
    fun readsTheLatestReleaseFromGitHub() {
        val r = ReleaseParser.parse(sample)!!
        assertEquals("v1.4.0", r.tag)
        assertEquals("1.4.0", r.version)
        assertTrue(r.apkUrl.endsWith("DnevenTapet-1.4.0.apk"))
        assertEquals(5242880L, r.apkSize)
    }

    @Test
    fun releaseWithoutApkOrPrereleaseIsIgnored() {
        assertNull(ReleaseParser.parse("""{"tag_name":"v1.4.0","assets":[]}"""))
        assertNull(ReleaseParser.parse(sample.replace("\"prerelease\":false", "\"prerelease\":true")))
    }

    @Test
    fun showsOnlyWhatIsNew() {
        val text = whatsNew(ReleaseParser.parse(sample)!!.notes)
        assertEquals("- Едно: нещо", text)
    }
}
