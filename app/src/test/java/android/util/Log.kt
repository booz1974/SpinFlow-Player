package android.util

/**
 * Testdouble voor android.util.Log: getAllPlayers() roept in debug-builds Log.d aan
 * (MassApiClient.kt:159), en de gemockte android.jar gooit daar in unit tests een RuntimeException.
 * Zonder productiewijziging of testOptions.returnDefaultValues is dit de enige weg.
 */
object Log {
    @JvmStatic fun d(tag: String?, msg: String?): Int = 0
    @JvmStatic fun i(tag: String?, msg: String?): Int = 0
    @JvmStatic fun w(tag: String?, msg: String?): Int = 0
    @JvmStatic fun e(tag: String?, msg: String?): Int = 0
}
