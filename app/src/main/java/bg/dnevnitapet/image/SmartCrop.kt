package bg.dnevnitapet.image

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Chooses where to cut a picture so that its main subject stays on screen.
 *
 * Works on a small copy of the picture (a few hundred pixels wide). Every pixel gets an
 * "interest" score made of:
 *  - how different its colour is from the picture's average colour (frequency-tuned saliency),
 *  - local detail (edges),
 *  - colour saturation,
 *  - skin tones and detected faces (people should never be cut in half).
 * The crop window is then slid across the picture and the position that keeps the most
 * interest, preferring the subject well inside the window rather than at its edge, wins.
 *
 * Pure Kotlin so that it can be unit-tested on the JVM.
 */
object SmartCrop {
    data class Rect(val left: Int, val top: Int, val width: Int, val height: Int) {
        val right get() = left + width
        val bottom get() = top + height
    }

    /** A region known to be important, e.g. a detected face, in analysis-image coordinates. */
    data class Boost(val left: Int, val top: Int, val right: Int, val bottom: Int, val weight: Double = 1.0)

    /**
     * @param argb pixels of the analysis image, row by row.
     * @param aspect wanted width / height of the crop.
     * @return the largest crop with [aspect] that keeps the subject.
     */
    fun findCrop(argb: IntArray, w: Int, h: Int, aspect: Double, boosts: List<Boost> = emptyList()): Rect {
        require(argb.size >= w * h && w > 0 && h > 0)
        val cropW = min(w, (h * aspect).roundToInt()).coerceAtLeast(1)
        val cropH = min(h, (w / aspect).roundToInt()).coerceAtLeast(1)
        val horizontal = cropW < w
        val vertical = cropH < h
        if (!horizontal && !vertical) return Rect(0, 0, w, h)

        val importance = importanceMap(argb, w, h, boosts)
        return if (horizontal) {
            val cols = DoubleArray(w)
            for (y in 0 until h) for (x in 0 until w) cols[x] += importance[y * w + x]
            Rect(bestOffset(cols, cropW), 0, cropW, h)
        } else {
            val rows = DoubleArray(h)
            for (y in 0 until h) for (x in 0 until w) rows[y] += importance[y * w + x]
            // Slight preference for the upper part: skies and faces sit higher than the middle.
            Rect(0, bestOffset(rows, cropH, centre = 0.45), w, cropH)
        }
    }

    /** Centre crop with the given aspect, used for pictures already composed for portrait. */
    fun centreCrop(w: Int, h: Int, aspect: Double): Rect {
        val cropW = min(w, (h * aspect).roundToInt())
        val cropH = min(h, (w / aspect).roundToInt())
        return Rect((w - cropW) / 2, (h - cropH) / 2, cropW, cropH)
    }

    internal fun importanceMap(argb: IntArray, w: Int, h: Int, boosts: List<Boost>): DoubleArray {
        val n = w * h
        val r = DoubleArray(n)
        val g = DoubleArray(n)
        val b = DoubleArray(n)
        val luma = DoubleArray(n)
        var mr = 0.0
        var mg = 0.0
        var mb = 0.0
        for (i in 0 until n) {
            val c = argb[i]
            r[i] = ((c shr 16) and 0xFF) / 255.0
            g[i] = ((c shr 8) and 0xFF) / 255.0
            b[i] = (c and 0xFF) / 255.0
            luma[i] = 0.299 * r[i] + 0.587 * g[i] + 0.114 * b[i]
            mr += r[i]; mg += g[i]; mb += b[i]
        }
        mr /= n; mg /= n; mb /= n

        // 3x3 blur before the colour distance removes noise and fine texture.
        val br = blur(r, w, h)
        val bg = blur(g, w, h)
        val bb = blur(b, w, h)

        val saliency = DoubleArray(n)
        val edges = DoubleArray(n)
        val sat = DoubleArray(n)
        val skin = DoubleArray(n)
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            val dr = br[i] - mr
            val dg = bg[i] - mg
            val db = bb[i] - mb
            saliency[i] = sqrt(dr * dr + dg * dg + db * db)

            val l = luma[i]
            val left = luma[y * w + max(0, x - 1)]
            val right = luma[y * w + min(w - 1, x + 1)]
            val up = luma[max(0, y - 1) * w + x]
            val down = luma[min(h - 1, y + 1) * w + x]
            edges[i] = abs(4 * l - left - right - up - down)

            val mx = max(r[i], max(g[i], b[i]))
            val mn = min(r[i], min(g[i], b[i]))
            sat[i] = if (mx > 0.05 && l in 0.08..0.92) (mx - mn) / mx else 0.0

            skin[i] = skinScore(r[i], g[i], b[i], l)
        }
        normalise(saliency)
        normalise(edges)

        val out = DoubleArray(n)
        for (i in 0 until n) {
            out[i] = 0.55 * saliency[i] + 0.25 * edges[i] + 0.12 * sat[i] + 0.6 * skin[i]
        }
        for (boost in boosts) {
            val x0 = boost.left.coerceIn(0, w - 1)
            val x1 = boost.right.coerceIn(0, w - 1)
            val y0 = boost.top.coerceIn(0, h - 1)
            val y1 = boost.bottom.coerceIn(0, h - 1)
            for (y in y0..y1) for (x in x0..x1) out[y * w + x] += 3.0 * boost.weight
        }
        return out
    }

    private fun skinScore(r: Double, g: Double, b: Double, l: Double): Double {
        if (l < 0.2 || l > 0.9) return 0.0
        val mag = sqrt(r * r + g * g + b * b)
        if (mag == 0.0) return 0.0
        // Cosine similarity to a typical skin tone.
        val sr = 0.78; val sg = 0.57; val sb = 0.44
        val smag = sqrt(sr * sr + sg * sg + sb * sb)
        val cos = (r * sr + g * sg + b * sb) / (mag * smag)
        return if (cos > 0.985 && r > g && g > b) (cos - 0.985) / 0.015 else 0.0
    }

    /**
     * Best start offset for a window of [size] over [profile]. Interest near the window's centre
     * counts fully, interest at its edges counts less, and a small bias keeps the crop near [centre].
     */
    internal fun bestOffset(profile: DoubleArray, size: Int, centre: Double = 0.5): Int {
        val length = profile.size
        if (size >= length) return 0
        val weights = DoubleArray(size) { k ->
            val t = (k + 0.5) / size * 2 - 1 // -1..1 across the window
            1.0 - 0.55 * t * t
        }
        val maxOffset = length - size
        val sum = profile.sum()
        // Nothing stands out (flat picture): cut around the preferred centre.
        if (sum < 1e-6) return (maxOffset * centre).roundToInt().coerceIn(0, maxOffset)
        val total = sum
        var best = 0
        var bestScore = Double.NEGATIVE_INFINITY
        for (offset in 0..maxOffset) {
            var s = 0.0
            for (k in 0 until size) s += profile[offset + k] * weights[k]
            val position = offset.toDouble() / maxOffset
            val bias = 1.0 - 0.08 * abs(position - centre) * 2
            val score = s / total * bias
            if (score > bestScore) {
                bestScore = score
                best = offset
            }
        }
        return best
    }

    private fun blur(src: DoubleArray, w: Int, h: Int): DoubleArray {
        val out = DoubleArray(src.size)
        for (y in 0 until h) for (x in 0 until w) {
            var s = 0.0
            var c = 0
            for (dy in -1..1) for (dx in -1..1) {
                val xx = x + dx
                val yy = y + dy
                if (xx in 0 until w && yy in 0 until h) {
                    s += src[yy * w + xx]; c++
                }
            }
            out[y * w + x] = s / c
        }
        return out
    }

    private fun normalise(a: DoubleArray) {
        var mx = 0.0
        for (v in a) if (v > mx) mx = v
        if (mx <= 0) return
        for (i in a.indices) a[i] /= mx
    }
}
