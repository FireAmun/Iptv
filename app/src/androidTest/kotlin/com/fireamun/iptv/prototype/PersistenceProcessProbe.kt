package com.fireamun.iptv.prototype

import android.app.Activity
import android.os.Bundle
import android.os.Process
import androidx.test.runner.AndroidJUnitRunner
import com.fireamun.iptv.core.data.*
import com.fireamun.iptv.core.model.*
import kotlinx.coroutines.runBlocking

/** Test-APK-only probe, invoked in separate target processes by the disposable CI device. */
internal fun AndroidJUnitRunner.persistenceProcessProbe(mode: String) {
    try {
        val store = (targetContext.applicationContext as IptvApplication).libraryStore
        val source = SourceSpec.xtream("Process fixture", XtreamConnection.create("https://example.invalid", "process-fixture-user", "process-fixture-password")!!)
        runBlocking {
            when (mode) {
                "seed" -> {
                    store.clear()
                    store.save(source, listOf(
                        PlaylistEntry(Channel(0, "Process live", null, ContentKind.LIVE), MediaAddress.parse("https://example.invalid/live/fixture/clip.ts"), providerKey = "LIVE:1"),
                        PlaylistEntry(Channel(1, "Process series", null, ContentKind.SERIES), null, 2, "SERIES:2"),
                    ))
                }
                "verify" -> {
                    val saved = store.restore() ?: error("Missing fixture")
                    check(saved.source.encode().contentEquals(source.encode()))
                    check(saved.entries.map { it.channel.title } == listOf("Process live", "Process series"))
                    check(saved.entries.first().address!!.reveal() == "https://example.invalid/live/fixture/clip.ts")
                    check(saved.entries.last().seriesId == 2L && saved.savedAtMillis > 0)
                }
                "clear" -> { store.clear(); check(store.restore() == null) }
                else -> error("Unknown probe")
            }
        }
        finish(Activity.RESULT_OK, Bundle().apply { putString("stream", "IPTV_PERSISTENCE_PROBE_OK pid=${Process.myPid()}") })
    } catch (_: Exception) {
        finish(Activity.RESULT_CANCELED, Bundle().apply { putString("stream", "IPTV_PERSISTENCE_PROBE_FAILED") })
    }
}
