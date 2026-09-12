package com.example.appopt.ui.screens.settings.dialogs

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.example.appopt.AuthenticatorApp
import com.example.appopt.R
import com.example.appopt.data.cloud.GoogleDriveManager
import com.example.appopt.security.MnemonicManager
import com.example.appopt.security.SecurityConfig
import com.example.appopt.ui.components.AppDialogActionButtons
import com.example.appopt.ui.screens.settings.dialogs.components.DriveProtectStepMethod
import com.example.appopt.ui.screens.settings.dialogs.components.DriveProtectStepMnemonic
import com.example.appopt.ui.screens.settings.dialogs.components.DriveProtectStepQuiz
import com.example.appopt.ui.theme.Dimensions
import com.example.appopt.ui.theme.Motion
import com.example.appopt.ui.theme.rememberAppHaptics
import com.example.appopt.util.EmergencyKitPdfGenerator

/**
 * Diálogo modal con máquina de estados de 3 pasos para la protección cifrada E2EE y configuración inicial en Google Drive.
 *
 * Pasos:
 * 1. Configuración del método principal (Contraseña maestra o Clave de 64 dígitos).
 * 2. Visualización y respaldo del Kit de Recuperación con método principal + 12 palabras semilla BIP-39 e impresión PDF.
 * 3. Verificación de retención mnemónica (*Quiz BIP-39*) antes de activar el respaldo.
 *
 * @param onProtectAndSync Callback invocado con la contraseña y frase final para cifrar e iniciar la sincronización.
 * @param onDismiss Callback invocado para cerrar el diálogo modal.
 * @param modifier Modificador de diseño Compose opcional.
 */
@Composable
fun DriveProtectDialog(
    onProtectAndSync: (primaryPass: CharArray, emergencyMnemonic: CharArray) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context: Context = LocalContext.current
    val appHaptics = rememberAppHaptics()
    val secureClipboard = remember { AuthenticatorApp.instance.secureClipboardManager }
    val clipboardState by secureClipboard.clipboardState.collectAsState()

    var step by remember { mutableIntStateOf(1) }
    var selectedProtectionTab by remember { mutableIntStateOf(0) }
    var masterPasswordText by remember { mutableStateOf("") }
    var masterPasswordConfirmText by remember { mutableStateOf("") }
    var isMasterPasswordVisible by remember { mutableStateOf(false) }
    var generated64Key by remember { mutableStateOf(GoogleDriveManager.generate64DigitKey()) }
    var isKeyVisible by remember { mutableStateOf(false) }
    var generatedMnemonicWords by remember { mutableStateOf(MnemonicManager.generate12WordPhrase()) }

    var quizQuestions by remember { mutableStateOf<List<MnemonicManager.MnemonicQuizQuestion>>(emptyList()) }
    var quizSelectedAnswers by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    var isQuizError by remember { mutableStateOf(false) }

    val keyCopyCountdown = if (clipboardState.label == SecurityConfig.CLIPBOARD_LABEL_RECOVERY_64KEY) clipboardState.remainingSeconds else 0
    val mnemonicCopyCountdown = if (clipboardState.label == SecurityConfig.CLIPBOARD_LABEL_RECOVERY_MNEMONIC) clipboardState.remainingSeconds else 0

    val keyCopiedMsg = stringResource(R.string.settings_drive_key_copied)
    val wordsCopiedMsg = stringResource(R.string.settings_drive_words_copied)
    val passwordLabel = stringResource(R.string.settings_drive_password_label)
    val keyLabel = stringResource(R.string.settings_drive_key_label)
    val quizErrorMsg = stringResource(R.string.settings_drive_quiz_description)

    val isPasswordValid = masterPasswordText.length >= 10 && masterPasswordText == masterPasswordConfirmText
    val isStep1Valid = if (selectedProtectionTab == 0) isPasswordValid else generated64Key.isNotBlank()
    val isQuizAnswered = quizQuestions.isNotEmpty() && quizSelectedAnswers.size == quizQuestions.size

    val animatedProgress by animateFloatAsState(
        targetValue = step / 3f,
        animationSpec = tween(
            durationMillis = Motion.Duration.MEDIUM,
            easing = Motion.EasingCurve.Standard
        ),
        label = "driveProtectProgress"
    )

    AlertDialog(
        onDismissRequest = {
            if (step > 1) {
                step -= 1
            } else {
                onDismiss()
            }
        },
        shape = RoundedCornerShape(Dimensions.CornerRadius.large),
        confirmButton = {},
        dismissButton = null,
        title = null,
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md)
            ) {
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(Dimensions.Spacing.xs)
                        .clip(RoundedCornerShape(Dimensions.CornerRadius.pill)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )

                AnimatedContent(
                    targetState = step,
                    transitionSpec = { Motion.Spec.dialogStepContentTransform() },
                    contentAlignment = Alignment.TopStart,
                    modifier = Modifier.fillMaxWidth(),
                    label = "driveProtectStepTransition"
                ) { currentStep ->
                    when (currentStep) {
                        1 -> {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md)
                            ) {
                                Text(
                                    text = stringResource(R.string.settings_drive_protect_step1_title),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                DriveProtectStepMethod(
                                    selectedTab = selectedProtectionTab,
                                    onTabSelected = { selectedProtectionTab = it },
                                    masterPasswordText = masterPasswordText,
                                    onMasterPasswordChange = { masterPasswordText = it },
                                    masterPasswordConfirmText = masterPasswordConfirmText,
                                    onMasterPasswordConfirmChange = { masterPasswordConfirmText = it },
                                    isMasterPasswordVisible = isMasterPasswordVisible,
                                    onTogglePasswordVisibility = { isMasterPasswordVisible = !isMasterPasswordVisible },
                                    generated64Key = generated64Key,
                                    isKeyVisible = isKeyVisible,
                                    onToggleKeyVisibility = { isKeyVisible = !isKeyVisible },
                                    copyCountdown = keyCopyCountdown,
                                    onRegenerateKey = {
                                        appHaptics.click()
                                        generated64Key = GoogleDriveManager.generate64DigitKey()
                                    },
                                    onCopyKey = {
                                        secureClipboard.copySecurelyWithFeedback(
                                            context = context,
                                            label = SecurityConfig.CLIPBOARD_LABEL_RECOVERY_64KEY,
                                            text = generated64Key,
                                            feedbackMessage = keyCopiedMsg,
                                            onHaptics = { appHaptics.copy() },
                                            autoClearSeconds = SecurityConfig.CLIPBOARD_RECOVERY_KEY_AUTO_CLEAR_SECONDS
                                        )
                                    }
                                )

                                AppDialogActionButtons(
                                    confirmText = stringResource(R.string.settings_drive_next_step),
                                    onConfirm = { step = 2 },
                                    dismissText = stringResource(R.string.action_close),
                                    onDismiss = onDismiss,
                                    confirmEnabled = isStep1Valid
                                )
                            }
                        }
                        2 -> {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md)
                            ) {
                                Text(
                                    text = stringResource(R.string.settings_drive_protect_step2_title),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                DriveProtectStepMnemonic(
                                    mnemonicWords = generatedMnemonicWords,
                                    primaryMethodLabel = if (selectedProtectionTab == 0) passwordLabel else keyLabel,
                                    primaryMethodValue = if (selectedProtectionTab == 0) masterPasswordText else generated64Key,
                                    isPasswordMethod = selectedProtectionTab == 0,
                                    copyCountdown = mnemonicCopyCountdown,
                                    onCopyWords = {
                                        secureClipboard.copySecurelyWithFeedback(
                                            context = context,
                                            label = SecurityConfig.CLIPBOARD_LABEL_RECOVERY_MNEMONIC,
                                            text = generatedMnemonicWords.joinToString(" "),
                                            feedbackMessage = wordsCopiedMsg,
                                            onHaptics = { appHaptics.copy() },
                                            autoClearSeconds = SecurityConfig.CLIPBOARD_RECOVERY_KEY_AUTO_CLEAR_SECONDS
                                        )
                                    },
                                    onPrintPdf = {
                                        appHaptics.click()
                                        val primaryTitle = if (selectedProtectionTab == 0) passwordLabel else keyLabel
                                        val primaryVal = if (selectedProtectionTab == 0) {
                                            masterPasswordText
                                        } else {
                                            generated64Key
                                        }
                                        EmergencyKitPdfGenerator.printEmergencyKit(
                                            context = context,
                                            primaryMethodTitle = primaryTitle,
                                            primaryMethodValue = primaryVal,
                                            mnemonicWords = generatedMnemonicWords
                                        )
                                    }
                                )

                                AppDialogActionButtons(
                                    confirmText = stringResource(R.string.settings_drive_to_quiz_step),
                                    onConfirm = {
                                        quizQuestions = MnemonicManager.generateQuiz(generatedMnemonicWords)
                                        quizSelectedAnswers = emptyMap()
                                        isQuizError = false
                                        step = 3
                                    },
                                    dismissText = stringResource(R.string.settings_drive_details_back),
                                    onDismiss = { step = 1 }
                                )
                            }
                        }
                        3 -> {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md)
                            ) {
                                Text(
                                    text = stringResource(R.string.settings_drive_quiz_title),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                DriveProtectStepQuiz(
                                    questions = quizQuestions,
                                    selectedAnswers = quizSelectedAnswers,
                                    isError = isQuizError,
                                    onSelectAnswer = { questionIdx, option ->
                                        appHaptics.click()
                                        isQuizError = false
                                        quizSelectedAnswers = quizSelectedAnswers + (questionIdx to option)
                                    }
                                )

                                AppDialogActionButtons(
                                    confirmText = stringResource(R.string.settings_drive_encrypt_and_sync),
                                    onConfirm = {
                                        val allCorrect = quizQuestions.mapIndexed { idx, q ->
                                            quizSelectedAnswers[idx] == q.correctWord
                                        }.all { it }

                                        if (allCorrect) {
                                            appHaptics.success()
                                            val primaryPass = if (selectedProtectionTab == 0) {
                                                masterPasswordText.toCharArray()
                                            } else {
                                                generated64Key.toCharArray()
                                            }
                                            val mnemonicPass = generatedMnemonicWords.joinToString(" ").toCharArray()
                                            try {
                                                onProtectAndSync(primaryPass, mnemonicPass)
                                            } finally {
                                                primaryPass.fill('0')
                                                mnemonicPass.fill('0')
                                                masterPasswordText = ""
                                                masterPasswordConfirmText = ""
                                                generated64Key = ""
                                            }
                                        } else {
                                            appHaptics.error()
                                            isQuizError = true
                                            Toast.makeText(context, quizErrorMsg, Toast.LENGTH_LONG).show()
                                        }
                                    },
                                    dismissText = stringResource(R.string.settings_drive_details_back),
                                    onDismiss = { step = 2 },
                                    confirmEnabled = isQuizAnswered
                                )
                            }
                        }
                    }
                }
            }
        },
        modifier = modifier
    )
}
