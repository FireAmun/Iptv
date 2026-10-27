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
                    val first = store.save(source, listOf(
                        PlaylistEntry(Channel(0, "Process live", null, ContentKind.LIVE), MediaAddress.parse("https://example.invalid/live/fixture/clip.ts"), providerKey = "LIVE:1"),
                        PlaylistEntry(Channel(1, "Process series", null, ContentKind.SERIES), null, 2, "SERIES:2"),
                    ))
                    val second = store.save(SourceSpec.file(), listOf(PlaylistEntry(Channel(0, "Process secondary", null), MediaAddress.parse("https://example.invalid/secondary"))))
                    store.rename(second.sourceId!!, "Process alternate")
                    val session = store.activateSession(first.sourceId!!)
                    store.setFavourite(session.sourceId!!, session.generation, first.entries.first().channel.id, true)
                }
                "verify" -> {
                    val saved = store.restore() ?: error("Missing fixture")
                    check(saved.source.encode().contentEquals(source.encode()))
                    check(saved.entries.map { it.channel.title } == listOf("Process live", "Process series"))
                    check(saved.entries.first().address!!.reveal() == "https://example.invalid/live/fixture/clip.ts")
                    check(saved.entries.last().seriesId == 2L && saved.savedAtMillis > 0)
                    val directory = store.directory()
                    check(directory.sources.size == 2 && directory.activeSourceId == saved.sourceId)
                    val header = store.openSession()!!
                    check(header.initialEntries.isEmpty() && header.count == 2)
                    check(store.page(BrowseRequest(header.sourceId!!, header.generation, query = "pro", favouritesOnly = true), 0, 60).items.single().id == saved.entries.first().channel.id)
                    val second = directory.sources.single { it.id != saved.sourceId }
                    check(second.name == "Process alternate")
                    check(store.activate(second.id).entries.single().channel.title == "Process secondary")
                    store.activate(saved.sourceId!!)
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
