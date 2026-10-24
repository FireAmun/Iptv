package com.fireamun.iptv.prototype

import com.fireamun.iptv.core.data.M3uParser
import com.fireamun.iptv.core.model.ImportError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {
    @Before fun mainDispatcher() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun resetDispatcher() { Dispatchers.resetMain() }
    private fun playlist(title: String, scheme: String = "https") = "#EXTM3U\n#EXTINF:-1,$title\n$scheme://example.invalid/live\n"

    @Test fun failedReplacementPreservesLibraryAndSuccessfulReplacementIsAtomic() = runTest {
        val model = LibraryViewModel(ioDispatcher = StandardTestDispatcher(testScheduler))
        model.importFile { playlist("First").byteInputStream() }
        assertTrue(model.state.value.importing)
        advanceUntilIdle()
        assertEquals("First", model.state.value.channels.single().title)
        model.importFile { "<html>private-token</html>".byteInputStream() }
        advanceUntilIdle()
        assertEquals("First", model.state.value.channels.single().title)
        assertEquals(ImportError.INVALID_PLAYLIST, model.state.value.error)
        model.importFile { playlist("Second").byteInputStream() }
        advanceUntilIdle()
        assertEquals(listOf("Second"), model.state.value.channels.map { it.title })
        assertNull(model.state.value.error)
    }
    @Test fun httpImportAndPlaybackRequireSeparateApproval() = runTest {
        val model = LibraryViewModel(loadUrl = { M3uParser().parse(playlist("HTTP item", "http").byteInputStream()) }, ioDispatcher = StandardTestDispatcher(testScheduler))
        model.importUrl("http://example.invalid/source?token=private")
        advanceUntilIdle()
        assertTrue(model.state.value.needsHttpApproval)
        assertTrue(model.state.value.channels.isEmpty())
        model.approveHttp()
        advanceUntilIdle()
        assertEquals("HTTP item", model.state.value.channels.single().title)
        model.selectChannel(0)
        assertNull(model.playback.value)
        assertTrue(model.state.value.needsHttpApproval)
        model.dismissHttp()
        assertNull(model.playback.value)
        model.selectChannel(0)
        model.approveHttp()
        assertNotNull(model.playback.value)
        model.closePlayback()
        assertNull(model.playback.value)
        assertEquals(0, model.state.value.selectedId)
        assertEquals(1, model.state.value.channels.size)
    }
    @Test fun unsupportedSourcesAndReadErrorsHaveSafeCodes() = runTest {
        val model = LibraryViewModel(ioDispatcher = StandardTestDispatcher(testScheduler))
        model.importUrl("file:///private-token")
        assertEquals(ImportError.INVALID_ADDRESS, model.state.value.error)
        model.importFile { throw SecurityException("private-token") }
        advanceUntilIdle()
        assertEquals(ImportError.READ_FAILED, model.state.value.error)
        assertFalse(model.state.value.toString().contains("private-token"))
    }
    @Test fun cancellingAnOpenedFileClosesReadsThatIgnoreThreadInterruption() = runTest {
        val entered = CountDownLatch(1)
        val closed = CountDownLatch(1)
        val stream = object : InputStream() {
            override fun read(): Int {
                entered.countDown()
                while (closed.count > 0) {
                    try { closed.await(20, TimeUnit.MILLISECONDS) } catch (_: InterruptedException) { }
                }
                return -1
            }
            override fun close() { closed.countDown() }
        }
        val model = LibraryViewModel()
        try {
            model.importFile { stream }
            runCurrent()
            assertTrue(withContext(Dispatchers.IO) { entered.await(2, TimeUnit.SECONDS) })
            model.cancelImport()
            assertTrue("Cancelled read must be closed promptly", withContext(Dispatchers.IO) { closed.await(500, TimeUnit.MILLISECONDS) })
        } finally { stream.close() }
    }
}
