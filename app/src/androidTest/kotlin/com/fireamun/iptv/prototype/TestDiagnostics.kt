package com.fireamun.iptv.prototype

import android.os.Process
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers

/** Test APK only. CI can bound a stalled test and retain safe Java stacks in its report. */
class TestDiagnostics : TestRule {
    enum class Phase { TEST_START, BEFORE_LIBRARY_RESET, AFTER_LIBRARY_RESET, BEFORE_LOCALE_RESET, AFTER_LOCALE_RESET, BEFORE_COMPOSE_IDLE, AFTER_COMPOSE_IDLE, BEFORE_SCENARIO_CLOSE, AFTER_SCENARIO_CLOSE, COMPLETE }
    private val current = AtomicReference(Phase.TEST_START)
    fun phase(value: Phase) {
        current.set(value)
        Log.i(TAG, "phase=${value.name}")
    }
    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            val finished = AtomicBoolean(false)
            val seconds = InstrumentationRegistry.getArguments().getString("diagnosticTimeoutSeconds")?.toLongOrNull()?.takeIf { it in 1..600 }
            val watchdog = seconds?.let {
                Thread({
                    try { Thread.sleep(it * 1000) } catch (_: InterruptedException) { return@Thread }
                    if (!finished.compareAndSet(false, true)) return@Thread
                    Log.e(TAG, "Timed out in phase=${current.get().name}")
                    for ((thread, stack) in Thread.getAllStackTraces().entries.sortedBy { entry -> entry.key.id }) {
                        // Do not print arbitrary thread names, exception messages or any test input.
                        Log.e(TAG, "threadId=${thread.id} state=${thread.state.name}")
                        for (frame in stack.take(48)) Log.e(TAG, "  ${frame.className}.${frame.methodName}:${frame.lineNumber}")
                    }
                    // A killed instrumentation process is a failed run, never a passing partial suite.
                    Process.killProcess(Process.myPid())
                }, "iptv-test-diagnostics").apply { isDaemon = true; start() }
            }
            fun resetLibrary() {
                phase(Phase.BEFORE_LIBRARY_RESET)
                val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as IptvApplication
                app.guideRefresher.automatic = false
                app.guideRefresher.select(null)
                runBlocking(Dispatchers.IO) { app.libraryStore.clear() }
                // A PIN or category lock left by one test must never hide another test's fixture.
                app.parentalControls.forget()
                // Paired phones and a remembered TV belong to one test only.
                app.remoteController.forget()
                InstrumentationRegistry.getInstrumentation().runOnMainSync { app.remoteController.finishTyping() }
                app.remoteHost.stopPairing()
                app.remoteHost.revokeAll()
                app.remoteHost.setEnabled(true)
                app.remoteHost.addressOverride = null
                phase(Phase.AFTER_LIBRARY_RESET)
            }
            try { resetLibrary(); base.evaluate() }
            finally {
                try { resetLibrary() } finally { finished.set(true); phase(Phase.COMPLETE); watchdog?.interrupt() }
            }
        }
    }
    private companion object { const val TAG = "IptvTestDiagnostics" }
}
