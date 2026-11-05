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
                        PlaylistEntry(Channel(2, "Process movie", null, ContentKind.MOVIE), MediaAddress.parse("https://example.invalid/movie/fixture.mp4"), providerKey = "MOVIE:3"),
                    ))
                    val second = store.save(SourceSpec.file(), listOf(PlaylistEntry(Channel(0, "Process secondary", null), MediaAddress.parse("https://example.invalid/secondary"))))
                    store.rename(second.sourceId!!, "Process alternate")
                    val session = store.activateSession(first.sourceId!!)
                    store.setFavourite(session.sourceId!!, session.generation, first.entries.first().channel.id, true)
                    store.save(ProgressTarget(session.sourceId!!, first.entries[2].channel.id, ContentKind.MOVIE), WatchProgress(12_000, 30_000, false, 100))
                    store.save(ProgressTarget(session.sourceId!!, first.entries[1].channel.id, ContentKind.EPISODE, "EPISODE:31"), WatchProgress(15_000, 30_000, false, 200),
                        EpisodeSnapshot("EPISODE:31", "Process episode", 1, 2, MediaAddress.parse("https://example.invalid/series/fixture.mp4")!!))
                    check(store.recent(session.sourceId!!, ContentKind.MOVIE).single().progress.positionMs == 12_000L)
                    check(store.recent(session.sourceId!!, ContentKind.EPISODE).single().progress.positionMs == 15_000L)
                }
                "verify" -> {
                    val saved = store.restore() ?: error("Missing fixture")
                    check(saved.source.encode().contentEquals(source.encode()))
                    check(saved.entries.map { it.channel.title } == listOf("Process live", "Process series", "Process movie"))
                    check(saved.entries.first().address!!.reveal() == "https://example.invalid/live/fixture/clip.ts")
                    check(saved.entries[1].seriesId == 2L && saved.savedAtMillis > 0)
                    val directory = store.directory()
                    check(directory.sources.size == 2 && directory.activeSourceId == saved.sourceId)
                    val header = store.openSession()!!
                    check(header.initialEntries.isEmpty() && header.count == 3)
                    check(store.page(BrowseRequest(header.sourceId!!, header.generation, query = "pro", favouritesOnly = true), 0, 60).items.single().id == saved.entries.first().channel.id)
                    val second = directory.sources.single { it.id != saved.sourceId }
                    check(second.name == "Process alternate")
                    check(store.activate(second.id).entries.single().channel.title == "Process secondary")
                    store.activate(saved.sourceId!!)
                    val movie = store.recent(saved.sourceId!!, ContentKind.MOVIE).single()
                    val episode = store.recent(saved.sourceId!!, ContentKind.EPISODE).single()
                    check(movie.rootItemId == saved.entries[2].channel.id && movie.progress.positionMs == 12_000L)
                    check(episode.rootItemId == saved.entries[1].channel.id && episode.progress.positionMs == 15_000L)
                    check(store.read(movie.reference)!!.progress.durationMs == 30_000L)
                    val restoredEpisode = store.read(episode.reference)!!
                    check(restoredEpisode.target.episodeProviderKey == "EPISODE:31" && restoredEpisode.snapshot!!.episode == 2)
                    check(restoredEpisode.snapshot!!.address.reveal() == "https://example.invalid/series/fixture.mp4")
                    check(store.recent(second.id, ContentKind.MOVIE).isEmpty() && store.recent(second.id, ContentKind.EPISODE).isEmpty())
                }
                "clear" -> {
                    val sourceIds = store.directory().sources.map { it.id }
                    store.clear(); check(store.restore() == null)
                    for (id in sourceIds) check(store.recent(id, ContentKind.MOVIE).isEmpty() && store.recent(id, ContentKind.EPISODE).isEmpty())
                }
                else -> error("Unknown probe")
            }
        }
        finish(Activity.RESULT_OK, Bundle().apply { putString("stream", "IPTV_PERSISTENCE_PROBE_OK pid=${Process.myPid()}\nIPTV_PROGRESS_PROBE_OK stage=$mode") })
    } catch (_: Exception) {
        finish(Activity.RESULT_CANCELED, Bundle().apply { putString("stream", "IPTV_PERSISTENCE_PROBE_FAILED") })
    }
}
