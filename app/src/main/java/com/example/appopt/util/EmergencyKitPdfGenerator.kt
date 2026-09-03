package com.example.appopt.util

import android.content.Context
import android.graphics.Bitmap
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
import com.example.appopt.ui.util.QrCodeGenerator
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Generador nativo del Kit de Recuperación de Emergencia (*Emergency Kit*) en formato PDF.
 *
 * Principio de diseño de seguridad (Sección 6 y 12 del Plan):
 * - Genera localmente un documento formal A4 sin dependencias externas de red.
 * - Incluye fecha, método principal, cuadrícula de 12 palabras BIP-39 y código QR de restauración rápida.
 * - Permite impresión directa a través del [PrintManager] de Android o exportación a archivo PDF.
 */
object EmergencyKitPdfGenerator {

    private const val PAGE_WIDTH = 595 // Ancho A4 en puntos (72 dpi)
    private const val PAGE_HEIGHT = 842 // Alto A4 en puntos (72 dpi)

    /**
     * Genera el documento PDF del Kit de Emergencia y lo escribe en el [OutputStream] proporcionado.
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
        val canvas = page.canvas

        renderPdfContent(context, canvas, primaryMethodTitle, primaryMethodValue, mnemonicWords)

        document.finishPage(page)
        document.writeTo(outputStream)
        document.close()
    }

    /**
     * Envía el Kit de Emergencia directamente al administrador de impresión de Android ([PrintManager])
     * permitiendo al usuario imprimirlo físicamente o guardarlo como PDF con la impresora virtual del sistema.
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

                val info = PrintDocumentInfo.Builder("AppOPT_Emergency_Kit.pdf")
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
                    callback?.onWriteFailed("Descriptor nulo")
                    return
                }

                try {
                    FileOutputStream(destination.fileDescriptor).use { out ->
                        writeEmergencyKitPdf(context, primaryMethodTitle, primaryMethodValue, mnemonicWords, out)
                    }
                    callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback?.onWriteFailed(e.message)
                }
            }
        }

        printManager.print(jobName, printAdapter, PrintAttributes.Builder().build())
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

        val bodyPaint = Paint().apply {
            color = Color.rgb(51, 65, 85) // Slate 700
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
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

        // Metadatos (Fecha de generación)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val dateText = "${context.getString(R.string.emergency_kit_pdf_generated_at)}: ${dateFormat.format(Date())}"
        canvas.drawText(dateText, leftMargin, currentY, subtitlePaint)
        currentY += 24f

        // Línea divisoria
        canvas.drawLine(leftMargin, currentY, leftMargin + contentWidth, currentY, cardBorderPaint)
        currentY += 20f

        // 3. Sección: Método Principal
        canvas.drawText(primaryMethodTitle, leftMargin, currentY, sectionTitlePaint)
        currentY += 14f

        val primaryCardRect = RectF(leftMargin, currentY, leftMargin + contentWidth, currentY + 36f)
        canvas.drawRoundRect(primaryCardRect, 6f, 6f, cardBackgroundPaint)
        canvas.drawRoundRect(primaryCardRect, 6f, 6f, cardBorderPaint)

        canvas.drawText(primaryMethodValue, leftMargin + 12f, currentY + 22f, monoPaint)
        currentY += 52f

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

        var wordY = currentY + 20f
        val firstColX = leftMargin + 16f
        val secondColX = leftMargin + colWidth + 16f

        for (i in 0 until 6) {
            val leftWord = if (i < mnemonicWords.size) "${i + 1}. ${mnemonicWords[i]}" else ""
            val rightIndex = i + 6
            val rightWord = if (rightIndex < mnemonicWords.size) "${rightIndex + 1}. ${mnemonicWords[rightIndex]}" else ""

            canvas.drawText(leftWord, firstColX, wordY, monoPaint)
            canvas.drawText(rightWord, secondColX, wordY, monoPaint)
            wordY += rowHeight
        }

        currentY = wordsGridRect.bottom + 24f

        // 5. Sección: Código QR de Recuperación Rápida
        canvas.drawText(context.getString(R.string.emergency_kit_pdf_qr_section_title), leftMargin, currentY, sectionTitlePaint)
        currentY += 10f
        canvas.drawText(context.getString(R.string.emergency_kit_pdf_qr_instruction), leftMargin, currentY, subtitlePaint)
        currentY += 14f

        val qrContent = mnemonicWords.joinToString(" ")
        val qrBitmap = QrCodeGenerator.generateQrBitmap(qrContent, 160)

        if (qrBitmap != null) {
            val qrX = leftMargin + (contentWidth - 160f) / 2f
            canvas.drawBitmap(qrBitmap, qrX, currentY, null)
            currentY += 175f
        }

        // 6. Pie de página de seguridad y advertencia
        val warningRect = RectF(leftMargin, PAGE_HEIGHT - 90f, leftMargin + contentWidth, PAGE_HEIGHT - 40f)
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
