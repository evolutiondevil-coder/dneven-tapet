package bg.dnevnitapet.wallpaper

import bg.dnevnitapet.net.Http
import bg.dnevnitapet.story.Places
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.withTimeoutOrNull

/** Today's weather in a city (Open-Meteo, free, no account); asked at most every 90 minutes. */
class WeatherService(private val http: Http, private val places: Places) {
    private var cached: Triple<String, Long, PictureRules.Sky>? = null

    suspend fun sky(city: String): PictureRules.Sky? {
        val name = city.trim()
        if (name.isEmpty()) return null
        cached?.let { (c, at, sky) -> if (c == name && System.currentTimeMillis() - at < 90 * 60_000L) return sky }
        return withTimeoutOrNull(10_000) {
            runCatching {
                val (lat, lon) = places.locate(name) ?: return@runCatching null
                val json = http.getText("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=weather_code")
                val code = Json.parseToJsonElement(json).jsonObject["current"]?.jsonObject?.get("weather_code")?.jsonPrimitive?.intOrNull
                    ?: return@runCatching null
                PictureRules.sky(code).also { cached = Triple(name, System.currentTimeMillis(), it) }
            }.getOrNull()
        }
    }
}
