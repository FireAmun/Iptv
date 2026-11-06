package com.fireamun.iptv.prototype

import android.app.Activity
import android.database.sqlite.SQLiteDatabase
import android.os.Bundle
import android.os.Debug
import android.os.Process
import android.os.SystemClock
import androidx.test.runner.AndroidJUnitRunner
import com.fireamun.iptv.core.data.*
import com.fireamun.iptv.core.model.*
import com.fireamun.iptv.core.storage.KeystoreEnvelope
import com.fireamun.iptv.core.storage.RoomLibraryStore
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockResponseBody
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okio.BufferedSink
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.io.FilterInputStream
import java.io.IOException
import java.net.InetAddress
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicIntegerArray
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLongArray

/** Manual test-APK measurement, deliberately outside JUnit and its timeouts.
 * The host must enforce a process watchdog and require a complete event and RESULT_OK.
 * Benchmark modes never touch IptvApplication.libraryStore or its default key/database.
 * Explicit ui_seed replaces the application's library with synthetic data for hands-on review;
 * its host must enforce the disposable review-AVD whitelist before launching instrumentation.
 */
internal fun AndroidJUnitRunner.catalogueCapacityProbe(mode: String, rawSize: String?) {
    val size = rawSize?.toIntOrNull() ?: 0
    val safeMode = mode.takeIf { it in setOf("parse", "import", "refresh", "open", "clear", "cancel", "xtream", "ui_seed") } ?: "invalid"
    val probe = CapacityProbe(this, safeMode, size)
    // Release a small reserve before formatting the fixed OOM result.
    var emergencyReserve: ByteArray? = ByteArray(64 * 1024)
    try {
        check(safeMode != "invalid" && size in setOf(100_000, 200_000, 300_000))
        runBlocking { probe.run() }
        probe.finish(true)
    } catch (_: OutOfMemoryError) {
        emergencyReserve = null
        probe.finish(false, "OOM")
    } catch (failure: ImportFailure) {
        probe.finish(false, failure.code.name)
    } catch (failure: Exception) {
        val type = when (failure) {
            is FileNotFoundException -> "FILE_OPEN"
            is IOException -> "FILE_IO"
            is SecurityException -> "SECURITY"
            is CancellationException -> "UNEXPECTED_CANCELLATION"
            is IllegalStateException -> "ASSERTION_OR_STATE"
            is IllegalArgumentException -> "ARGUMENT"
            is InterruptedException -> "INTERRUPTED"
            else -> "RUNTIME_OTHER"
        }
        probe.finish(false, "ASSERTION_OR_RUNTIME", type)
    } finally {
        emergencyReserve?.fill(0)
    }
}

private const val CAPACITY_DB = "capacity-probe.db"
private const val CAPACITY_ALIAS = "iptv.capacity.probe.v1"
private const val PAGE_SIZE = 60
private val MOVIE_PROGRESS = WatchProgress(12_000, 60_000, false, 100)
private val EPISODE_PROGRESS = WatchProgress(15_000, 60_000, false, 200)

private class CapacityProbe(private val runner: AndroidJUnitRunner, private val mode: String, private val size: Int) {
    // Instrumentation runs as the target UID; use a dedicated fixture directory in its sandbox.
    private val fixtureDirectory by lazy { runner.targetContext.getDir("capacity-probe", android.content.Context.MODE_PRIVATE) }
    private val fixture get() = File(fixtureDirectory, "capacity-probe.m3u")
    private val metadata get() = File(fixtureDirectory, "capacity-probe.json")
    private val xtreamActions = listOf("auth", "get_live_categories", "get_live_streams", "get_vod_categories", "get_vod_streams", "get_series_categories", "get_series")
    private fun xtreamFile(index: Int) = File(fixtureDirectory, "capacity-xtream-$index.json")

    private fun event(type: String, extras: JSONObject = JSONObject()): String {
        extras.put("event", type).put("mode", mode).put("size", size).put("pid", Process.myPid())
        return "IPTV_CAPACITY $extras\n"
    }
    private fun emit(type: String, extras: JSONObject = JSONObject()) {
        runner.sendStatus(0, Bundle().apply { putString("stream", event(type, extras)) })
    }
    fun finish(success: Boolean, code: String? = null, failureType: String? = null) {
        val extras = JSONObject().put("success", success)
        if (code != null) extras.put("code", code)
        if (failureType != null) extras.put("failureType", failureType)
        val line = event(if (success) "complete" else "failed", extras)
        runner.sendStatus(0, Bundle().apply { putString("stream", line) })
        runner.finish(if (success) Activity.RESULT_OK else Activity.RESULT_CANCELED, Bundle().apply { putString("stream", line) })
    }
    private suspend fun <T> phase(name: String, action: suspend () -> T): T {
        emit("phase_start", JSONObject().put("phase", name))
        val sampler = HeapSampler()
        sampler.start()
        val start = SystemClock.elapsedRealtimeNanos()
        var success = false
        try { return action().also { success = true } }
        finally {
            val elapsedMs = (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000.0
            sampler.stop()
            emit("phase_end", JSONObject().put("phase", name).put("success", success)
                .put("elapsedMs", elapsedMs)
                .put("peakJavaHeapBytes", sampler.javaPeak).put("peakNativeHeapBytes", sampler.nativePeak)
                .put("maxJavaHeapBytes", Runtime.getRuntime().maxMemory()).put("samples", sampler.samples))
        }
    }

    suspend fun run() {
        emit("start", JSONObject().put("storageEntryCap", MAX_CATALOGUE_ENTRIES)
            .put("saveContract", "session")
            .put("parserEntryCap", size).put("parserByteCap", ParserLimits().maxBytes)
            .put("parserMillisCap", ParserLimits().maxMillis).put("parserLineCharCap", ParserLimits().maxLineChars)
            .put("parserFieldCharCap", ParserLimits().maxFieldChars).put("categoryCount", 100)
            .put("artworkCount", if (mode == "xtream") size else 0).put("syntheticMovieCount", if (mode in setOf("import", "refresh", "open", "ui_seed")) 1 else 0)
            .put("syntheticSeriesCount", if (mode in setOf("import", "refresh", "open", "ui_seed")) 1 else 0)
            .put("writesApplicationLibrary", mode == "ui_seed")
            .put("sampleIntervalMs", 100))
        if (mode == "clear") {
            phase("clear") {
                check(runner.targetContext.deleteDatabase(CAPACITY_DB) || !runner.targetContext.getDatabasePath(CAPACITY_DB).exists())
                KeystoreEnvelope(CAPACITY_ALIAS).clear()
                check(!fixture.exists() || fixture.delete())
                check(!metadata.exists() || metadata.delete())
                xtreamActions.indices.forEach { check(!xtreamFile(it).exists() || xtreamFile(it).delete()) }
            }
            return
        }
        if (mode == "xtream") { xtream(); return }
        if (mode in setOf("parse", "import", "refresh", "cancel", "ui_seed")) {
            phase("fixture_generate") { generateFixture() }
            emit("fixture", JSONObject().put("fixtureBytes", fixture.length()))
        }
        if (mode == "ui_seed") { uiSeed(); return }
        if (mode == "cancel") { cancel(); return }
        if (mode == "parse") {
            val entries = phase("parse") { parseFixture() }
            phase("parse_assert") {
                check(entries.size == size && entries.first().channel.title == title(0) && entries.last().channel.title == title(size - 1))
                check(entries.all { it.channel.kind == ContentKind.UNKNOWN && it.artwork == null })
            }
            return
        }
        val store = phase("store_open") { RoomLibraryStore.open(runner.targetContext, CAPACITY_DB, KeystoreEnvelope(CAPACITY_ALIAS)) }
        try {
            when (mode) {
                "import" -> import(store)
                "refresh" -> refresh(store)
                "open" -> open(store)
            }
        } finally { store.close() }
        phase("database_metrics") { databaseMetrics() }
    }

    /** Explicitly destructive to the disposable review app's synthetic library. This is not an
     * isolated benchmark mode; the host's strict AVD whitelist is mandatory before invocation. */
    private suspend fun uiSeed() {
        val entries = classify(phase("parse") { parseFixture() })
        val store = (runner.targetContext.applicationContext as IptvApplication).libraryStore
        phase("ui_seed_clear") { store.clear() }
        val saved = phase("ui_seed_save") { store.saveSession(SourceSpec.file(), entries) }
        phase("ui_seed_assert") {
            val session = store.openSession()!!
            check(session.count == size && session.initialEntries.isEmpty() && session.source.kind == SourceKind.FILE)
            assertCommittedSession(saved, session)
        }
    }

    private fun databaseMetrics() {
        val dbFile = runner.targetContext.getDatabasePath(CAPACITY_DB)
        val metrics = JSONObject()
        SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS).use { db ->
            for ((table, field) in listOf("library_entries" to "entryRows", "entry_search" to "searchRows", "item_identities" to "identityRows")) {
                val count = db.rawQuery("SELECT COUNT(*) FROM $table", null).use { cursor -> check(cursor.moveToFirst()); cursor.getLong(0) }
                metrics.put(field, count)
                check(count == size.toLong())
            }
        }
        metrics.put("dbBytes", dbFile.length()).put("walBytes", File(dbFile.path + "-wal").length())
            .put("shmBytes", File(dbFile.path + "-shm").length())
        emit("database_metrics", metrics)
    }

    private suspend fun cancel() {
        var bytesRead = 0L
        var closed = false
        var propagated = false
        var workMs = 0.0
        phase("cancel_parse") {
            val start = SystemClock.elapsedRealtimeNanos()
            try {
                object : FilterInputStream(fixture.inputStream()) {
                    override fun read(): Int = super.read().also { if (it >= 0) bytesRead++ }
                    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = `in`.read(buffer, offset, length).also { if (it > 0) bytesRead += it }
                    override fun close() { try { super.close() } finally { closed = true } }
                }.use { input ->
                    M3uParser(ParserLimits(maxEntries = size)).parse(input) {
                        if (bytesRead >= 64 * 1024) throw CancellationException()
                    }
                }
            } catch (_: CancellationException) { propagated = true }
            finally { workMs = (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000.0 }
        }
        phase("cancel_assert") {
            check(propagated && closed && bytesRead in 65_536..(fixture.length() - 1))
            // Decoder read-ahead is bounded. This checks parser/input cancellation only;
            // the ViewModel's noncancellable saving boundary is a separate test.
            check(bytesRead <= 128 * 1024)
            emit("cancel_result", JSONObject().put("bytesRead", bytesRead).put("fixtureBytes", fixture.length())
                .put("cancelWorkMs", workMs).put("cancelPropagated", propagated).put("inputClosed", closed)
                .put("remainderUnconsumed", bytesRead < fixture.length()))
        }
    }

    private fun kindCount(index: Int) = if (index == 2) size - 2 * (size / 3) else size / 3
    private fun generateXtreamFixtures() {
        xtreamFile(0).writeText("""{"user_info":{"auth":1,"status":"Active"}}""")
        for (kindIndex in 0..2) {
            val categoryIndex = 1 + 2 * kindIndex
            xtreamFile(categoryIndex).bufferedWriter().use { out ->
                out.append('[')
                repeat(100) { index ->
                    if (index != 0) out.append(',')
                    out.append("{\"category_id\":\"$index\",\"category_name\":\"Group $index\"}")
                }
                out.append(']')
            }
            xtreamFile(categoryIndex + 1).bufferedWriter().use { out ->
                out.append('[')
                repeat(kindCount(kindIndex)) { index ->
                    if (index != 0) out.append(',')
                    val idField = if (kindIndex == 2) "series_id" else "stream_id"
                    val artworkField = if (kindIndex == 2) "cover" else "stream_icon"
                    out.append("{\"$idField\":${index + 1},\"name\":\"${title(index)}\",\"category_id\":\"${index % 100}\",\"container_extension\":\"mp4\",\"$artworkField\":\"${xtreamArtwork(kindIndex, index)}\",\"year\":\"${2000 + index % 25}\",\"rating\":\"8.3\"}")
                }
                out.append(']')
            }
        }
        check(xtreamActions.indices.all { xtreamFile(it).length() in 1..ParserLimits().maxBytes })
    }

    private suspend fun xtream() {
        phase("xtream_fixture_generate") { generateXtreamFixtures() }
        emit("fixture", JSONObject().put("fixtureBytes", xtreamActions.indices.sumOf { xtreamFile(it).length() }))
        val requests = AtomicIntegerArray(xtreamActions.size)
        val bodyBytes = AtomicLongArray(xtreamActions.size)
        val bodyComplete = AtomicIntegerArray(xtreamActions.size)
        val bodyFailure = AtomicIntegerArray(xtreamActions.size)
        val currentAction = AtomicInteger(-1)
        val invalidRequest = AtomicBoolean(false)
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val action = request.url.queryParameter("action") ?: "auth"
                    val index = xtreamActions.indexOf(action)
                    if (index < 0 || request.url.encodedPath != "/player_api.php" || request.method != "GET" ||
                        request.url.queryParameter("username") != "capacity-fixture" || request.url.queryParameter("password") != "synthetic-only") {
                        invalidRequest.set(true)
                        return MockResponse(code = 404)
                    }
                    requests.incrementAndGet(index)
                    currentAction.set(index)
                    val file = xtreamFile(index)
                    return MockResponse.Builder().addHeader("Content-Type", "application/json").body(object : MockResponseBody {
                        override val contentLength: Long get() = file.length()
                        override fun writeTo(sink: BufferedSink) {
                            try {
                                file.inputStream().use { input ->
                                    val buffer = ByteArray(8192)
                                    while (true) {
                                        val count = input.read(buffer)
                                        if (count == -1) break
                                        sink.write(buffer, 0, count)
                                        bodyBytes.addAndGet(index, count.toLong())
                                    }
                                }
                                // 5.3.2's HTTP/1 writer buffers over the socket's BufferedSink,
                                // then calls emit(), which can leave the final <8KiB downstream.
                                // Flush both buffering layers, especially the small auth body.
                                sink.flush()
                                bodyComplete.incrementAndGet(index)
                            } catch (failure: IOException) {
                                bodyFailure.set(index, if (failure is FileNotFoundException) 2 else 1)
                                throw failure
                            } catch (failure: SecurityException) {
                                bodyFailure.set(index, 3); throw failure
                            } catch (failure: OutOfMemoryError) {
                                bodyFailure.set(index, 4); throw failure
                            } catch (failure: RuntimeException) {
                                bodyFailure.set(index, 5); throw failure
                            }
                        }
                    }).build()
                }
            }
            server.start(InetAddress.getByName("127.0.0.1"), 0)
            // Match the literal address bound above; server.url() otherwise uses reverse hostName.
            val connection = XtreamConnection.create(server.url("/").newBuilder().host("127.0.0.1").build().toString(), "capacity-fixture", "synthetic-only")!!
            val entries = try {
                phase("xtream_load") { XtreamLoader(ParserLimits(maxEntries = size)).load(connection) }
            } finally {
                // Fixed slots follow xtreamActions order. Count bytes accepted by the body sink,
                // not wire bytes. Failure codes:0 none,1 IO,2 file open,3 security,4 OOM,5 runtime.
                emit("xtream_transport", JSONObject().put("currentActionIndex", currentAction.get())
                    .put("serverRequestCount", server.requestCount).put("invalidRequest", invalidRequest.get())
                    .put("requestCounts", JSONArray(xtreamActions.indices.map { requests.get(it) }))
                    .put("bodyWriteBytes", JSONArray(xtreamActions.indices.map { bodyBytes.get(it) }))
                    .put("bodyCompleteCounts", JSONArray(xtreamActions.indices.map { bodyComplete.get(it) }))
                    .put("bodyFailureCodes", JSONArray(xtreamActions.indices.map { bodyFailure.get(it) })))
            }
            phase("xtream_assert") {
                check(!invalidRequest.get() && xtreamActions.indices.all { requests.get(it) == 1 })
                check(xtreamActions.indices.all { bodyComplete.get(it) == 1 && bodyBytes.get(it) == xtreamFile(it).length() && bodyFailure.get(it) == 0 })
                check(entries.size == size && entries.first().channel.id == 0 && entries.last().channel.id == size - 1)
                var offset = 0
                for ((index, kind) in listOf(ContentKind.LIVE, ContentKind.MOVIE, ContentKind.SERIES).withIndex()) {
                    repeat(kindCount(index)) { position ->
                        val entry = entries[offset + position]
                        check(entry.channel.kind == kind && entry.channel.title == title(position) && entry.channel.group == "Group ${position % 100}")
                        check(entry.artwork?.reveal() == xtreamArtwork(index, position) && entry.channel.hasArtwork)
                        check(entry.channel.year == 2000 + position % 25 && entry.channel.rating == 8.3 && entry.channel.categoryKey == (position % 100).toString())
                        check(entry.providerKey == "${kind.name}:${position + 1}")
                        check(if (kind == ContentKind.SERIES) entry.seriesId == position + 1L && entry.address == null else entry.address != null)
                    }
                    offset += kindCount(index)
                }
                emit("xtream_result", JSONObject().put("entryCount", entries.size).put("liveCount", kindCount(0))
                    .put("movieCount", kindCount(1)).put("seriesCount", kindCount(2)).put("authRequests", requests.get(0))
                    .put("catalogueRequests", 3).put("categoryRequests", 3).put("artworkCount", entries.count { it.artwork != null }))
            }
            val store = phase("store_open") { RoomLibraryStore.open(runner.targetContext, CAPACITY_DB, KeystoreEnvelope(CAPACITY_ALIAS)) }
            try {
                phase("empty_assert") { check(store.openSession() == null && !metadata.exists()) }
                val source = SourceSpec.xtream("Synthetic capacity fixture", connection)
                val meta = seedXtream(store, source, entries)
                refreshXtream(store, source, entries, meta)
            } finally { store.close() }
            phase("database_metrics") { databaseMetrics() }
            check(!invalidRequest.get() && xtreamActions.indices.all { requests.get(it) == 1 })
        }
    }

    private fun xtreamArtwork(kindIndex: Int, index: Int) = "https://images.example.invalid/capacity/$kindIndex/${index + 1}.jpg"

    /** Retain the real loader input while saves return only committed session metadata. */
    private suspend fun seedXtream(store: RoomLibraryStore, source: SourceSpec, entries: List<PlaylistEntry>): JSONObject {
        val saved = phase("xtream_save") { store.saveSession(source, entries) }
        val session = phase("xtream_saved_session") { store.openSession()!! }
        assertCommittedSession(saved, session)
        check(session.source.kind == SourceKind.XTREAM)
        val meta = JSONObject().put("sourceId", saved.sourceId!!).put("generation", session.generation)
        phase("xtream_user_state_seed") {
            meta.put("firstId", idAt(store, session, 0)).put("lastId", idAt(store, session, size - 1))
                .put("movieId", idAt(store, session, kindCount(0)))
                .put("seriesId", idAt(store, session, kindCount(0) + kindCount(1)))
            val sourceId = session.sourceId!!
            store.setFavourite(sourceId, session.generation, meta.getInt("firstId"), true)
            store.save(ProgressTarget(sourceId, meta.getInt("movieId"), ContentKind.MOVIE), MOVIE_PROGRESS)
            store.save(ProgressTarget(sourceId, meta.getInt("seriesId"), ContentKind.EPISODE, "EPISODE:31"), EPISODE_PROGRESS,
                EpisodeSnapshot("EPISODE:31", "Synthetic episode", 1, 2, MediaAddress.parse("https://example.invalid/capacity/episode.mp4")!!))
            assertUserState(store, meta, session)
            assertXtreamStoredMetadata(store, meta, session)
        }
        return meta
    }

    private suspend fun refreshXtream(store: RoomLibraryStore, source: SourceSpec, entries: List<PlaylistEntry>, meta: JSONObject) {
        val saved = phase("xtream_refresh_save") {
            store.saveSession(source, entries.asReversed(), refreshing = true, targetSourceId = meta.getString("sourceId"))
        }
        phase("xtream_refresh_assert") {
            val after = store.openSession()!!
            assertCommittedSession(saved, after)
            check(after.sourceId == meta.getString("sourceId") && after.generation != meta.getString("generation") && after.count == size)
            check(after.initialEntries.isEmpty() && after.source.encode().contentEquals(source.encode()))
            check(idAt(store, after, 0) == meta.getInt("lastId") && idAt(store, after, size - 1) == meta.getInt("firstId"))
            check(idAt(store, after, size - 1 - kindCount(0)) == meta.getInt("movieId"))
            check(idAt(store, after, size - 1 - kindCount(0) - kindCount(1)) == meta.getInt("seriesId"))
            assertUserState(store, meta, after)
            assertXtreamStoredMetadata(store, meta, after)
        }
    }

    private suspend fun assertXtreamStoredMetadata(store: RoomLibraryStore, meta: JSONObject, session: LibrarySession) {
        for ((idKey, kindIndex, position) in listOf(
            Triple("firstId", 0, 0), Triple("movieId", 1, 0), Triple("seriesId", 2, 0), Triple("lastId", 2, kindCount(2) - 1),
        )) {
            val id = meta.getInt(idKey)
            val entry = store.item(session.sourceId!!, session.generation, id)!!
            val kind = listOf(ContentKind.LIVE, ContentKind.MOVIE, ContentKind.SERIES)[kindIndex]
            check(entry.channel.kind == kind && entry.channel.year == 2000 + position % 25 && entry.channel.rating == 8.3)
            check(entry.channel.categoryKey == (position % 100).toString() && entry.channel.hasArtwork)
            check(entry.providerKey == "${kind.name}:${position + 1}")
            check(store.artwork(LibraryArtwork(session.sourceId!!, id, session.generation))?.reveal() == xtreamArtwork(kindIndex, position))
        }
    }

    private fun title(index: Int) = if (index % 2 == 0) "Capacity Channel item$index" else "قناة اختبار item$index"
    private fun generateFixture() {
        fixture.outputStream().bufferedWriter(Charsets.UTF_8).use { out ->
            out.append("#EXTM3U\n")
            repeat(size) { index ->
                out.append("#EXTINF:-1 group-title=\"Group ${index % 100}\",${title(index)}\n")
                out.append("https://example.invalid/capacity/$index.ts\n")
            }
        }
        check(fixture.length() in 1..ParserLimits().maxBytes)
    }
    private fun parseFixture(): List<PlaylistEntry> = fixture.inputStream().use { M3uParser(ParserLimits(maxEntries = size)).parse(it) }.also { check(it.size == size) }

    // M3U has UNKNOWN kinds. Replace only two roots for explicit history preservation checks;
    // retain all parsed locators and disclose these synthetic classifications in the start event.
    private fun classify(entries: List<PlaylistEntry>): List<PlaylistEntry> = object : AbstractList<PlaylistEntry>() {
        override val size: Int get() = entries.size
        override fun get(index: Int): PlaylistEntry {
            val entry = entries[index]
            return when (index) {
                size - 2 -> PlaylistEntry(entry.channel.copy(kind = ContentKind.SERIES), entry.address, seriesId = 7L)
                size - 1 -> PlaylistEntry(entry.channel.copy(kind = ContentKind.MOVIE), entry.address)
                else -> entry
            }
        }
    }
    private suspend fun import(store: RoomLibraryStore) {
        // A fresh import is intentional; require explicit clear rather than replacing fixture state.
        phase("empty_assert") { check(store.openSession() == null && !metadata.exists()) }
        val entries = classify(phase("parse") { parseFixture() })
        val saved = phase("save") { store.saveSession(SourceSpec.file(), entries) }
        val session = phase("saved_session") { store.openSession()!! }
        assertCommittedSession(saved, session)
        val meta = JSONObject().put("size", size).put("sourceId", saved.sourceId!!).put("generation", session.generation)
            .put("reversed", false).put("writerPid", Process.myPid())
        phase("user_state_seed") {
            meta.put("firstId", idAt(store, session, 0)).put("lastId", idAt(store, session, size - 1))
                .put("seriesId", idAt(store, session, size - 2))
            val source = saved.sourceId!!
            store.setFavourite(source, session.generation, meta.getInt("firstId"), true)
            store.save(ProgressTarget(source, meta.getInt("lastId"), ContentKind.MOVIE), MOVIE_PROGRESS)
            store.save(ProgressTarget(source, meta.getInt("seriesId"), ContentKind.EPISODE, "EPISODE:31"), EPISODE_PROGRESS,
                EpisodeSnapshot("EPISODE:31", "Synthetic episode", 1, 2, MediaAddress.parse("https://example.invalid/capacity/episode.mp4")!!))
            assertUserState(store, meta, session)
            writeMetadata(meta)
        }
    }
    private fun readMetadata(): JSONObject = JSONObject(metadata.readText()).also { check(it.getInt("size") == size) }
    private fun writeMetadata(meta: JSONObject) { metadata.writeText(meta.toString()) }
    private suspend fun refresh(store: RoomLibraryStore) {
        val meta = readMetadata()
        val before = phase("before_refresh") { store.openSession()!! }
        check(before.sourceId == meta.getString("sourceId") && before.generation == meta.getString("generation") && before.count == size)
        val original = classify(phase("parse") { parseFixture() })
        val reversed = !meta.getBoolean("reversed")
        val ordered = if (reversed) original.asReversed() else original
        val saved = phase("refresh_save") { store.saveSession(SourceSpec.file(), ordered, refreshing = true, targetSourceId = before.sourceId) }
        phase("refresh_assert") {
            val after = store.openSession()!!
            assertCommittedSession(saved, after)
            check(after.sourceId == before.sourceId && after.generation != before.generation && after.count == size)
            check(idAt(store, after, 0) == meta.getInt(if (reversed) "lastId" else "firstId"))
            check(idAt(store, after, size - 1) == meta.getInt(if (reversed) "firstId" else "lastId"))
            check(idAt(store, after, if (reversed) 1 else size - 2) == meta.getInt("seriesId"))
            assertUserState(store, meta, after)
            meta.put("generation", after.generation).put("reversed", reversed).put("writerPid", Process.myPid())
            writeMetadata(meta)
        }
    }
    private fun assertCommittedSession(saved: LibrarySession, opened: LibrarySession) {
        check(saved.initialEntries.isEmpty() && opened.initialEntries.isEmpty() && saved.count == size && opened.count == size)
        check(saved.sourceId != null && saved.sourceId == opened.sourceId && saved.generation == opened.generation)
        check(saved.savedAtMillis > 0 && saved.savedAtMillis == opened.savedAtMillis)
    }
    private suspend fun idAt(store: LibraryPersistence, session: LibrarySession, offset: Int): Int {
        val page = store.page(BrowseRequest(session.sourceId!!, session.generation), offset, 1)
        check(page.total == size && page.items.size == 1)
        return page.items.single().id
    }
    private suspend fun assertUserState(store: RoomLibraryStore, meta: JSONObject, session: LibrarySession) {
        val source = session.sourceId!!
        val favourite = store.page(BrowseRequest(source, session.generation, favouritesOnly = true), 0, PAGE_SIZE)
        check(favourite.total == 1 && favourite.items.single().id == meta.getInt("firstId"))
        check(store.read(ProgressTarget(source, meta.optInt("movieId", meta.getInt("lastId")), ContentKind.MOVIE))!!.progress == MOVIE_PROGRESS)
        val episode = store.read(ProgressTarget(source, meta.getInt("seriesId"), ContentKind.EPISODE, "EPISODE:31"))!!
        check(episode.progress == EPISODE_PROGRESS && episode.snapshot!!.episode == 2 && episode.snapshot!!.season == 1)
        check(episode.snapshot!!.address.reveal() == "https://example.invalid/capacity/episode.mp4")
    }
    private suspend fun open(store: RoomLibraryStore) {
        val meta = readMetadata()
        check(meta.getInt("writerPid") != Process.myPid())
        val session = phase("open_session") { store.openSession()!! }
        check(session.sourceId == meta.getString("sourceId") && session.generation == meta.getString("generation"))
        check(session.initialEntries.isEmpty() && session.count == size)
        val request = BrowseRequest(session.sourceId!!, session.generation)
        val reversed = meta.getBoolean("reversed")
        phase("first_page") {
            val page = store.page(request, 0, PAGE_SIZE)
            check(page.total == size && page.items.size == PAGE_SIZE)
            check(page.items.first().id == meta.getInt(if (reversed) "lastId" else "firstId"))
        }
        phase("deep_page") {
            val page = store.page(request, size - PAGE_SIZE, PAGE_SIZE)
            check(page.total == size && page.items.size == PAGE_SIZE)
            check(page.items.last().id == meta.getInt(if (reversed) "firstId" else "lastId"))
        }
        phase("categories") { check(store.categories(request).size == 100) }
        phase("latin_selective_search") { check(store.page(request.copy(query = "Channel item0"), 0, PAGE_SIZE).total == 1) }
        phase("arabic_selective_search") { check(store.page(request.copy(query = "قناة item${size - 1}"), 0, PAGE_SIZE).total == 1) }
        phase("latin_broad_search") {
            val page = store.page(request.copy(query = "Channel"), 0, PAGE_SIZE)
            check(page.total == size / 2 && page.items.size == PAGE_SIZE)
        }
        phase("arabic_broad_search") {
            val page = store.page(request.copy(query = "قناة"), 0, PAGE_SIZE)
            check(page.total == size / 2 && page.items.size == PAGE_SIZE)
        }
        phase("favourites_and_history") { assertUserState(store, meta, session) }
    }
}

/** These are independently sampled heap peaks, not whole-process peaks or peak PSS. */
private class HeapSampler {
    @Volatile private var stopping = false
    var javaPeak = 0L; private set
    var nativePeak = 0L; private set
    var samples = 0L; private set
    private val worker = Thread({
        while (!stopping) {
            sample()
            try { Thread.sleep(100) } catch (_: InterruptedException) { break }
        }
    }, "capacity-heap-sampler").apply { isDaemon = true }
    @Synchronized private fun sample() {
        val runtime = Runtime.getRuntime()
        javaPeak = maxOf(javaPeak, runtime.totalMemory() - runtime.freeMemory())
        nativePeak = maxOf(nativePeak, Debug.getNativeHeapAllocatedSize())
        samples++
    }
    fun start() { sample(); worker.start() }
    fun stop() { stopping = true; worker.interrupt(); worker.join(); sample() }
}
