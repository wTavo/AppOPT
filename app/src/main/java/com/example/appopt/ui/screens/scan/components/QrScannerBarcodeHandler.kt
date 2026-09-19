package com.example.appopt.ui.screens.scan.components

import com.example.appopt.domain.model.ParsedOtpData
import com.example.appopt.domain.model.TransferQrChunk
import com.example.appopt.domain.totp.OtpUriParser
import com.example.appopt.security.TransferCrypto
import com.example.appopt.ui.screens.scan.QrScannerMode
import com.example.appopt.ui.theme.AppHaptics
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/**
 * Gestor y orquestador desacoplado para el procesamiento y filtrado de códigos QR escaneados (Directivas 8, 28 y 29).
 */
object QrScannerBarcodeHandler {

    /**
     * Procesa y filtra de forma segura un código QR escaneado según el [mode] activo.
     *
     * En modo [QrScannerMode.SINGLE_ACCOUNT]:
     * - Procesa exclusivamente códigos OTP individuales (`otpauth://`).
     * - Ignora silenciosamente códigos de transferencia u otros formatos no pertinentes.
     *
     * En modo [QrScannerMode.TRANSFER_MIGRATION]:
     * - Procesa exclusivamente códigos de transferencia por lotes cifrados (`appopt-transfer://`).
     * - Ignora silenciosamente códigos OTP individuales estándar u otros formatos.
     *
     * @param rawValue Cadena bruta leída por el escáner.
     * @param mode Modo de filtrado y operación del escáner.
     * @param appHaptics Controlador de vibración y háptica del sistema.
     * @param blockedSessionIds Identificadores de sesiones bloqueadas por exceso de intentos.
     * @param blockedPayloadFingerprints Huellas de cargas útiles bloqueadas.
     * @param sessionChunks Mapa de fragmentos acumulados para la sesión activa.
     * @param currentSessionId Identificador de la sesión activa de transferencia.
     * @param onSessionUpdated Callback invocado cuando se detecta una nueva sesión o cambia la cantidad esperada de fragmentos.
     * @param onChunkScanned Callback invocado al registrar exitosamente un fragmento nuevo.
     * @param onChunkDuplicate Callback invocado al re-escanear un fragmento ya presente en la sesión.
     * @param onSingleOtpScanned Callback invocado al escanear una clave OTP individual (otpauth://).
     * @param onTransferPayloadReady Callback invocado para transferencias de un único fragmento.
     * @param onChunksReady Callback invocado cuando se recopilan todos los fragmentos requeridos.
     * @param onIgnored Callback invocado cuando el código no corresponde al modo activo para reanudar el escaneo silenciosamente.
     * @param onError Callback invocado ante fallos de análisis o bloqueos por seguridad en códigos pertinentes.
     */
    suspend fun handleScannedBarcode(
        rawValue: String,
        mode: QrScannerMode,
        appHaptics: AppHaptics,
        blockedSessionIds: Set<Long>,
        blockedPayloadFingerprints: Set<Int>,
        sessionChunks: MutableMap<Int, TransferQrChunk>,
        currentSessionId: Long,
        onSessionUpdated: (Long, Int) -> Unit,
        onChunkScanned: (Int) -> Unit,
        onChunkDuplicate: (Int) -> Unit,
        onSingleOtpScanned: (ParsedOtpData) -> Unit,
        onTransferPayloadReady: (String) -> Unit,
        onChunksReady: (List<TransferQrChunk>) -> Unit,
        onIgnored: () -> Unit,
        onError: suspend () -> Unit
    ) {
        when (mode) {
            QrScannerMode.SINGLE_ACCOUNT -> {
                if (rawValue.startsWith("otpauth://", ignoreCase = true)) {
                    val parseResult = OtpUriParser.parse(rawValue)
                    if (parseResult.isSuccess) {
                        val otpData = parseResult.getOrThrow()
                        onSingleOtpScanned(otpData)
                    } else {
                        onError()
                    }
                } else {
                    onIgnored()
                }
            }
            QrScannerMode.TRANSFER_MIGRATION -> {
                if (rawValue.startsWith(TransferCrypto.QR_TRANSFER_PREFIX, ignoreCase = true)) {
                    if (rawValue.hashCode() in blockedPayloadFingerprints) {
                        onError()
                        return
                    }

                    try {
                        val chunk = TransferCrypto.parseTransferChunk(rawValue)
                        if (chunk.total > 1) {
                            if (chunk.sessionId in blockedSessionIds) {
                                onError()
                                return
                            }

                            if (chunk.sessionId != currentSessionId) {
                                sessionChunks.clear()
                                onSessionUpdated(chunk.sessionId, chunk.total)
                            }

                            if (sessionChunks.containsKey(chunk.index)) {
                                appHaptics.click()
                                onChunkDuplicate(chunk.index)
                            } else {
                                sessionChunks[chunk.index] = chunk
                                appHaptics.success()
                                onChunkScanned(chunk.index)

                                if (sessionChunks.size == chunk.total) {
                                    delay(750L.milliseconds)
                                    onChunksReady(sessionChunks.values.toList())
                                }
                            }
                        } else {
                            onTransferPayloadReady(rawValue)
                        }
                    } catch (_: Exception) {
                        onTransferPayloadReady(rawValue)
                    }
                } else {
                    onIgnored()
                }
            }
        }
    }
}
