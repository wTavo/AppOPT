package com.example.appopt.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import com.example.appopt.R
import java.io.FileOutputStream
import java.io.OutputStream
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withRotation

/**
 * Generador nativo y blindado del Kit de Recuperación de Emergencia (*Emergency Kit*) en formato PDF.
 *
 * Características de seguridad y anti-extracción:
 * - **Rasterización Gráfica Anti-Copiado (300 DPI):** Todo el contenido se dibuja en un lienzo de alta resolución
 *   y se inserta como gráfico puro. Esto imposibilita la selección, subrayado o copiado de texto por parte de
 *   usuarios, visores de PDF o scripts maliciosos de scraping (*Zero Text Scraping*).
 * - **Patrón de Seguridad y Marca de Agua:** Estampa un fondo de seguridad con marca de agua tenue anti-fotocopia.
 * - **Cero Metadatos:** Generación en memoria sin dependencias de red, librerías de terceros ni metadatos de usuario.
 */
object EmergencyKitPdfGenerator {

    private const val PAGE_WIDTH = 595 // Ancho A4 en puntos (72 dpi)
    private const val PAGE_HEIGHT = 842 // Alto A4 en puntos (72 dpi)
    private const val SCALE_FACTOR = 3f // Escala de renderizado para 300 DPI (Calidad de imprenta)

    /**
     * Genera el documento PDF del Kit de Emergencia protegido contra copiado y lo escribe en [OutputStream].
     *
     * @param context Contexto de la aplicación para resolver recursos de texto.
     * @param primaryMethodTitle Título del método principal (ej. "Contraseña maestra" o "Clave de 64 dígitos").
     * @param primaryMethodValue Valor o indicador del método principal.
     * @param mnemonicWords Lista de las 12 palabras BIP-39 ordenadas del 1 al 12.
     * @param outputStream Flujo de salida donde se grabará el archivo PDF binario.
     */
    fun writeEmergencyKitPdf(
        context: Context,
        primaryMethodTitle: String,
        primaryMethodValue: String,
        mnemonicWords: List<String>,
        outputStream: OutputStream
    ) {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        val page = document.startPage(pageInfo)
        val pdfCanvas = page.canvas

        // 1. Crear lienzo de alta resolución (300 DPI) para rasterización gráfica
        val bitmapWidth = (PAGE_WIDTH * SCALE_FACTOR).toInt()
        val bitmapHeight = (PAGE_HEIGHT * SCALE_FACTOR).toInt()
        val highResBitmap = createBitmap(bitmapWidth, bitmapHeight)
        val renderCanvas = Canvas(highResBitmap)

        // Fondo blanco inicial
        renderCanvas.drawColor(Color.WHITE)

        // Escalar el lienzo para usar coordenadas virtuales de 595 x 842
        renderCanvas.scale(SCALE_FACTOR, SCALE_FACTOR)

        // 2. Dibujar marca de agua y contenido gráfico completo
        renderWatermark(context, renderCanvas)
        renderPdfContent(context, renderCanvas, primaryMethodTitle, primaryMethodValue, mnemonicWords)

        // 3. Estampar el bitmap rasterizado en la página PDF (Cero texto plano seleccionable)
        val filterPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            isAntiAlias = true
            isDither = true
        }
        val destRect = RectF(0f, 0f, PAGE_WIDTH.toFloat(), PAGE_HEIGHT.toFloat())
        pdfCanvas.drawBitmap(highResBitmap, null, destRect, filterPaint)

        // 4. Liberar memoria del bitmap
        highResBitmap.recycle()

        document.finishPage(page)
        document.writeTo(outputStream)
        document.close()
    }

    /**
     * Envía el Kit de Emergencia protegido directamente al administrador de impresión de Android ([PrintManager]).
     *
     * @param context Contexto de la actividad.
     * @param primaryMethodTitle Título del método principal.
     * @param primaryMethodValue Valor del método principal.
     * @param mnemonicWords Lista de 12 palabras BIP-39.
     */
    fun printEmergencyKit(
        context: Context,
        primaryMethodTitle: String,
        primaryMethodValue: String,
        mnemonicWords: List<String>
    ) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
        val jobName = "${context.getString(R.string.app_name)}_Emergency_Kit"

        // Suspender temporalmente el bloqueo por timeout para no desmontar el diálogo de protección al volver del visor de impresión
        com.example.appopt.AuthenticatorApp.instance.appLockManager.suspendLockTemporarily()

        val printAdapter = object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes?,
                cancellationSignal: CancellationSignal?,
                callback: LayoutResultCallback?,
                extras: Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback?.onLayoutCancelled()
                    return
                }

                val info = PrintDocumentInfo.Builder(context.getString(R.string.emergency_kit_pdf_filename))
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(1)
                    .build()

                callback?.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out PageRange>?,
                destination: ParcelFileDescriptor?,
                cancellationSignal: CancellationSignal?,
                callback: WriteResultCallback?
            ) {
                if (destination == null) {
                    callback?.onWriteFailed(context.getString(R.string.emergency_kit_descriptor_error))
                    return
                }

                try {
                    FileOutputStream(destination.fileDescriptor).use { out ->
                        writeEmergencyKitPdf(context, primaryMethodTitle, primaryMethodValue, mnemonicWords, out)
                    }
                    callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (_: Exception) {
                    callback?.onWriteFailed(context.getString(R.string.emergency_kit_print_error))
                }
            }
        }

        printManager.print(jobName, printAdapter, PrintAttributes.Builder().build())
    }

    /**
     * Dibuja una marca de agua diagonal de seguridad tenue para protección anti-fotocopia.
     *
     * @param context Contexto de la aplicación para resolver recursos de texto.
     * @param canvas Lienzo de alta resolución donde se estampará la marca de agua.
     */
    private fun renderWatermark(context: Context, canvas: Canvas) {
        val watermarkPaint = Paint().apply {
            color = Color.argb(12, 15, 23, 42) // Opacidad ultrabaja ~5%
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        canvas.withRotation(-32f, PAGE_WIDTH / 2f, PAGE_HEIGHT / 2f) {
            val text = context.getString(R.string.emergency_kit_watermark_text)
            for (y in -200..1200 step 140) {
                for (x in -300..900 step 360) {
                    drawText(text, x.toFloat(), y.toFloat(), watermarkPaint)
                }
            }
        }
    }

    private fun renderPdfContent(
        context: Context,
        canvas: Canvas,
        primaryMethodTitle: String,
        primaryMethodValue: String,
        mnemonicWords: List<String>
    ) {
        val titlePaint = Paint().apply {
            color = Color.rgb(15, 23, 42) // Slate 900
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val subtitlePaint = Paint().apply {
            color = Color.rgb(100, 116, 139) // Slate 500
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }

        val sectionTitlePaint = Paint().apply {
            color = Color.rgb(30, 41, 59) // Slate 800
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val monoPaint = Paint().apply {
            color = Color.rgb(15, 23, 42)
            textSize = 9.5f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            isAntiAlias = true
        }

        val cardBackgroundPaint = Paint().apply {
            color = Color.rgb(241, 245, 249) // Slate 100
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        val cardBorderPaint = Paint().apply {
            color = Color.rgb(203, 213, 225) // Slate 300
            style = Paint.Style.STROKE
            strokeWidth = 1f
            isAntiAlias = true
        }

        val accentLinePaint = Paint().apply {
            color = Color.rgb(37, 99, 235) // Blue 600
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        var currentY = 48f
        val leftMargin = 42f
        val contentWidth = PAGE_WIDTH - (leftMargin * 2)

        // 1. Barra superior decorativa de acento
        canvas.drawRect(leftMargin, currentY, leftMargin + contentWidth, currentY + 4f, accentLinePaint)
        currentY += 24f

        // 2. Encabezado principal
        canvas.drawText(context.getString(R.string.emergency_kit_pdf_title), leftMargin, currentY, titlePaint)
        currentY += 16f
        canvas.drawText(context.getString(R.string.emergency_kit_pdf_subtitle), leftMargin, currentY, subtitlePaint)
        currentY += 14f

        // Metadatos (Fecha de emisión)
        val formattedDate = DateTimeFormatter.formatAbsoluteDateTime(System.currentTimeMillis())
        val dateText = "${context.getString(R.string.emergency_kit_pdf_generated_at)}: $formattedDate"
        canvas.drawText(dateText, leftMargin, currentY, subtitlePaint)
        currentY += 24f

        // Línea divisoria
        canvas.drawLine(leftMargin, currentY, leftMargin + contentWidth, currentY, cardBorderPaint)
        currentY += 20f

        // 3. Sección: Método Principal
        canvas.drawText(primaryMethodTitle, leftMargin, currentY, sectionTitlePaint)
        currentY += 14f

        val lines = primaryMethodValue.split("\n")
        val lineHeight = 16f
        val cardHeight = maxOf(36f, (lines.size * lineHeight) + 16f)
        val primaryCardRect = RectF(leftMargin, currentY, leftMargin + contentWidth, currentY + cardHeight)
        canvas.drawRoundRect(primaryCardRect, 6f, 6f, cardBackgroundPaint)
        canvas.drawRoundRect(primaryCardRect, 6f, 6f, cardBorderPaint)

        if (lines.size == 1) {
            canvas.drawText(lines[0], leftMargin + 12f, currentY + 22f, monoPaint)
        } else {
            lines.forEachIndexed { index, line ->
                canvas.drawText(line, leftMargin + 12f, currentY + 18f + (index * lineHeight), monoPaint)
            }
        }
        currentY += cardHeight + 16f

        // 4. Sección: Frase de Emergencia (12 Palabras BIP-39)
        canvas.drawText(context.getString(R.string.emergency_kit_pdf_words_section_title), leftMargin, currentY, sectionTitlePaint)
        currentY += 10f
        canvas.drawText(context.getString(R.string.emergency_kit_pdf_words_instruction), leftMargin, currentY, subtitlePaint)
        currentY += 14f

        // Cuadrícula de 12 palabras (2 columnas de 6)
        val colWidth = (contentWidth - 16f) / 2f
        val rowHeight = 26f

        val wordsGridRect = RectF(leftMargin, currentY, leftMargin + contentWidth, currentY + (rowHeight * 6) + 12f)
        canvas.drawRoundRect(wordsGridRect, 8f, 8f, cardBackgroundPaint)
        canvas.drawRoundRect(wordsGridRect, 8f, 8f, cardBorderPaint)

        val firstColX = leftMargin + 16f
        val secondColX = leftMargin + colWidth + 16f
        val baseWordY = currentY + 20f

        for (i in 0 until 6) {
            val wordY = baseWordY + (i * rowHeight)
            val leftWord = if (i < mnemonicWords.size) "${i + 1}. ${mnemonicWords[i]}" else ""
            val rightIndex = i + 6
            val rightWord = if (rightIndex < mnemonicWords.size) "${rightIndex + 1}. ${mnemonicWords[rightIndex]}" else ""

            canvas.drawText(leftWord, firstColX, wordY, monoPaint)
            canvas.drawText(rightWord, secondColX, wordY, monoPaint)
        }

        // 5. Pie de página de seguridad y advertencia
        val warningRect = RectF(leftMargin, PAGE_HEIGHT - 110f, leftMargin + contentWidth, PAGE_HEIGHT - 50f)
        val warningBgPaint = Paint().apply {
            color = Color.rgb(254, 242, 242) // Red 50
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val warningBorderPaint = Paint().apply {
            color = Color.rgb(254, 202, 202) // Red 200
            style = Paint.Style.STROKE
            strokeWidth = 1f
            isAntiAlias = true
        }
        val warningTextPaint = Paint().apply {
            color = Color.rgb(185, 28, 28) // Red 700
            textSize = 8.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }

        canvas.drawRoundRect(warningRect, 6f, 6f, warningBgPaint)
        canvas.drawRoundRect(warningRect, 6f, 6f, warningBorderPaint)

        canvas.drawText(context.getString(R.string.emergency_kit_pdf_warning_1), leftMargin + 10f, warningRect.top + 18f, warningTextPaint)
        canvas.drawText(context.getString(R.string.emergency_kit_pdf_warning_2), leftMargin + 10f, warningRect.top + 34f, warningTextPaint)
    }
}
