package bg.dnevnitapet.util

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.IntentCompat
import androidx.core.content.PackageManagerCompat
import androidx.core.content.UnusedAppRestrictionsConstants
import java.util.concurrent.TimeUnit

/** Battery-related checks and shortcuts into the system settings (Samsung friendly). */
object Power {
    fun isUnrestricted(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true

    /** System dialog "Allow app to always run in background?" – on Samsung this sets battery to "Unrestricted". */
    @SuppressLint("BatteryLife")
    fun requestUnrestricted(context: Context) {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + context.packageName))
        if (!start(context, intent)) openAppDetails(context)
    }

    fun openAppDetails(context: Context) {
        start(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName)))
    }

    /** Samsung "Battery" settings (where "Background usage limits" lives); generic fallback elsewhere. */
    fun openBatterySettings(context: Context) {
        val samsung = listOf(
            ComponentName("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity"),
            ComponentName("com.samsung.android.sm", "com.samsung.android.sm.battery.ui.BatteryActivity"),
        )
        for (component in samsung) {
            if (start(context, Intent().setComponent(component))) return
        }
        if (!start(context, Intent(Intent.ACTION_POWER_USAGE_SUMMARY))) {
            start(context, Intent(Settings.ACTION_SETTINGS))
        }
    }

    fun openExactAlarmSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= 31) {
            if (start(context, Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + context.packageName)))) return
        }
        openAppDetails(context)
    }

    fun isSamsung(): Boolean = Build.MANUFACTURER.equals("samsung", ignoreCase = true)

    /**
     * Android pauses apps that have not been opened for a few months ("Pause app activity if unused"),
     * which would also stop the daily change. Returns true if that can happen, null if unknown.
     * Blocking call – use off the main thread.
     */
    fun unusedAppPauseActive(context: Context): Boolean? = try {
        when (PackageManagerCompat.getUnusedAppRestrictionsStatus(context).get(3, TimeUnit.SECONDS)) {
            UnusedAppRestrictionsConstants.DISABLED -> false
            UnusedAppRestrictionsConstants.API_30_BACKPORT,
            UnusedAppRestrictionsConstants.API_30,
            UnusedAppRestrictionsConstants.API_31,
            -> true
            else -> null
        }
    } catch (e: Exception) {
        null
    }

    fun openUnusedAppSettings(context: Context) {
        val intent = IntentCompat.createManageUnusedAppRestrictionsIntent(context, context.packageName)
        if (!start(context, intent)) openAppDetails(context)
    }

    private fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: Exception) {
        false
    }
}
