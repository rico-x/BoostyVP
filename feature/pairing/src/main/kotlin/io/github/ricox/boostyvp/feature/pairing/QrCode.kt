package io.github.ricox.boostyvp.feature.pairing

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Renders [content] as a QR bitmap.
 *
 * Two choices matter for a code being photographed off a television:
 * error correction is set high, because glare and the panel's own pixel grid
 * eat modules; and the quiet zone is kept wide, because scanners fail on codes
 * that run to the edge of a bright rectangle.
 */
internal fun generateQrBitmap(content: String, sizePx: Int = 720): ImageBitmap? = runCatching {
    val hints = mapOf(
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H,
        EncodeHintType.MARGIN to QUIET_ZONE_MODULES,
        EncodeHintType.CHARACTER_SET to "UTF-8",
    )

    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)

    val pixels = IntArray(matrix.width * matrix.height)
    for (y in 0 until matrix.height) {
        val row = y * matrix.width
        for (x in 0 until matrix.width) {
            pixels[row + x] = if (matrix.get(x, y)) Color.BLACK else Color.WHITE
        }
    }

    Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
        .apply { setPixels(pixels, 0, matrix.width, 0, 0, matrix.width, matrix.height) }
        .asImageBitmap()
}.getOrNull()

private const val QUIET_ZONE_MODULES = 2
