package bg.dnevnitapet.sources

import bg.dnevnitapet.Fixtures
import bg.dnevnitapet.model.Category
import bg.dnevnitapet.sources.bing.BingParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryClassifierTest {
    @Test
    fun `classifies real Bing captions`() {
        val byKey = BingParser.parse(Fixtures.read("bing_en-US.json")).associateBy { it.key }
        assertEquals(Category.SEA, CategoryClassifier.classify(byKey.getValue("bing:RoseIsland")))
        assertEquals(Category.CITIES, CategoryClassifier.classify(byKey.getValue("bing:TrianaSpain")))
        assertEquals(Category.ANIMALS, CategoryClassifier.classify(byKey.getValue("bing:DakotaBison")))
        assertEquals(Category.NATURE, CategoryClassifier.classify(byKey.getValue("bing:PipiwaiTrail")))
        assertEquals(Category.MOUNTAINS, CategoryClassifier.classify(byKey.getValue("bing:AlpineBraies")))
    }

    @Test
    fun `most specific category wins`() {
        assertEquals(Category.ANIMALS, CategoryClassifier.classify("A red fox in a snowy forest"))
        assertEquals(Category.SPACE, CategoryClassifier.classify("The Milky Way over a mountain lake"))
        assertEquals(Category.ARCHITECTURE, CategoryClassifier.classify("Castle on a hill above the sea"))
        assertNull(CategoryClassifier.classify("Lanterns for Diwali"))
    }

    @Test
    fun `filter keeps unclassified pictures and drops disabled categories`() {
        val bison = BingParser.parse(Fixtures.read("bing_en-US.json")).first { it.key == "bing:DakotaBison" }
        val diwali = BingParser.parse(Fixtures.read("bing_en-US.json")).first { it.key == "bing:DiwaliLamps" }
        val noAnimals = Category.entries.toSet() - Category.ANIMALS
        assertFalse(CategoryClassifier.isAllowed(bison, noAnimals))
        assertTrue(CategoryClassifier.isAllowed(bison, Category.entries.toSet()))
        assertTrue(CategoryClassifier.isAllowed(diwali, setOf(Category.SPACE)))
    }
}
