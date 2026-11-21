package bg.dnevnitapet.archive

/**
 * The main colour of a picture, for "търси по цвят". Pure logic on ARGB pixels, unit-tested.
 * Dark and grey pixels do not count; if hardly any pixel is colourful, the picture is "bw".
 */
object ColorBucket {
    /** Buckets in the order they are shown, with the colour of their dot. */
    val all: List<Pair<String, Long>> = listOf(
        "red" to 0xFFE53935, "orange" to 0xFFFB8C00, "yellow" to 0xFFFDD835, "green" to 0xFF43A047,
        "teal" to 0xFF00ACC1, "blue" to 0xFF1E88E5, "purple" to 0xFF8E24AA, "bw" to 0xFF9E9E9E,
    )

    fun label(bucket: String): String = when (bucket) {
        "red" -> "червено"
        "orange" -> "оранжево"
        "yellow" -> "жълто"
        "green" -> "зелено"
        "teal" -> "синьо-зелено"
        "blue" -> "синьо"
        "purple" -> "лилаво"
        else -> "черно-бяло"
    }

    fun of(pixels: IntArray): String {
        if (pixels.isEmpty()) return "bw"
        val counts = HashMap<String, Int>()
        var colourful = 0
        val hsv = FloatArray(3)
        for (p in pixels) {
            toHsv(p, hsv)
            if (hsv[1] < 0.28f || hsv[2] < 0.22f) continue
            colourful++
            val bucket = hueBucket(hsv[0])
            counts[bucket] = (counts[bucket] ?: 0) + 1
        }
        if (colourful < pixels.size * 0.08f) return "bw"
        return counts.maxByOrNull { it.value }!!.key
    }

    fun hueBucket(hue: Float): String = when {
        hue < 15f || hue >= 345f -> "red"
        hue < 45f -> "orange"
        hue < 70f -> "yellow"
        hue < 160f -> "green"
        hue < 200f -> "teal"
        hue < 255f -> "blue"
        else -> "purple"
    }

    private fun toHsv(argb: Int, out: FloatArray) {
        val r = (argb shr 16 and 0xFF) / 255f
        val g = (argb shr 8 and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val d = max - min
        val h = when {
            d == 0f -> 0f
            max == r -> 60f * (((g - b) / d) % 6f)
            max == g -> 60f * ((b - r) / d + 2f)
            else -> 60f * ((r - g) / d + 4f)
        }
        out[0] = if (h < 0) h + 360f else h
        out[1] = if (max == 0f) 0f else d / max
        out[2] = max
    }
}
