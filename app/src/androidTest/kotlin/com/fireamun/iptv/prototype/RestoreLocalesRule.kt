package com.fireamun.iptv.prototype

import android.app.LocaleManager
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.ExternalResource

/** Establish English before launch and restore after Compose/scenario disposal, avoiding UI races. */
class RestoreLocalesRule(private val diagnostics: TestDiagnostics) : ExternalResource() {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private var original = LocaleListCompat.getEmptyLocaleList()
    override fun before() {
        instrumentation.runOnMainSync {
            original = if (Build.VERSION.SDK_INT >= 33) LocaleListCompat.wrap(instrumentation.targetContext.getSystemService(LocaleManager::class.java).applicationLocales)
            else AppCompatDelegate.getApplicationLocales()
            setLocales(LocaleListCompat.forLanguageTags("en"))
        }
    }
    override fun after() {
        diagnostics.phase(TestDiagnostics.Phase.BEFORE_LOCALE_RESET)
        instrumentation.runOnMainSync {
            setLocales(original)
        }
        diagnostics.phase(TestDiagnostics.Phase.AFTER_LOCALE_RESET)
    }
    private fun setLocales(locales: LocaleListCompat) {
        if (Build.VERSION.SDK_INT >= 33) instrumentation.targetContext.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(locales.toLanguageTags())
        else AppCompatDelegate.setApplicationLocales(locales)
    }
}
