package bg.dnevnitapet.wallpaper

import bg.dnevnitapet.model.PhotoMode
import kotlin.random.Random

/**
 * Rules for the user's own photos. Pure logic (no Android), unit-tested.
 *
 * Own photos come in rounds: every photo once, in shuffled order, then a new shuffled round.
 * A new round never starts with the photo that is on screen right now.
 */
object MyPhotos {
    const val SOURCE_ID = "mine"
    const val MAX_PHOTOS = 100

    fun isMine(key: String?) = key != null && key.startsWith("$SOURCE_ID:")

    /**
     * The next own photo and the rest of the round after it.
     * @param album keys of all usable own photos (blocked ones already removed), in album order.
     * @param rotation the rest of the current round.
     */
    fun nextKey(album: List<String>, rotation: List<String>, currentKey: String?, random: Random = Random.Default): Pair<String, List<String>>? {
        if (album.isEmpty()) return null
        var round = rotation.filter { it in album }
        if (round.isEmpty()) round = newRound(album, currentKey, random)
        return round.first() to round.drop(1)
    }

    /** Own photos in the order they will come (the rest of this round, then the others). */
    fun upcoming(album: List<String>, rotation: List<String>, currentKey: String?): List<String> {
        val round = rotation.filter { it in album && it != currentKey }
        val rest = album.filter { it !in round && it != currentKey }
        return round + rest
    }

    private fun newRound(album: List<String>, currentKey: String?, random: Random): List<String> {
        val shuffled = album.shuffled(random)
        if (shuffled.size > 1 && shuffled.first() == currentKey) return shuffled.drop(1) + shuffled.first()
        return shuffled
    }

    /**
     * Should the next change use an own photo?
     * Mixed mode alternates: after an own photo an internet picture, and the other way round.
     */
    fun useMineNext(mode: PhotoMode, currentKey: String?, hasMine: Boolean): Boolean = when (mode) {
        PhotoMode.ONLINE -> false
        PhotoMode.MINE -> true
        PhotoMode.MIXED -> hasMine && !isMine(currentKey)
    }

    /**
     * The upcoming pictures as the main screen shows them, in the order the changes will use them.
     * Mixed mode interleaves the two lists, starting with the kind that comes next.
     */
    fun <T> upcomingMixed(mode: PhotoMode, online: List<T>, mine: List<T>, currentIsMine: Boolean): List<T> = when (mode) {
        PhotoMode.ONLINE -> online
        PhotoMode.MINE -> mine
        PhotoMode.MIXED -> {
            val out = ArrayList<T>(online.size + mine.size)
            var mineTurn = !currentIsMine
            var i = 0
            var j = 0
            while (i < online.size || j < mine.size) {
                if (mineTurn && j < mine.size || i >= online.size) out += mine[j++] else out += online[i++]
                mineTurn = !mineTurn
            }
            out
        }
    }
}
