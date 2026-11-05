package com.fireamun.iptv.prototype

import android.app.Application
import android.app.UiModeManager
import android.content.Context
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import com.posthog.android.PostHogAndroid

class IptvApplication : Application() {
    val libraryStore by lazy { com.fireamun.iptv.core.storage.RoomLibraryStore.open(this) }
    private val progressScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
    val progressRecorder by lazy { PlaybackProgressRecorder(progressScope, libraryStore) }
    val artworkLoader by lazy { protectedImageLoader(this, libraryStore) }
    override fun onCreate() {
        super.onCreate()
        initializePostHog()
        Appearance.apply(this)
    }

    private fun initializePostHog() {
        // Monitoring is optional; configuration or SDK failure must not block playback.
        runCatching {
            postHogConfiguration(BuildConfig.POSTHOG_API_KEY, BuildConfig.POSTHOG_HOST)?.let {
                PostHogAndroid.setup(this, it)
            }
        }
    }

}

/** Appearance is a nonsecret setting; sources never enter these preferences. */
internal object Appearance {
    fun isDark(context: Context): Boolean = context.getSharedPreferences("appearance", Context.MODE_PRIVATE).getBoolean("dark", true)
    fun toggle(context: Context) {
        setDark(context, !isDark(context))
    }
    fun setDark(context: Context, dark: Boolean) {
        context.getSharedPreferences("appearance", Context.MODE_PRIVATE).edit().putBoolean("dark", dark).apply()
        apply(context)
    }
    fun apply(context: Context) {
        if (Build.VERSION.SDK_INT >= 31) context.getSystemService(UiModeManager::class.java).setApplicationNightMode(if (isDark(context)) UiModeManager.MODE_NIGHT_YES else UiModeManager.MODE_NIGHT_NO)
        else AppCompatDelegate.setDefaultNightMode(if (isDark(context)) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO)
    }
}
