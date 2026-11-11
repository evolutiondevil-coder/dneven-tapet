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

/** Battery-related checks and shortcuts into the system settings, for Samsung and other makers. */
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

    /** Battery settings: Samsung's own screen ("Background usage limits"), else the standard one. */
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

    fun isSamsung(): Boolean = vendor() == Vendor.SAMSUNG

    /** Phone makers whose systems stop background apps in their own way. */
    enum class Vendor(val label: String) {
        SAMSUNG("Samsung (One UI)"),
        XIAOMI("Xiaomi, Redmi, POCO (HyperOS / MIUI)"),
        HUAWEI("Huawei / Honor"),
        OPPO("OPPO, realme, OnePlus (ColorOS / OxygenOS)"),
        VIVO("vivo / iQOO"),
        OTHER("Google Pixel, Motorola, Nokia, Sony и други"),
    }

    fun vendor(): Vendor {
        val m = (Build.MANUFACTURER + " " + Build.BRAND).lowercase()
        return when {
            "samsung" in m -> Vendor.SAMSUNG
            "xiaomi" in m || "redmi" in m || "poco" in m -> Vendor.XIAOMI
            "huawei" in m || "honor" in m -> Vendor.HUAWEI
            "oppo" in m || "realme" in m || "oneplus" in m -> Vendor.OPPO
            "vivo" in m || "iqoo" in m -> Vendor.VIVO
            else -> Vendor.OTHER
        }
    }

    /** Step-by-step instructions (Bulgarian) for keeping the app running on this phone. */
    fun steps(vendor: Vendor): String = when (vendor) {
        Vendor.SAMSUNG ->
            "1. Бутон „Разреши“ горе → „Разреши“. Това слага батерията на „Без ограничения“.\n" +
                "2. Настройки → Батерия → Ограничения за използване във фонов режим → " +
                "„Приложения, които никога не заспиват“ → + → Дневен тапет.\n" +
                "3. В същия екран можеш да изключиш „Приспиване на неизползвани приложения“.\n" +
                "4. Настройки → Приложения → Дневен тапет → изключи „Пауза на активността, ако не се използва“."
        Vendor.XIAOMI ->
            "1. Бутон „Разреши“ горе → „Разреши“.\n" +
                "2. Бутон „Автоматично стартиране“ отдолу (или Настройки → Приложения → Управление на приложения → " +
                "Дневен тапет) → включи „Автоматично стартиране“.\n" +
                "3. Там: Пестене на батерията → „Без ограничения“.\n" +
                "4. В списъка с последните приложения задръж Дневен тапет → катинарче, за да не се затваря при почистване."
        Vendor.HUAWEI ->
            "1. Бутон „Разреши“ горе → „Разреши“.\n" +
                "2. Бутон „Автоматично стартиране“ отдолу (или Настройки → Батерия → Стартиране на приложения) → " +
                "Дневен тапет → изключи „Автоматично управление“ и включи и трите ключа.\n" +
                "3. В последните приложения дръпни Дневен тапет надолу → катинарче."
        Vendor.OPPO ->
            "1. Бутон „Разреши“ горе → „Разреши“.\n" +
                "2. Настройки → Приложения → Дневен тапет → Използване на батерията → включи " +
                "„Работа на заден план“ и „Автоматично стартиране“.\n" +
                "3. В последните приложения задръж Дневен тапет → „Заключи“."
        Vendor.VIVO ->
            "1. Бутон „Разреши“ горе → „Разреши“.\n" +
                "2. Настройки → Батерия → Консумация на заден план → Дневен тапет → „Разреши висока консумация на заден план“.\n" +
                "3. Бутон „Автоматично стартиране“ отдолу (i Manager → Управление на приложения → Автоматично стартиране) → включи."
        Vendor.OTHER ->
            "1. Бутон „Разреши“ горе → „Разреши“ (батерията става „Без ограничения“). На повечето телефони това е достатъчно.\n" +
                "2. Ако все пак смяната се пропуска: Настройки → Приложения → Дневен тапет → Батерия → „Без ограничения“."
    }

    /** Opens the maker's "auto-start" screen where one exists; returns false if there is none. */
    fun openAutoStartSettings(context: Context): Boolean {
        val candidates = when (vendor()) {
            Vendor.XIAOMI -> listOf(
                ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
            )
            Vendor.HUAWEI -> listOf(
                ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
                ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
                ComponentName("com.hihonor.systemmanager", "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
            )
            Vendor.OPPO -> listOf(
                ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
                ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
                ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
            )
            Vendor.VIVO -> listOf(
                ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
                ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
            )
            else -> emptyList()
        }
        return candidates.any { start(context, Intent().setComponent(it)) }
    }

    fun hasAutoStartScreen(): Boolean = vendor() in setOf(Vendor.XIAOMI, Vendor.HUAWEI, Vendor.OPPO, Vendor.VIVO)

    /** Android "Data Saver": blocks mobile data for apps in the background. */
    fun dataSaverBlocksBackground(context: Context): Boolean {
        val cm = context.getSystemService(android.net.ConnectivityManager::class.java) ?: return false
        return cm.isActiveNetworkMetered &&
            cm.restrictBackgroundStatus == android.net.ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED
    }

    fun openDataSaverSettings(context: Context) {
        val intent = Intent(Settings.ACTION_IGNORE_BACKGROUND_DATA_RESTRICTIONS_SETTINGS, Uri.parse("package:" + context.packageName))
        if (!start(context, intent)) openAppDetails(context)
    }

    fun openUrl(context: Context, url: String) {
        start(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

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
