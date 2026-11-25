package com.fireamun.iptv.prototype

import com.fireamun.iptv.core.data.PinThrottle
import com.fireamun.iptv.core.data.PinVerifier
import com.fireamun.iptv.core.model.CategoryLocks
import com.fireamun.iptv.core.model.ContentKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ParentalControlsTest {
    private class MemoryStore : ParentalStore {
        var verifier: PinVerifier? = null
        var throttle = PinThrottle()
        val locks = mutableMapOf<String, CategoryLocks>()
        override fun verifier() = verifier
        override fun saveVerifier(value: PinVerifier?) { verifier = value }
        override fun throttle() = throttle
        override fun saveThrottle(value: PinThrottle) { throttle = value }
        override fun locks(sourceId: String) = locks[sourceId] ?: CategoryLocks()
        override fun saveLocks(sourceId: String, value: CategoryLocks) { locks[sourceId] = value }
        override fun clearLocks(sourceId: String?) { if (sourceId == null) locks.clear() else locks.remove(sourceId) }
    }
    private var now = 1_000_000L
    private val store = MemoryStore()
    private fun controls() = ParentalControls(store, { now }, Dispatchers.Unconfined)

    @Test fun lockedCategoriesHideOnlyWhileRestricted() = runBlocking {
        val parental = controls()
        assertFalse(parental.state.value.pinSet)
        parental.setPin("1234".toCharArray())
        parental.setLock("a", ContentKind.MOVIE, "Horror", true)
        assertTrue(parental.state.value.unlocked)
        assertTrue(parental.hidden("a").isEmpty)
        parental.relock()
        assertEquals(setOf("Horror"), parental.hidden("a").labels(ContentKind.MOVIE))
        assertTrue(parental.blocks("a", ContentKind.MOVIE, "Horror"))
        assertFalse(parental.blocks("b", ContentKind.MOVIE, "Horror"))
        assertThrows(IllegalStateException::class.java) { parental.setLock("a", ContentKind.LIVE, "News", true) }
        assertThrows(IllegalStateException::class.java) { parental.disable() }
        assertEquals(PinResult.Accepted, parental.unlock("1234".toCharArray()))
        assertTrue(parental.hidden("a").isEmpty)
    }

    @Test fun wrongPinsAreThrottledAndAnAcceptedPinResetsTheCount() = runBlocking {
        val parental = controls()
        parental.setPin("1234".toCharArray()); parental.relock()
        repeat(4) { assertEquals(PinResult.Rejected(0), parental.unlock("0000".toCharArray())) }
        assertEquals(PinResult.Rejected(30_000), parental.unlock("0000".toCharArray()))
        assertEquals("Even the right PIN waits", PinResult.Wait(30_000), parental.unlock("1234".toCharArray()))
        now += 30_000
        assertEquals(PinResult.Accepted, parental.unlock("1234".toCharArray()))
        assertEquals(PinThrottle(), store.throttle)
    }

    @Test fun backgroundRelocksAfterFiveMinutesOrAClockJumpBack() = runBlocking {
        val parental = controls()
        parental.setPin("1234".toCharArray())
        parental.onBackground(); now += ParentalControls.RELOCK_AFTER_MS - 1; parental.onForeground()
        assertTrue(parental.state.value.unlocked)
        parental.onBackground(); now += ParentalControls.RELOCK_AFTER_MS; parental.onForeground()
        assertFalse(parental.state.value.unlocked)
        parental.unlock("1234".toCharArray())
        parental.onBackground(); now -= 10; parental.onForeground()
        assertFalse(parental.state.value.unlocked)
    }

    @Test fun turningOffAndForgettingDropEveryLock() = runBlocking {
        val parental = controls()
        parental.setPin("1234".toCharArray())
        parental.setLock("a", ContentKind.LIVE, "Adults", true)
        parental.setLock("b", ContentKind.SERIES, "Horror", true)
        parental.sourceRemoved("b")
        assertTrue(store.locks("b").isEmpty)
        parental.disable()
        assertNull(store.verifier); assertTrue(store.locks.isEmpty())
        parental.setPin("5678".toCharArray()); parental.setLock("a", ContentKind.LIVE, "Adults", true); parental.relock()
        parental.forget()
        assertNull(store.verifier); assertTrue(store.locks.isEmpty())
        assertFalse(parental.state.value.pinSet)
        assertTrue(parental.hidden("a").isEmpty)
    }

    @Test fun restoredPinStartsLockedAndCannotBeReplacedUntilUnlocked() = runBlocking {
        controls().setPin("1234".toCharArray())
        val restored = controls()
        assertTrue(restored.state.value.pinSet)
        assertFalse(restored.state.value.unlocked)
        try { restored.setPin("9999".toCharArray()); fail("Locked PIN must not be replaceable") } catch (_: IllegalStateException) {}
        assertTrue(store.verifier!!.matches("1234".toCharArray()))
    }
}
