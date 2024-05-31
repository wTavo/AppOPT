package com.example.appopt

import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import dev.turingcomplete.kotlinonetimepassword.GoogleAuthenticator
import org.apache.commons.codec.binary.Base32
import java.nio.charset.StandardCharsets
import java.util.*
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


class MainActivity : AppCompatActivity() {
    private lateinit var otpTextView: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var qrCodeImageView: ImageView
    private lateinit var handler: Handler

    private val plainTextSecret = "HolaMundo123".toByteArray(StandardCharsets.UTF_8)
    private val base32EncodedSecret = Base32().encodeToString(plainTextSecret)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        otpTextView = findViewById(R.id.otpTextView)
        progressBar = findViewById(R.id.progressBar)
        qrCodeImageView = findViewById(R.id.qrCodeImageView)
        handler = Handler(Looper.getMainLooper())

        generateQrCode()
        startOtpGeneration()
    }

    private fun startOtpGeneration() {
        CoroutineScope(Dispatchers.Main).launch {
            var counter = 0L
            while (true) {
                val timestamp = Date(counter * 30000)

                val googleAuthenticator = GoogleAuthenticator(base32EncodedSecret)
                val otpCode = googleAuthenticator.generate(timestamp)

                otpTextView.text = otpCode

                val job = launch(Dispatchers.IO) {
                    repeat(30) {
                        delay(1000)
                        progressBar.setProgress((it + 1) * 100 / 30, true)
                    }
                }

                job.join()
                counter++
            }
        }
    }


    private fun generateQrCode() {
        val qrCodeSize = 200
        val qrCodeWriter = QRCodeWriter()
        val qrText = "otpauth://totp/Tavo:GusMontejo.25@gmail.com?secret=$base32EncodedSecret&issuer=Tavo&algorithm=SHA1&digits=12&period=30"
        Log.d("TAG", "El valor de base32EncodedSecret es: $base32EncodedSecret")
        val hints = mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L)
        val bitMatrix = qrCodeWriter.encode(qrText, BarcodeFormat.QR_CODE, qrCodeSize, qrCodeSize, hints)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        qrCodeImageView.setImageBitmap(bitmap)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}