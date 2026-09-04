package com.example.appopt.ui.util

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import java.util.EnumMap
import androidx.core.graphics.createBitmap

/**
 * Utilidad pura para la generación local de mapas de bits ([Bitmap]) de códigos QR.
 *
 * Principio de cero telemetría:
 * - Toda la generación de gráficos vectoriales y matrices QR se realiza en el procesador local.
 * - Cero peticiones de red y sin APIs de terceros externas.
 */
object QrCodeGenerator {

    /**
     * Genera un [Bitmap] monocromático de código QR a partir del contenido de texto provisto.
     *
     * @param content Cadena o URI a codificar en la matriz de código QR.
     * @param size Ancho y alto de la imagen resultante en píxeles.
     * @return [Bitmap] renderizado en memoria o null si ocurre un fallo de codificación.
     */
    fun generateQrBitmap(content: String, size: Int = 512): Bitmap? {
        return try {
            val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
                put(EncodeHintType.MARGIN, 1)
                put(EncodeHintType.CHARACTER_SET, "UTF-8")
            }
            val bitMatrix = MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val pixels = IntArray(width * height)
            for (y in 0 until height) {
                val offset = y * width
                for (x in 0 until width) {
                    pixels[offset + x] = if (bitMatrix[x, y]) Color.BLACK else Color.WHITE
                }
            }
            createBitmap(width, height, Bitmap.Config.RGB_565).apply {
                setPixels(pixels, 0, width, 0, 0, width, height)
            }
        } catch (_: Exception) {
            null
        }
    }
}
