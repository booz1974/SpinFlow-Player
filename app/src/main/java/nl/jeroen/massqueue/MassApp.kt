package nl.jeroen.massqueue

import android.app.Application
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient

/**
 * Houdt het actieve server-adres + token bij, zodat plaatjes van onze eigen server
 * het token als Authorization-header krijgen in plaats van als `?token=` in de URL
 * (dat belandde anders in Coil's cache-sleutels en in serverlogs).
 */
object ServerAuth {
    @Volatile private var base: HttpUrl? = null
    @Volatile private var token: String? = null

    fun update(baseUrl: String, newToken: String?) {
        base = baseUrl.toHttpUrlOrNull()
        token = newToken
    }

    /** Of [url] naar de ingestelde MA-server wijst (zelfde scheme, host en poort). */
    fun isServerUrl(url: HttpUrl): Boolean {
        val b = base ?: return false
        return url.scheme == b.scheme && url.host == b.host && url.port == b.port
    }

    val interceptor = Interceptor { chain ->
        val req = chain.request()
        val t = token
        if (isServerUrl(req.url) && !t.isNullOrBlank() && req.header("Authorization") == null) {
            chain.proceed(req.newBuilder().header("Authorization", "Bearer $t").build())
        } else {
            chain.proceed(req)
        }
    }
}

class MassApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        // Taal vóór de eerste tekst (UI, meldingen, Android Auto) bekend maken
        // Kapotte/onleesbare DataStore mag de start niet laten crashen: dan systeemtaal
        Lang.choice = try {
            runBlocking { SettingsStore(this@MassApp).loadLanguage() }
        } catch (e: Exception) {
            Log.w("MassApp", "Taalkeuze niet te lezen, systeemtaal gebruikt", e)
            AppLanguage.SYSTEM
        }
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .okHttpClient { OkHttpClient.Builder().addInterceptor(ServerAuth.interceptor).build() }
            .build()
}
