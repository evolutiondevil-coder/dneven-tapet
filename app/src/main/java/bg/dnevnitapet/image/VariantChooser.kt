package bg.dnevnitapet.image

import bg.dnevnitapet.model.ImageVariant
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Picks the best rendition of a picture for a screen of [targetW] x [targetH] (portrait). */
object VariantChooser {
    /** A portrait rendition made by the source is preferred while it needs at most this much enlarging. */
    const val PORTRAIT_PREFERENCE_MAX_UPSCALE = 1.25

    /** Pictures that would have to be enlarged more than this look soft, so they are skipped. */
    const val MAX_UPSCALE = 1.6

    data class Choice(val url: String, val variant: ImageVariant, val upscale: Double)

    /**
     * How much the variant must be enlarged after cropping it to the screen's aspect ratio.
     * 1.0 = exactly screen resolution, < 1 = more pixels than needed, unknown size = 1.0.
     */
    fun upscale(v: ImageVariant, targetW: Int, targetH: Int): Double {
        if (v.width <= 0 || v.height <= 0) return 1.0
        // Largest crop with the screen's aspect ratio that fits inside the picture.
        val targetAspect = targetW.toDouble() / targetH
        val cropH = min(v.height.toDouble(), v.width / targetAspect)
        return targetH / cropH
    }

    fun choose(variants: List<ImageVariant>, targetW: Int, targetH: Int): Choice? {
        if (variants.isEmpty()) return null
        fun scale(v: ImageVariant) = upscale(v, targetW, targetH)

        // Server-side crop to the exact screen size (Unsplash, Pexels).
        variants.firstOrNull { it.sizedTemplate && scale(it) <= MAX_UPSCALE }?.let { v ->
            val s = scale(v)
            // Never ask the server to enlarge: request at most the original resolution.
            val factor = if (s > 1.0) 1.0 / s else 1.0
            val w = max(1, (targetW * factor).roundToInt())
            val h = max(1, (targetH * factor).roundToInt())
            return Choice(v.url.replace("{w}", w.toString()).replace("{h}", h.toString()), v, s)
        }

        val plain = variants.filter { !it.sizedTemplate }
        if (plain.isEmpty()) return null

        plain.filter { it.portrait && scale(it) <= PORTRAIT_PREFERENCE_MAX_UPSCALE }
            .minByOrNull { pixels(it) }
            ?.let { return Choice(it.url, it, scale(it)) }

        val sharp = plain.filter { scale(it) <= 1.0 }
        val best = if (sharp.isNotEmpty()) sharp.minBy { pixels(it) } else plain.minBy { scale(it) }
        val s = scale(best)
        if (s > MAX_UPSCALE) return null
        return Choice(best.url, best, s)
    }

    /**
     * The rendition shown in the crop editor: as much of the picture as possible (for Bing and
     * Spotlight the wide 3840 px one rather than the portrait one) but not a huge original.
     */
    fun editSource(variants: List<ImageVariant>): ImageVariant? {
        val plain = variants.filter { !it.sizedTemplate }
        if (plain.isEmpty()) return null
        val known = plain.filter { it.width > 0 && it.height > 0 }
        if (known.isEmpty()) return plain.first()
        val reasonable = known.filter { max(it.width, it.height) <= 4100 }
        return reasonable.ifEmpty { known }.maxBy { pixels(it) }
    }

    private fun pixels(v: ImageVariant): Long =
        if (v.width <= 0 || v.height <= 0) Long.MAX_VALUE else v.width.toLong() * v.height
}
