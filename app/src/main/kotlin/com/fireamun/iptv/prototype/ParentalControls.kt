package com.fireamun.iptv.prototype

import android.content.Context
import android.util.Base64
import com.fireamun.iptv.core.data.ParentalPin
import com.fireamun.iptv.core.data.PinThrottle
import com.fireamun.iptv.core.data.PinVerifier
import com.fireamun.iptv.core.model.CategoryLocks
import com.fireamun.iptv.core.model.ContentKind
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** [revision] changes whenever what is hidden may have changed, so browsing can refresh. */
data class ParentalState(val pinSet: Boolean = false, val unlocked: Boolean = false, val revision: Long = 0)

sealed interface PinResult {
    data object Accepted : PinResult
    data class Rejected(val waitMs: Long) : PinResult
    data class Wait(val waitMs: Long) : PinResult
}

interface ParentalStore {
    fun verifier(): PinVerifier?
    fun saveVerifier(value: PinVerifier?)
    fun throttle(): PinThrottle
    fun saveThrottle(value: PinThrottle)
    fun locks(sourceId: String): CategoryLocks
    fun saveLocks(sourceId: String, value: CategoryLocks)
    fun clearLocks(sourceId: String?)
}

/**
 * Household parental PIN. While a PIN is set and the session is locked, chosen categories are hidden everywhere the
 * catalogue is read. One correct PIN opens them until the app has been in the background for [RELOCK_AFTER_MS] or
 * the process ends. All PIN work happens on [compute]; callers stay on the main thread.
 */
class ParentalControls(
    private val store: ParentalStore,
    private val clock: () -> Long = System::currentTimeMillis,
    private val compute: CoroutineDispatcher = Dispatchers.Default,
) {
    private val mutableState = MutableStateFlow(ParentalState(pinSet = store.verifier() != null))
    val state = mutableState.asStateFlow()
    private var backgroundSince: Long? = null

    private fun publish(pinSet: Boolean = mutableState.value.pinSet, unlocked: Boolean = mutableState.value.unlocked) {
        mutableState.value = ParentalState(pinSet, pinSet && unlocked, mutableState.value.revision + 1)
    }

    val restricted get() = mutableState.value.let { it.pinSet && !it.unlocked }
    fun locks(sourceId: String?): CategoryLocks = sourceId?.let(store::locks) ?: CategoryLocks()
    fun hidden(sourceId: String?): CategoryLocks = if (restricted) locks(sourceId) else CategoryLocks()
    fun blocks(sourceId: String?, kind: ContentKind, group: String?) = restricted && locks(sourceId).blocks(kind, group)
    fun waitMs(): Long = store.throttle().waitMs(clock())

    suspend fun unlock(pin: CharArray): PinResult = try {
        val verifier = store.verifier()
        val wait = waitMs()
        when {
            verifier == null -> PinResult.Accepted
            wait > 0 -> PinResult.Wait(wait)
            withContext(compute) { verifier.matches(pin) } -> {
                store.saveThrottle(PinThrottle())
                publish(unlocked = true)
                PinResult.Accepted
            }
            else -> {
                val next = store.throttle().failed(clock())
                store.saveThrottle(next)
                PinResult.Rejected(next.waitMs(clock()))
            }
        }
    } finally { pin.fill('0') }

    /** Setting or changing the PIN is only offered while unlocked (or before any PIN exists). */
    suspend fun setPin(pin: CharArray) {
        try {
            check(!restricted)
            val verifier = withContext(compute) { ParentalPin.create(pin) }
            store.saveVerifier(verifier)
            store.saveThrottle(PinThrottle())
            publish(pinSet = true, unlocked = true)
        } finally { pin.fill('0') }
    }

    /** Turning the PIN off also drops every category lock. */
    fun disable() {
        check(!restricted)
        store.saveVerifier(null)
        store.saveThrottle(PinThrottle())
        store.clearLocks(null)
        publish(pinSet = false, unlocked = false)
    }

    fun setLock(sourceId: String, kind: ContentKind, label: String, locked: Boolean) {
        check(!restricted && mutableState.value.pinSet)
        val current = store.locks(sourceId)
        if (locked && current.labels(kind).size >= MAX_LOCKS_PER_KIND) return
        store.saveLocks(sourceId, current.with(kind, label, locked))
        publish()
    }

    fun relock() { if (mutableState.value.unlocked) publish(unlocked = false) }
    fun onBackground() { backgroundSince = clock() }
    fun onForeground() {
        val since = backgroundSince ?: return
        backgroundSince = null
        val elapsed = clock() - since
        if (elapsed < 0 || elapsed >= RELOCK_AFTER_MS) relock()
    }

    fun sourceRemoved(sourceId: String) { store.clearLocks(sourceId); publish() }

    /** "Forgot PIN": the caller removes every playlist; the PIN, throttle and all locks go with them. */
    fun forget() {
        store.saveVerifier(null)
        store.saveThrottle(PinThrottle())
        store.clearLocks(null)
        publish(pinSet = false, unlocked = false)
    }

    companion object {
        const val RELOCK_AFTER_MS = 5 * 60_000L
        /** Keeps the store's NOT IN clause well under SQLite's bound-argument limit. */
        const val MAX_LOCKS_PER_KIND = 300
    }
}

/** Group labels and a salted verifier only; no PIN, source address or credential enters these preferences. */
internal class PreferencesParentalStore(context: Context) : ParentalStore {
    private val prefs = context.applicationContext.getSharedPreferences("parental", Context.MODE_PRIVATE)

    override fun verifier(): PinVerifier? = runCatching {
        val salt = prefs.getString("salt", null) ?: return null
        val hash = prefs.getString("hash", null) ?: return null
        PinVerifier(Base64.decode(salt, Base64.NO_WRAP), Base64.decode(hash, Base64.NO_WRAP), prefs.getInt("iterations", 0))
    }.getOrNull()

    override fun saveVerifier(value: PinVerifier?) {
        prefs.edit().apply {
            if (value == null) remove("salt").remove("hash").remove("iterations")
            else putString("salt", Base64.encodeToString(value.salt(), Base64.NO_WRAP))
                .putString("hash", Base64.encodeToString(value.hash(), Base64.NO_WRAP)).putInt("iterations", value.iterations)
        }.commit()
    }

    override fun throttle() = runCatching {
        PinThrottle(prefs.getInt("failures", 0), prefs.getLong("lockedUntil", 0))
    }.getOrDefault(PinThrottle())

    override fun saveThrottle(value: PinThrottle) {
        prefs.edit().putInt("failures", value.failures).putLong("lockedUntil", value.lockedUntilMs).commit()
    }

    override fun locks(sourceId: String): CategoryLocks = runCatching {
        val json = JSONObject(prefs.getString(key(sourceId), null) ?: return CategoryLocks())
        CategoryLocks(CategoryLocks.TAB_KINDS.associateWith { kind ->
            json.optJSONArray(kind.name)?.let { array -> (0 until array.length()).map(array::getString).toSet() }.orEmpty()
        }.filterValues { it.isNotEmpty() })
    }.getOrDefault(CategoryLocks())

    override fun saveLocks(sourceId: String, value: CategoryLocks) {
        if (value.isEmpty) { prefs.edit().remove(key(sourceId)).commit(); return }
        val json = JSONObject()
        value.byKind.forEach { (kind, labels) -> json.put(kind.name, JSONArray(labels.sorted())) }
        prefs.edit().putString(key(sourceId), json.toString()).commit()
    }

    override fun clearLocks(sourceId: String?) {
        prefs.edit().apply {
            if (sourceId != null) remove(key(sourceId)) else prefs.all.keys.filter { it.startsWith(PREFIX) }.forEach(::remove)
        }.commit()
    }

    private fun key(sourceId: String) = PREFIX + sourceId
    private companion object { const val PREFIX = "locks:" }
}
