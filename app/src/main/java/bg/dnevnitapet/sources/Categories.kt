package bg.dnevnitapet.sources

import bg.dnevnitapet.model.Candidate
import bg.dnevnitapet.model.Category

val Category.label: String
    get() = when (this) {
        Category.NATURE -> "Природа"
        Category.MOUNTAINS -> "Планини"
        Category.SEA -> "Море"
        Category.CITIES -> "Градове"
        Category.SPACE -> "Космос"
        Category.ANIMALS -> "Животни"
        Category.ARCHITECTURE -> "Архитектура"
    }

/** Search words used for sources that support searching (Unsplash, Pexels). */
val Category.searchQuery: String
    get() = when (this) {
        Category.NATURE -> "nature landscape"
        Category.MOUNTAINS -> "mountains"
        Category.SEA -> "ocean coast"
        Category.CITIES -> "city skyline"
        Category.SPACE -> "night sky stars"
        Category.ANIMALS -> "wildlife"
        Category.ARCHITECTURE -> "architecture"
    }

/**
 * Curated sources (Bing, Spotlight, Wikimedia) do not tag their pictures, so the
 * category is guessed from the caption. Each picture gets one main category, the most
 * specific one found; a picture with no recognised words gets none and is always allowed.
 */
object CategoryClassifier {
    private val priority = listOf(
        Category.SPACE, Category.ANIMALS, Category.ARCHITECTURE, Category.CITIES,
        Category.SEA, Category.MOUNTAINS, Category.NATURE,
    )

    private val words: Map<Category, Set<String>> = mapOf(
        Category.SPACE to setOf(
            "galaxy", "galaxies", "nebula", "nebulae", "comet", "meteor", "meteors", "planet", "planets",
            "saturn", "jupiter", "mars", "venus", "nasa", "telescope", "astronaut", "eclipse",
            "constellation", "supernova", "cosmos", "cosmic", "starry", "astronomy", "asteroid", "orbit",
        ),
        Category.ANIMALS to setOf(
            "animal", "animals", "wildlife", "bird", "birds", "bear", "bears", "fox", "foxes", "wolf", "wolves",
            "deer", "elk", "moose", "bison", "buffalo", "lion", "lions", "tiger", "leopard", "cheetah",
            "elephant", "elephants", "giraffe", "zebra", "zebras", "whale", "whales", "dolphin", "dolphins",
            "seal", "seals", "penguin", "penguins", "owl", "eagle", "hawk", "puffin", "puffins", "flamingo",
            "flamingos", "heron", "cranes", "parrot", "monkey", "monkeys", "gorilla", "chimpanzee",
            "orangutan", "lemur", "koala", "kangaroo", "wombat", "panda", "otter", "otters", "beaver",
            "squirrel", "rabbit", "hare", "horse", "horses", "cattle", "cow", "cows", "sheep", "goat",
            "goats", "ibex", "turtle", "tortoise", "frog", "lizard", "iguana", "snake", "crocodile",
            "alligator", "fish", "shark", "jellyfish", "octopus", "butterfly", "butterflies", "bee",
            "bees", "insect", "dragonfly", "hummingbird", "kingfisher", "swan", "swans", "duck", "ducks",
            "geese", "goose", "pelican", "pelicans", "macaque", "macaques", "reindeer", "caribou",
            "walrus", "hedgehog", "lynx", "camel", "camels", "llama", "alpaca", "hippo", "rhino",
            "meerkat", "meerkats", "vombatus", "tiere", "vögel", "animaux", "oiseaux", "animales",
        ),
        Category.ARCHITECTURE to setOf(
            "architecture", "cathedral", "church", "chapel", "basilica", "mosque", "temple", "temples",
            "monastery", "abbey", "castle", "palace", "fortress", "fort", "tower", "bridge", "lighthouse",
            "crypt", "cloister", "ruins", "pagoda", "shrine", "mausoleum", "building", "library",
            "museum", "opera", "dome", "aqueduct", "windmill", "windmills", "amphitheater",
            "colosseum", "pyramid", "pyramids", "staircase", "villa", "château", "chateau", "schloss",
            "burg", "kirche", "kathedrale", "cathédrale", "église", "chiesa", "iglesia", "castello",
            "castillo", "palacio", "palazzo",
        ),
        Category.CITIES to setOf(
            "city", "cities", "cityscape", "skyline", "downtown", "town", "street", "streets", "district",
            "square", "plaza", "metropolis", "neighborhood", "neighbourhood", "stadt", "altstadt",
            "ville", "città", "ciudad",
        ),
        Category.SEA to setOf(
            "sea", "ocean", "beach", "beaches", "coast", "coastal", "coastline", "shore", "bay", "island",
            "islands", "cove", "lagoon", "reef", "coral", "waves", "surf", "harbor", "harbour", "seaside",
            "atoll", "fjord", "archipelago", "peninsula", "underwater", "marine", "gulf", "meer",
            "strand", "küste", "insel", "mer", "plage", "mare", "mar", "playa", "isla",
        ),
        Category.MOUNTAINS to setOf(
            "mountain", "mountains", "mount", "peak", "peaks", "summit", "alps", "alpine", "dolomites",
            "himalaya", "himalayas", "andes", "rockies", "massif", "volcano", "volcanoes", "glacier",
            "glaciers", "ridge", "crag", "berg", "berge", "gebirge", "dolomiten", "montagne",
            "montagna", "montaña", "sierra", "cordillera",
        ),
        Category.NATURE to setOf(
            "nature", "forest", "forests", "woods", "tree", "trees", "lake", "lakes", "river", "waterfall",
            "waterfalls", "falls", "park", "desert", "dune", "dunes", "field", "fields", "meadow",
            "flowers", "flower", "garden", "autumn", "foliage", "winter", "snow", "canyon", "valley",
            "grassland", "grasslands", "prairie", "savanna", "jungle", "rainforest", "landscape",
            "sunset", "sunrise", "aurora", "cave", "geyser", "wetland", "marsh", "swamp", "bamboo",
            "cypress", "wald", "fluss", "forêt", "lac", "bosque", "lago", "bosco",
        ),
    )

    private val phrases: Map<Category, List<String>> = mapOf(
        Category.SPACE to listOf("milky way", "space station", "star cluster", "solar system", "night sky"),
        Category.ANIMALS to listOf("sea lion", "polar bear", "bee-eater", "bee-eaters"),
        Category.CITIES to listOf("old town", "city lights"),
        Category.NATURE to listOf("northern lights", "national park", "hot spring"),
    )

    private val tokenRegex = Regex("[\\p{L}-]+")

    fun classify(text: String): Category? {
        val lower = text.lowercase()
        val tokens = tokenRegex.findAll(lower).map { it.value }.toSet()
        for (category in priority) {
            val set = words[category].orEmpty()
            if (tokens.any { it in set }) return category
            if (phrases[category].orEmpty().any { lower.contains(it) }) return category
        }
        return null
    }

    fun classify(candidate: Candidate): Category? =
        candidate.category ?: classify(listOf(candidate.title, candidate.description, candidate.location).joinToString(" "))

    /** A picture is allowed when its main category is enabled, or when it has none. */
    fun isAllowed(candidate: Candidate, enabled: Set<Category>): Boolean {
        val category = classify(candidate) ?: return true
        return category in enabled
    }
}
