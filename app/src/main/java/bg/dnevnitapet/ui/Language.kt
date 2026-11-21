package bg.dnevnitapet.ui

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.LocaleList
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import bg.dnevnitapet.R
import bg.dnevnitapet.i18n.AppLanguage

/**
 * First start: "Избери език · Choose a language" – both names written in their own language, so
 * everyone understands it. The phone's language is marked; a tap chooses.
 */
@Composable
fun LanguagePicker(suggested: AppLanguage, onPick: (AppLanguage) -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0xFF101418))) {
        Image(
            painterResource(R.mipmap.ic_launcher_background),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            alpha = 0.55f,
        )
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.2f), Color.Black.copy(alpha = 0.75f)))))
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 28.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.Bottom,
        ) {
            Text("Дневен тапет · Daily Wallpaper", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            Text("Избери език", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Choose a language", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(28.dp))
            AppLanguage.entries.forEach { lang ->
                LanguageCard(lang, marked = lang == suggested) { onPick(lang) }
                Spacer(Modifier.height(12.dp))
            }
            Text(
                "Може да се смени по всяко време в Настройки.\nYou can change it any time in Settings.",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun LanguageCard(lang: AppLanguage, marked: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (marked) SelectedOrange else Color.White.copy(alpha = 0.12f),
        contentColor = if (marked) OnSelectedOrange else Color.White,
        border = if (marked) null else BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth().height(64.dp),
    ) {
        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
                    .background(if (marked) OnSelectedOrange.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) { Text(lang.code.uppercase(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.width(16.dp))
            Text(lang.nativeName, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (marked) Icon(Icons.Filled.Check, contentDescription = null)
        }
    }
}

/** On Android 13+ the phone also knows the app's language (system screens, the widget's texts). */
fun applySystemLanguage(context: Context, lang: AppLanguage) {
    if (Build.VERSION.SDK_INT >= 33) {
        runCatching { context.getSystemService(LocaleManager::class.java)?.applicationLocales = LocaleList.forLanguageTags(lang.code) }
    }
}

/** Starts the app anew, so that every text is made again in the new language. */
fun restartApp(activity: Activity) {
    val intent = activity.packageManager.getLaunchIntentForPackage(activity.packageName)
        ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        ?: return activity.recreate()
    activity.startActivity(intent)
    activity.finishAffinity()
    Runtime.getRuntime().exit(0)
}
