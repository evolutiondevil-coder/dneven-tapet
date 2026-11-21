package bg.dnevnitapet.i18n

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Locale

/** The app's languages. The texts are written in Bulgarian in the code; English comes from i18n/en.json. */
enum class AppLanguage(val code: String, val nativeName: String) {
    BG("bg", "Български"),
    EN("en", "English"),
    ;

    companion object {
        fun of(code: String): AppLanguage? = entries.firstOrNull { it.code == code }

        /** The phone's language when the user has not chosen yet: Bulgarian phones Bulgarian, others English. */
        fun fromDevice(): AppLanguage = if (Locale.getDefault().language == "bg") BG else EN
    }
}

object I18n {
    /** The language of the app's texts (Compose redraws when it changes). */
    var language by mutableStateOf(AppLanguage.BG)

    val locale: Locale get() = if (language == AppLanguage.EN) Locale.ENGLISH else Locale("bg")

    private val english: Map<String, String> by lazy {
        runCatching {
            val text = I18n::class.java.classLoader!!.getResourceAsStream("i18n/en.json")!!.bufferedReader().use { it.readText() }
            (Json.parseToJsonElement(text) as JsonObject).mapValues { it.value.jsonPrimitive.content }
        }.getOrDefault(emptyMap())
    }

    /** English translations of the Bulgarian texts (for the tests). */
    fun englishTable(): Map<String, String> = english

    fun translate(bulgarian: String): String =
        if (language == AppLanguage.EN) english[bulgarian] ?: bulgarian else bulgarian
}

/**
 * A text of the app in the chosen language. [text] is the Bulgarian original; {0}, {1}… are
 * replaced by [args] (numbers, names) after translating.
 */
fun tr(text: String, vararg args: Any?): String {
    var s = I18n.translate(text)
    args.forEachIndexed { i, a -> s = s.replace("{$i}", a.toString()) }
    return s
}
