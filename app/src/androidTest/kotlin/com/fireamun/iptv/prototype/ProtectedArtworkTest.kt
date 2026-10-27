package com.fireamun.iptv.prototype

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.fireamun.iptv.core.data.*
import com.fireamun.iptv.core.model.*
import com.fireamun.iptv.core.storage.*
import kotlinx.coroutines.runBlocking
import mockwebserver3.*
import okio.Buffer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.util.UUID

/** Generated shapes are fixture artwork, never captured from an owner's source. */
internal fun syntheticPoster(index: Int): ByteArray {
    val bitmap = Bitmap.createBitmap(180, 270, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(intArrayOf(0xff233a42.toInt(), 0xff65402c.toInt(), 0xff383f2a.toInt())[index.mod(3)])
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xfff5cb5c.toInt() }
    canvas.drawCircle(90f, 105f, 45f, paint)
    paint.color = 0xff0b0b0b.toInt(); canvas.drawRect(0f, 165f, 180f, 270f, paint)
    paint.color = Color.WHITE; paint.textSize = 15f
    canvas.drawText("CINEMA $index", 18f, 215f, paint)
    return ByteArrayOutputStream().use { output -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, output); bitmap.recycle(); output.toByteArray() }
}

@RunWith(AndroidJUnit4::class)
class ProtectedArtworkTest {
    @get:Rule val diagnostics = TestDiagnostics()
    @Test fun fetchesProtectedArtworkWithOpaqueMemoryKeysAndNoDiskLocatorCache() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "art-${UUID.randomUUID()}.db"
        val protector = KeystoreEnvelope("iptv.test.$name")
        val store = RoomLibraryStore.open(context, name, protector)
        try {
            MockWebServer().use { server ->
                server.enqueue(MockResponse.Builder().addHeader("Content-Type", "image/png").body(Buffer().write(syntheticPoster(1))).build()); server.start()
                val privateUrl = server.url("/poster?token=private-art-fixture").toString()
                store.save(SourceSpec.file(), listOf(PlaylistEntry(Channel(0, "Fixture", null, ContentKind.MOVIE), MediaAddress.parse("https://example.invalid/movie"), artwork = MediaAddress.parse(privateUrl))))
                val session = store.openSession()!!
                val reference = LibraryArtwork(session.sourceId!!, 0, session.generation)
                val loader = protectedImageLoader(context, store)
                try {
                    val result = loader.execute(ImageRequest.Builder(context).data(reference).size(90, 135).build())
                    assertTrue(result is SuccessResult)
                    val key = (result as SuccessResult).memoryCacheKey!!.key
                    assertTrue(key.startsWith("art:")); assertFalse(key.contains("private-art-fixture")); assertFalse(key.contains("http"))
                    assertNull(loader.diskCache)
                    assertEquals(1, server.requestCount)
                } finally { loader.shutdown() }
            }
        } finally { store.close(); context.deleteDatabase(name); protector.clear() }
    }
}
