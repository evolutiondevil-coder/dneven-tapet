package bg.dnevnitapet.sources

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Lenient helpers: sources change their answers, so missing fields must never crash. */
internal fun parseJson(text: String): JsonElement =
    try {
        Json.parseToJsonElement(text)
    } catch (e: Exception) {
        throw SourceFormatException("Отговорът не е JSON")
    }

internal fun JsonElement?.obj(): JsonObject? = this as? JsonObject
internal fun JsonElement?.arr(): JsonArray? = this as? JsonArray

internal fun JsonObject?.str(name: String): String? {
    val el = this?.get(name) ?: return null
    if (el is JsonNull) return null
    return (el as? JsonPrimitive)?.contentOrNull
}

internal fun JsonObject?.int(name: String): Int? = str(name)?.toIntOrNull()

internal fun JsonObject?.bool(name: String): Boolean? = when (str(name)?.lowercase()) {
    "true" -> true
    "false" -> false
    else -> null
}

internal fun JsonObject?.child(name: String): JsonObject? = this?.get(name).obj()
