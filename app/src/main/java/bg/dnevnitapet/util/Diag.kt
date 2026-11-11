package bg.dnevnitapet.util

import android.util.Log

/** Short diagnostic lines in the phone's own log (logcat, tag "DnevenTapet"). Nothing leaves the phone. */
object Diag {
    fun i(message: String) {
        Log.i("DnevenTapet", message)
    }
}
