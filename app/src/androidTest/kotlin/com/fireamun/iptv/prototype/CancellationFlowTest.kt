package com.fireamun.iptv.prototype

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.core.os.LocaleListCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CancellationFlowTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun cancellingProviderOpeningKeepsTheAppUsable() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        Intents.init()
        try {
            Intents.intending(hasAction(Intent.ACTION_OPEN_DOCUMENT)).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK,
                Intent().setData(Uri.parse("content://com.fireamun.iptv.prototype.test.fixtures/slow")).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)))
            ActivityScenario.launch<MobileActivity>(Intent(instrumentation.targetContext, MobileActivity::class.java)).use { scenario ->
                try {
                    scenario.onActivity { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en")) }
                    compose.onNodeWithTag("import_file").performClick()
                    compose.waitUntil(timeoutMillis = 4000) {
                        instrumentation.targetContext.contentResolver.query(Uri.parse("content://com.fireamun.iptv.prototype.test.fixtures/slow-state"), null, null, null, null)?.use { it.moveToFirst() && it.getInt(0) == 1 } == true
                    }
                    compose.waitUntil(timeoutMillis = 1000) { compose.onAllNodesWithText("Cancel").fetchSemanticsNodes().isNotEmpty() }
                    compose.onNodeWithText("Cancel").performClick()
                    // Wait beyond the provider timeout so a late uncaught opening exception cannot pass unnoticed.
                    instrumentation.waitForIdleSync()
                    Thread.sleep(5500)
                    compose.onNodeWithTag("import_file").assertIsEnabled()
                    compose.onNodeWithTag("import_error").assertDoesNotExist()
                } finally {
                    scenario.onActivity { AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList()) }
                    compose.waitForIdle()
                }
            }
        } finally { Intents.release() }
    }
}
