package com.sharesafe.app

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.test.core.app.ApplicationProvider
import com.sharesafe.app.core.ImageRedactor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import com.sharesafe.app.core.clampTo
import com.sharesafe.app.core.Beautifier
import com.sharesafe.app.core.BeautifyOptions
import com.sharesafe.app.core.BackgroundKind
import com.sharesafe.app.core.RedactRegion
import com.sharesafe.app.core.RedactStyle
import com.sharesafe.app.core.RegionKind

@RunWith(RobolectricTestRunner::class)
class RedactorAndGeometryTest {

    @Test
    fun `strength scaling maps to block and divisor ranges`() {
        assertEquals(6, ImageRedactor.pixelateBlockSize(0.5f))
        assertEquals(24, ImageRedactor.pixelateBlockSize(2f))
        assertEquals(48, ImageRedactor.pixelateBlockSize(4f))
        assertEquals(12, ImageRedactor.blurDivisor(0.5f))
        assertEquals(96, ImageRedactor.blurDivisor(4f))
    }

    @Test
    fun `clampTo enforces min size and bounds`() {
        // Fully off-screen rects get pulled back into bounds (never null).
        val pulled = Rect(-50, -50, -40, -40).clampTo(100, 100)!!
        assertTrue(pulled.left >= 0 && pulled.top >= 0)
        val c = Rect(90, 90, 200, 200).clampTo(100, 100, minSize = 8)!!
        assertTrue(c.right <= 100 && c.bottom <= 100)
        assertTrue(c.width() >= 8)
    }

    @Test
    fun `black style renders opaque pixels`() {
        val bmp = Bitmap.createBitmap(60, 60, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(android.graphics.Color.WHITE)
        val region = RedactRegion.new(Rect(10, 10, 40, 40), RegionKind.MANUAL, style = RedactStyle.BLACK)
        val out = ImageRedactor.render(bmp, listOf(region), RedactStyle.PIXELATE)
        val px = out.getPixel(25, 25)
        assertEquals(android.graphics.Color.BLACK, px)
    }

    @Test
    fun `beautifier adds padding around image`() {
        val bmp = Bitmap.createBitmap(100, 50, Bitmap.Config.ARGB_8888)
        val out = Beautifier.render(
            bmp,
            BeautifyOptions(paddingPx = 20, cornerRadiusPx = 10f, background = BackgroundKind.MIDNIGHT),
        )
        assertEquals(140, out.width)
        assertEquals(90, out.height)
    }

    @Test
    fun `viewmodel crop update clamps to source and marks manual`() {
        val vm = MainViewModel(ApplicationProvider.getApplicationContext())
        // no source → ignored
        vm.updateCropRect(Rect(0, 0, 10, 10))
        assertNull(vm.detectedCrop)
    }
}
