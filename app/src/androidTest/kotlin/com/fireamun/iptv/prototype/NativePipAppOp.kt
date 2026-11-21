package com.fireamun.iptv.prototype

import android.app.AppOpsManager
import android.os.ParcelFileDescriptor
import android.os.Process
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals

/** Keeps native PiP denied for the whole fixture, including ActivityScenario cleanup. */
internal fun withDeniedNativePipAppOp(block: () -> Unit) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val context = instrumentation.targetContext
    val appOps = context.getSystemService(AppOpsManager::class.java)
    fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand(command),
    ).bufferedReader().use { it.readText() }
    fun originalPipAppOp(): String {
        val output = shell("appops get ${context.packageName} PICTURE_IN_PICTURE")
        return Regex("PICTURE_IN_PICTURE:\\s*(allow|ignore|deny|default|foreground)").find(output)?.groupValues?.get(1)
            // An absent PiP op defaults to allow; literal MODE_DEFAULT is a distinct native mode.
            ?: if (output.contains("No operations")) {
                Regex("Default mode:\\s*(allow|ignore|deny|default|foreground)").find(output)?.groupValues?.get(1)
                    ?: error("Cannot determine original default PiP app-op mode")
            } else error("Cannot determine original PiP app-op mode")
    }
    val original = originalPipAppOp()
    val originalEffective = appOps.checkOpNoThrow(AppOpsManager.OPSTR_PICTURE_IN_PICTURE, Process.myUid(), context.packageName)
    try {
        shell("appops set ${context.packageName} PICTURE_IN_PICTURE deny")
        assertEquals(AppOpsManager.MODE_ERRORED,
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_PICTURE_IN_PICTURE, Process.myUid(), context.packageName))
        block()
    } finally {
        shell("appops set ${context.packageName} PICTURE_IN_PICTURE $original")
        assertEquals("Restore the original effective PiP app-op mode", originalEffective,
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_PICTURE_IN_PICTURE, Process.myUid(), context.packageName))
        assertEquals("Restore the exact original PiP app-op mode", original, originalPipAppOp())
    }
}
