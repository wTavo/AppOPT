package com.example.appopt.ui.screens.scan

import androidx.compose.animation.AnimatedContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.appopt.AuthenticatorApp
import com.example.appopt.R
import com.example.appopt.domain.model.ParsedAccountPreview
import com.example.appopt.domain.totp.OtpUriParser
import com.example.appopt.domain.totp.ParsedOtpData
import com.example.appopt.security.CryptoManager
import com.example.appopt.security.SecurityConfig
import com.example.appopt.security.TransferCrypto
import com.example.appopt.security.TransferQrChunk
import com.example.appopt.ui.components.AppModalDialog
import com.example.appopt.ui.components.LocalModalDismissHandler
import com.example.appopt.ui.screens.scan.components.QrScanCameraStep
import com.example.appopt.ui.screens.scan.components.QrScanSingleOtpStep
import com.example.appopt.ui.screens.scan.components.QrScanTransferPinStep
import com.example.appopt.ui.screens.scan.components.QrScanTransferSelectStep
import com.example.appopt.ui.theme.Motion
import com.example.appopt.ui.theme.rememberAppHaptics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

private enum class QrScannerStep {
    CAMERA,
    CONFIRM_SINGLE_OTP,
    TRANSFER_PIN,
    TRANSFER_SELECT_ACCOUNTS
}

/**
 * Modos de filtrado y operación del escáner de códigos QR.
 */
enum class QrScannerMode {
    /**
     * Modo exclusivo para alta de servicios 2FA individuales (`otpauth://`).
     * Ignora silenciosamente transferencias por lotes o formatos incompatibles.
     */
    SINGLE_ACCOUNT,

    /**
     * Modo exclusivo para importación y transferencia de cuentas por lotes cifrados (`appopt-transfer://`).
     * Ignora silenciosamente códigos OTP individuales estándar.
     */
    TRANSFER_MIGRATION
}

/**
 * Diálogo modal para escaneo de códigos QR con filtrado estricto por modo de operación.
 *
 * Estándares aplicados:
 * - Directiva 14: Máquina de estados monolítica dentro de [AppModalDialog] con navegación defensiva ([onBackStep]).
 * - Directiva 14: Estructura tripartita inmutable (Cabecera y Botones fijos con cuerpo central scrolleable).
 * - Directiva 22: Idempotencia y feedback háptico con [com.example.appopt.ui.components.AppAnimatedButton] y [com.example.appopt.ui.theme.AppHaptics].
 * - Directiva 9: Zeroización de memoria tras procesar claves criptográficas.
 * - Directiva 28: Inicio bajo demanda y filtrado silencioso según [mode].
 * - Prevención Antifraude: Verificación previa con detección de homóglifos, duplicados y consejos antiphishing.
 *
 * @param onDismiss Callback para cerrar el diálogo.
 * @param modifier Modificador de diseño Compose.
 * @param mode Modo de filtrado y operación del escáner ([QrScannerMode.SINGLE_ACCOUNT] o [QrScannerMode.TRANSFER_MIGRATION]).
 * @param onScanSuccess Callback invocado tras la importación exitosa de una o más cuentas.
 * @param title Título del diálogo.
 */
@Composable
fun QrScannerDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    mode: QrScannerMode = QrScannerMode.SINGLE_ACCOUNT,
    onScanSuccess: () -> Unit = {},
    title: String = if (mode == QrScannerMode.TRANSFER_MIGRATION) {
        stringResource(R.string.scan_import_title)
    } else {
        stringResource(R.string.scan_title)
    }
) {
    val appHaptics = rememberAppHaptics()
    val scope = rememberCoroutineScope()
    val repository = remember { AuthenticatorApp.instance.accountRepository }
    val existingAccounts by repository.getAccounts().collectAsStateWithLifecycle(initialValue = emptyList())

    val importAllAlreadyExistErrorText = stringResource(R.string.scan_import_all_already_exist)
    val pinMaxAttemptsErrorText = stringResource(R.string.scan_transfer_pin_error_max_attempts)
    val pinIncorrectAttemptsFormat = stringResource(R.string.scan_transfer_pin_error_incorrect_attempts)
    val qrExpiredErrorText = stringResource(R.string.scan_transfer_error_expired)

    var currentStep by remember { mutableStateOf(QrScannerStep.CAMERA) }

    var isCameraActive by remember { mutableStateOf(false) }
    var isProcessingBarcode by remember { mutableStateOf(false) }
    var lastScannedPayload by remember { mutableStateOf<String?>(null) }

    val blockedSessionIds = remember { mutableSetOf<Long>() }
    val blockedPayloadFingerprints = remember { mutableSetOf<Int>() }

    val sessionChunks = remember { mutableStateMapOf<Int, TransferQrChunk>() }
    var currentSessionId by remember { mutableLongStateOf(0L) }
    var totalExpectedChunks by remember { mutableIntStateOf(0) }
    var lastCapturedChunkIndex by remember { mutableIntStateOf(0) }
    var duplicateChunkIndex by remember { mutableStateOf<Int?>(null) }
    var capturedChunkAnimationIndex by remember { mutableStateOf<Int?>(null) }

    var pendingSingleOtp by remember { mutableStateOf<ParsedOtpData?>(null) }
    var pendingEncryptedPayload by remember { mutableStateOf<String?>(null) }
    var pendingEncryptedChunks by remember { mutableStateOf<List<TransferQrChunk>?>(null) }
    var transferPinInput by remember { mutableStateOf("") }
    var pinErrorMessage by remember { mutableStateOf<String?>(null) }
    var failedPinAttempts by remember { mutableIntStateOf(0) }
    var isVerifyingPin by remember { mutableStateOf(false) }

    var parsedAccounts by remember { mutableStateOf<List<ParsedAccountPreview>>(emptyList()) }
    var selectedAccountIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    val resetToCamera: () -> Unit = {
        pendingSingleOtp?.secretBytes?.let { CryptoManager.zeroize(it) }
        pendingSingleOtp = null
        pendingEncryptedPayload = null
        pendingEncryptedChunks = null
        sessionChunks.clear()
        currentSessionId = 0L
        totalExpectedChunks = 0
        transferPinInput = ""
        pinErrorMessage = null
        failedPinAttempts = 0
        isVerifyingPin = false
        parsedAccounts = emptyList()
        selectedAccountIds = emptySet()
        lastScannedPayload = null
        lastCapturedChunkIndex = 0
        duplicateChunkIndex = null
        capturedChunkAnimationIndex = null
        isProcessingBarcode = false
        currentStep = QrScannerStep.CAMERA
    }

    val handleDismiss: () -> Unit = {
        resetToCamera()
        onDismiss()
    }

    AppModalDialog(
        onDismissRequest = handleDismiss,
        onBackStep = {
            when (currentStep) {
                QrScannerStep.CONFIRM_SINGLE_OTP -> {
                    resetToCamera()
                    true
                }
                QrScannerStep.TRANSFER_SELECT_ACCOUNTS, QrScannerStep.TRANSFER_PIN -> {
                    if (!isVerifyingPin) {
                        resetToCamera()
                        true
                    } else {
                        false
                    }
                }
                QrScannerStep.CAMERA -> false
            }
        },
        modifier = modifier
    ) {
        val modalDismissHandler = LocalModalDismissHandler.current

        AnimatedContent(
            targetState = currentStep,
            transitionSpec = { Motion.Spec.dialogStepContentTransform() },
            label = "QrScannerStepTransition"
        ) { step ->
            when (step) {
                QrScannerStep.CAMERA -> {
                    QrScanCameraStep(
                        title = title,
                        mode = mode,
                        isCameraActive = isCameraActive,
                        isProcessingBarcode = isProcessingBarcode,
                        sessionChunks = sessionChunks,
                        totalExpectedChunks = totalExpectedChunks,
                        duplicateChunkIndex = duplicateChunkIndex,
                        capturedChunkAnimationIndex = capturedChunkAnimationIndex,
                        onCameraActiveChange = { isCameraActive = it },
                        onBarcodeScanned = { rawValue ->
                            if (rawValue != lastScannedPayload) {
                                lastScannedPayload = rawValue
                                isProcessingBarcode = true

                                scope.launch {
                                    handleScannedBarcode(
                                        rawValue = rawValue,
                                        mode = mode,
                                        appHaptics = appHaptics,
                                        blockedSessionIds = blockedSessionIds,
                                        blockedPayloadFingerprints = blockedPayloadFingerprints,
                                        sessionChunks = sessionChunks,
                                        currentSessionId = currentSessionId,
                                        onSessionUpdated = { newSessionId, totalChunks ->
                                            currentSessionId = newSessionId
                                            totalExpectedChunks = totalChunks
                                            lastCapturedChunkIndex = 0
                                            duplicateChunkIndex = null
                                            capturedChunkAnimationIndex = null
                                        },
                                        onChunkScanned = { chunkIndex ->
                                            lastCapturedChunkIndex = chunkIndex
                                            duplicateChunkIndex = null
                                            capturedChunkAnimationIndex = chunkIndex
                                            scope.launch {
                                                delay(850L.milliseconds)
                                                if (capturedChunkAnimationIndex == chunkIndex) {
                                                    capturedChunkAnimationIndex = null
                                                }
                                            }
                                            scope.launch {
                                                delay(300L.milliseconds)
                                                isProcessingBarcode = false
                                            }
                                        },
                                        onChunkDuplicate = { chunkIndex ->
                                            duplicateChunkIndex = chunkIndex
                                            scope.launch {
                                                delay(500L.milliseconds)
                                                isProcessingBarcode = false
                                            }
                                        },
                                        onSingleOtpScanned = { parsedOtp ->
                                            appHaptics.dragTick()
                                            pendingSingleOtp = parsedOtp
                                            currentStep = QrScannerStep.CONFIRM_SINGLE_OTP
                                        },
                                        onTransferPayloadReady = { payloadToDecrypt ->
                                            pendingEncryptedPayload = payloadToDecrypt
                                            pendingEncryptedChunks = null
                                            transferPinInput = ""
                                            pinErrorMessage = null
                                            failedPinAttempts = 0
                                            currentStep = QrScannerStep.TRANSFER_PIN
                                        },
                                        onChunksReady = { chunksToDecrypt ->
                                            pendingEncryptedChunks = chunksToDecrypt
                                            pendingEncryptedPayload = null
                                            transferPinInput = ""
                                            pinErrorMessage = null
                                            failedPinAttempts = 0
                                            currentStep = QrScannerStep.TRANSFER_PIN
                                        },
                                        onIgnored = {
                                            scope.launch {
                                                delay(200L.milliseconds)
                                                isProcessingBarcode = false
                                            }
                                        },
                                        onError = {
                                            appHaptics.error()
                                            delay(Motion.Duration.FEEDBACK_TOAST.toLong().milliseconds)
                                            isProcessingBarcode = false
                                        }
                                    )
                                }
                            }
                        },
                        onClose = handleDismiss
                    )
                }

                QrScannerStep.CONFIRM_SINGLE_OTP -> {
                    val otp = pendingSingleOtp
                    if (otp != null) {
                        QrScanSingleOtpStep(
                            otp = otp,
                            existingAccounts = existingAccounts,
                            onBack = {
                                otp.secretBytes.let { CryptoManager.zeroize(it) }
                                pendingSingleOtp = null
                                isProcessingBarcode = false
                                lastScannedPayload = null
                                currentStep = QrScannerStep.CAMERA
                            },
                            onSave = {
                                repository.saveAccount(
                                    issuer = otp.issuer,
                                    accountName = otp.accountName,
                                    secretBytes = otp.secretBytes,
                                    algorithm = otp.algorithm,
                                    digits = otp.digits,
                                    period = otp.period,
                                    type = otp.type,
                                    counter = otp.counter
                                )
                                CryptoManager.zeroize(otp.secretBytes)
                                true
                            },
                            onSaveConfirmed = {
                                appHaptics.success()
                                onScanSuccess()
                                if (modalDismissHandler != null) {
                                    modalDismissHandler()
                                } else {
                                    onDismiss()
                                }
                            }
                        )
                    }
                }

                QrScannerStep.TRANSFER_PIN -> {
                    QrScanTransferPinStep(
                        pin = transferPinInput,
                        onPinChange = {
                            transferPinInput = it
                            pinErrorMessage = null
                        },
                        errorMessage = pinErrorMessage,
                        isVerifyingPin = isVerifyingPin,
                        onBack = {
                            if (!isVerifyingPin) {
                                resetToCamera()
                            }
                        },
                        onConfirm = {
                            if (transferPinInput.length !in setOf(SecurityConfig.TRANSFER_QR_PIN_LENGTH, SecurityConfig.TRANSFER_KEY_LENGTH)) {
                                return@QrScanTransferPinStep
                            }

                            isVerifyingPin = true
                            val pinChars = transferPinInput.toCharArray()

                            scope.launch {
                                val result = try {
                                    if (pendingEncryptedChunks != null) {
                                        TransferCrypto.decryptAssembledChunks(
                                            pendingEncryptedChunks.orEmpty(),
                                            pinChars
                                        )
                                    } else if (pendingEncryptedPayload != null) {
                                        TransferCrypto.decryptTransferPayload(
                                            pendingEncryptedPayload.orEmpty(),
                                            pinChars
                                        )
                                    } else {
                                        Result.failure(IllegalArgumentException())
                                    }
                                } finally {
                                    CryptoManager.zeroize(pinChars)
                                }

                                result.fold(
                                    onSuccess = { decryptedJson ->
                                        val previews = repository.parseAccountsForPreview(decryptedJson)
                                        if (previews.isNotEmpty()) {
                                            parsedAccounts = previews
                                            selectedAccountIds = previews.map { it.id }.toSet()
                                            currentStep = QrScannerStep.TRANSFER_SELECT_ACCOUNTS
                                        } else {
                                            pinErrorMessage = importAllAlreadyExistErrorText
                                            appHaptics.error()
                                        }
                                        isVerifyingPin = false
                                    },
                                    onFailure = { error ->
                                        if (error is TransferCrypto.ExpiredTransferException) {
                                            pinErrorMessage = qrExpiredErrorText
                                            appHaptics.error()
                                            delay(Motion.Duration.FEEDBACK_TOAST.toLong().milliseconds)
                                            resetToCamera()
                                        } else {
                                            failedPinAttempts++
                                            val remaining = SecurityConfig.TRANSFER_QR_MAX_PIN_ATTEMPTS - failedPinAttempts

                                            if (remaining <= 0) {
                                                if (currentSessionId != 0L) {
                                                    blockedSessionIds.add(currentSessionId)
                                                }
                                                pendingEncryptedPayload?.let { blockedPayloadFingerprints.add(it.hashCode()) }
                                                pinErrorMessage = pinMaxAttemptsErrorText
                                                appHaptics.error()
                                                delay(Motion.Duration.FEEDBACK_TOAST.toLong().milliseconds)
                                                resetToCamera()
                                            } else {
                                                pinErrorMessage = String.format(pinIncorrectAttemptsFormat, remaining)
                                                appHaptics.error()
                                            }
                                        }
                                        isVerifyingPin = false
                                    }
                                )
                            }
                        }
                    )
                }

                QrScannerStep.TRANSFER_SELECT_ACCOUNTS -> {
                    QrScanTransferSelectStep(
                        parsedAccounts = parsedAccounts,
                        selectedAccountIds = selectedAccountIds,
                        onToggleAccount = { id ->
                            selectedAccountIds = if (id in selectedAccountIds) {
                                selectedAccountIds - id
                            } else {
                                selectedAccountIds + id
                            }
                        },
                        onSelectAll = {
                            selectedAccountIds = parsedAccounts.map { it.id }.toSet()
                        },
                        onDeselectAll = {
                            selectedAccountIds = emptySet()
                        },
                        onBack = resetToCamera,
                        onImportAccounts = {
                            val accountsToImport = parsedAccounts.filter { it.id in selectedAccountIds }
                            repository.importSelectedAccounts(accountsToImport)
                            true
                        },
                        onImportConfirmed = {
                            onScanSuccess()
                            if (modalDismissHandler != null) {
                                modalDismissHandler()
                            } else {
                                onDismiss()
                            }
                        }
                    )
                }
            }
        }
    }
}

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
private suspend fun handleScannedBarcode(
    rawValue: String,
    mode: QrScannerMode,
    appHaptics: com.example.appopt.ui.theme.AppHaptics,
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
