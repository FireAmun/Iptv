package com.fireamun.iptv.prototype

import android.app.Instrumentation
import android.os.ParcelFileDescriptor
import org.junit.Assert.*

/** Synthetic/empty test screens only; never call this during private-provider testing. */
internal fun captureSyntheticPreview(instrumentation: Instrumentation, name: String) {
    require(name.matches(Regex("[a-z0-9-]+")))
    // Compose semantics may be ready before the previous startup frame leaves the display.
    instrumentation.waitForIdleSync()
    instrumentation.uiAutomation.waitForIdle(100, 5000)
    fun command(value: String): ByteArray {
        val descriptor = instrumentation.uiAutomation.executeShellCommand(value)
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
    }
    command("mkdir -p /data/local/tmp/iptv-previews")
    command("screencap -p /data/local/tmp/iptv-previews/$name.png")
    val png = command("cat /data/local/tmp/iptv-previews/$name.png")
    assertTrue("Synthetic preview must be a PNG", png.size > 8)
    assertArrayEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a), png.copyOfRange(0, 8))
}
