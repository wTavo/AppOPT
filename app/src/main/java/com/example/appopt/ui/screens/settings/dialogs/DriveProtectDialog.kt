package com.example.appopt.ui.screens.settings.dialogs

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.example.appopt.AuthenticatorApp
import com.example.appopt.R
import com.example.appopt.data.cloud.GoogleDriveManager
import com.example.appopt.security.MnemonicManager
import com.example.appopt.security.SecurityConfig
import com.example.appopt.ui.theme.Dimensions
import com.example.appopt.ui.theme.rememberAppHaptics
import com.example.appopt.util.EmergencyKitPdfGenerator

/**
 * Diálogo modal con máquina de estados de 3 pasos para la protección cifrada E2EE y configuración inicial en Google Drive.
 *
 * Pasos:
 * 1. Configuración del método principal (Contraseña maestra o Clave de 64 dígitos).
 * 2. Visualización y respaldo del Kit de Recuperación con 12 palabras semilla BIP-39 e impresión PDF.
 * 3. Cuestionario interactivo de verificación para garantizar que el usuario respaldó las palabras.
 *
 * @param onProtectAndSync Callback invocado tras superar el cuestionario con los buffers de clave primaria y frase mnemónica.
 * @param onDismiss Callback invocado para cancelar el flujo.
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

    var step by remember { mutableIntStateOf(1) }
    var selectedProtectionTab by remember { mutableIntStateOf(0) }
    var masterPasswordText by remember { mutableStateOf("") }
    var masterPasswordConfirmText by remember { mutableStateOf("") }
    var isMasterPasswordVisible by remember { mutableStateOf(false) }
    var generated64Key by remember { mutableStateOf(GoogleDriveManager.generate64DigitKey()) }
    var generatedMnemonicWords by remember { mutableStateOf(MnemonicManager.generate12WordPhrase()) }

    var quizQuestions by remember { mutableStateOf<List<MnemonicManager.MnemonicQuizQuestion>>(emptyList()) }
    var quizSelectedAnswers by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    var isQuizError by remember { mutableStateOf(false) }

    val keyCopiedMsg = stringResource(R.string.settings_drive_key_copied)
    val wordsCopiedMsg = stringResource(R.string.settings_drive_words_copied)
    val passwordLabel = stringResource(R.string.settings_drive_password_label)
    val keyLabel = stringResource(R.string.settings_drive_key_label)
    val quizErrorMsg = stringResource(R.string.settings_drive_quiz_description)

    val isPasswordValid = masterPasswordText.length >= 10 && masterPasswordText == masterPasswordConfirmText
    val isStep1Valid = if (selectedProtectionTab == 0) isPasswordValid else generated64Key.isNotBlank()
    val isQuizAnswered = quizQuestions.isNotEmpty() && quizSelectedAnswers.size == quizQuestions.size

    AlertDialog(
        onDismissRequest = {
            when (step) {
                3 -> step = 2
                2 -> step = 1
                else -> onDismiss()
            }
        },
        shape = RoundedCornerShape(Dimensions.CornerRadius.large),
        title = {
            Text(
                text = when (step) {
                    1 -> stringResource(R.string.settings_drive_protect_step1_title)
                    2 -> stringResource(R.string.settings_drive_protect_step2_title)
                    else -> stringResource(R.string.settings_drive_quiz_title)
                },
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md)
            ) {
                when (step) {
                    1 -> DriveProtectStepMethod(
                        selectedTab = selectedProtectionTab,
                        onTabSelected = { selectedProtectionTab = it },
                        masterPasswordText = masterPasswordText,
                        onMasterPasswordChange = { masterPasswordText = it },
                        masterPasswordConfirmText = masterPasswordConfirmText,
                        onMasterPasswordConfirmChange = { masterPasswordConfirmText = it },
                        isMasterPasswordVisible = isMasterPasswordVisible,
                        onTogglePasswordVisibility = { isMasterPasswordVisible = !isMasterPasswordVisible },
                        generated64Key = generated64Key,
                        onRegenerateKey = {
                            appHaptics.click()
                            generated64Key = GoogleDriveManager.generate64DigitKey()
                        },
                        onCopyKey = {
                            appHaptics.copy()
                            secureClipboard.copyToClipboard(
                                label = "Google Drive 64-Key",
                                text = generated64Key,
                                autoClearSeconds = SecurityConfig.CLIPBOARD_RECOVERY_KEY_AUTO_CLEAR_SECONDS
                            )
                            Toast.makeText(context, keyCopiedMsg, Toast.LENGTH_SHORT).show()
                        }
                    )
                    2 -> DriveProtectStepEmergencyKit(
                        mnemonicWords = generatedMnemonicWords,
                        onCopyWords = {
                            appHaptics.copy()
                            secureClipboard.copyToClipboard(
                                label = "12 Words Emergency Kit",
                                text = generatedMnemonicWords.joinToString(" "),
                                autoClearSeconds = SecurityConfig.CLIPBOARD_RECOVERY_KEY_AUTO_CLEAR_SECONDS
                            )
                            Toast.makeText(context, wordsCopiedMsg, Toast.LENGTH_SHORT).show()
                        },
                        onPrintPdf = {
                            appHaptics.click()
                            val primaryTitle = if (selectedProtectionTab == 0) passwordLabel else keyLabel
                            val primaryVal = if (selectedProtectionTab == 0) masterPasswordText else generated64Key
                            EmergencyKitPdfGenerator.printEmergencyKit(
                                context = context,
                                primaryMethodTitle = primaryTitle,
                                primaryMethodValue = primaryVal,
                                mnemonicWords = generatedMnemonicWords
                            )
                        }
                    )
                    3 -> DriveProtectStepQuiz(
                        questions = quizQuestions,
                        selectedAnswers = quizSelectedAnswers,
                        isError = isQuizError,
                        onSelectAnswer = { questionIdx, option ->
                            appHaptics.click()
                            isQuizError = false
                            quizSelectedAnswers = quizSelectedAnswers + (questionIdx to option)
                        }
                    )
                }
            }
        },
        confirmButton = {
            when (step) {
                1 -> {
                    Button(
                        onClick = {
                            appHaptics.click()
                            step = 2
                        },
                        enabled = isStep1Valid,
                        shape = RoundedCornerShape(Dimensions.CornerRadius.medium)
                    ) {
                        Text(stringResource(R.string.settings_drive_next_step), style = MaterialTheme.typography.labelLarge)
                    }
                }
                2 -> {
                    Button(
                        onClick = {
                            appHaptics.click()
                            quizQuestions = MnemonicManager.generateQuiz(generatedMnemonicWords)
                            quizSelectedAnswers = emptyMap()
                            isQuizError = false
                            step = 3
                        },
                        shape = RoundedCornerShape(Dimensions.CornerRadius.medium)
                    ) {
                        Text(stringResource(R.string.settings_drive_to_quiz_step), style = MaterialTheme.typography.labelLarge)
                    }
                }
                3 -> {
                    Button(
                        onClick = {
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
                        enabled = isQuizAnswered,
                        shape = RoundedCornerShape(Dimensions.CornerRadius.medium)
                    ) {
                        Text(stringResource(R.string.settings_drive_encrypt_and_sync), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    when (step) {
                        3 -> step = 2
                        2 -> step = 1
                        else -> onDismiss()
                    }
                }
            ) {
                Text(
                    text = if (step == 1) stringResource(R.string.action_cancel) else stringResource(R.string.settings_drive_details_back),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        },
        modifier = modifier
    )
}

/**
 * Sub-componente para el Paso 1: Selección y configuración del método de protección principal.
 */
@Composable
private fun DriveProtectStepMethod(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    masterPasswordText: String,
    onMasterPasswordChange: (String) -> Unit,
    masterPasswordConfirmText: String,
    onMasterPasswordConfirmChange: (String) -> Unit,
    isMasterPasswordVisible: Boolean,
    onTogglePasswordVisibility: () -> Unit,
    generated64Key: String,
    onRegenerateKey: () -> Unit,
    onCopyKey: () -> Unit
) {
    Text(
        text = stringResource(R.string.settings_drive_protect_description),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    PrimaryTabRow(selectedTabIndex = selectedTab) {
        Tab(
            selected = selectedTab == 0,
            onClick = { onTabSelected(0) },
            text = { Text(stringResource(R.string.settings_drive_method_password), style = MaterialTheme.typography.labelSmall) },
            icon = { Icon(Icons.Filled.Password, contentDescription = null, modifier = Modifier.size(Dimensions.IconSize.small)) }
        )
        Tab(
            selected = selectedTab == 1,
            onClick = { onTabSelected(1) },
            text = { Text(stringResource(R.string.settings_drive_method_key), style = MaterialTheme.typography.labelSmall) },
            icon = { Icon(Icons.Filled.Key, contentDescription = null, modifier = Modifier.size(Dimensions.IconSize.small)) }
        )
    }

    if (selectedTab == 0) {
        OutlinedTextField(
            value = masterPasswordText,
            onValueChange = onMasterPasswordChange,
            label = { Text(stringResource(R.string.settings_drive_password_label)) },
            visualTransformation = if (isMasterPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = onTogglePasswordVisibility) {
                    Icon(
                        imageVector = if (isMasterPasswordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = null
                    )
                }
            },
            shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = masterPasswordConfirmText,
            onValueChange = onMasterPasswordConfirmChange,
            label = { Text(stringResource(R.string.settings_drive_password_confirm_label)) },
            visualTransformation = if (isMasterPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            isError = masterPasswordConfirmText.isNotEmpty() && masterPasswordText != masterPasswordConfirmText,
            shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    } else {
        Surface(
            shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            border = BorderStroke(Dimensions.Stroke.thin, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(Dimensions.Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm)
            ) {
                Text(
                    text = stringResource(R.string.settings_drive_key_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = generated64Key.chunked(16).joinToString("\n"),
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(onClick = onRegenerateKey) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.settings_drive_key_regenerate))
                    }
                    IconButton(onClick = onCopyKey) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = stringResource(R.string.settings_drive_copy_60s))
                    }
                }
            }
        }
    }
}

/**
 * Sub-componente para el Paso 2: Visualización y exportación/impresión del Kit de Emergencia BIP-39.
 */
@Composable
private fun DriveProtectStepEmergencyKit(
    mnemonicWords: List<String>,
    onCopyWords: () -> Unit,
    onPrintPdf: () -> Unit
) {
    Text(
        text = stringResource(R.string.settings_drive_emergency_description),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Surface(
        shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(Dimensions.Stroke.thin, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(Dimensions.Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.xs)
        ) {
            val chunked = mnemonicWords.chunked(3)
            chunked.forEachIndexed { rowIdx, rowWords ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    rowWords.forEachIndexed { colIdx, word ->
                        val wordNumber = rowIdx * 3 + colIdx + 1
                        Text(
                            text = "$wordNumber. $word",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm)
    ) {
        OutlinedButton(
            onClick = onCopyWords,
            shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(Dimensions.IconSize.small))
            Spacer(modifier = Modifier.width(Dimensions.Spacing.xs))
            Text(stringResource(R.string.settings_drive_copy_60s), style = MaterialTheme.typography.labelMedium)
        }

        OutlinedButton(
            onClick = onPrintPdf,
            shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(Dimensions.IconSize.small))
            Spacer(modifier = Modifier.width(Dimensions.Spacing.xs))
            Text(stringResource(R.string.settings_drive_print_pdf), style = MaterialTheme.typography.labelMedium)
        }
    }
}

/**
 * Sub-componente para el Paso 3: Cuestionario de verificación de palabras mnemónicas.
 */
@Composable
private fun DriveProtectStepQuiz(
    questions: List<MnemonicManager.MnemonicQuizQuestion>,
    selectedAnswers: Map<Int, String>,
    isError: Boolean,
    onSelectAnswer: (Int, String) -> Unit
) {
    Text(
        text = stringResource(R.string.settings_drive_quiz_description),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Column(verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm)) {
        questions.forEachIndexed { qIdx, q ->
            Surface(
                shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                border = BorderStroke(
                    Dimensions.Stroke.thin,
                    if (isError) MaterialTheme.colorScheme.error.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(Dimensions.Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.xs)
                ) {
                    Text(
                        text = stringResource(R.string.settings_drive_quiz_question_label, q.position),
                        style = MaterialTheme.typography.titleSmall
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.xs)
                    ) {
                        q.options.forEach { option ->
                            val isSelected = selectedAnswers[qIdx] == option
                            Surface(
                                shape = RoundedCornerShape(Dimensions.CornerRadius.small),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                border = BorderStroke(
                                    Dimensions.Stroke.thin,
                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onSelectAnswer(qIdx, option) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = Dimensions.Spacing.xs, horizontal = Dimensions.Spacing.xs),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            Icons.Filled.CheckCircle,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(Dimensions.IconSize.small / 1.4f)
                                        )
                                        Spacer(modifier = Modifier.width(Dimensions.Spacing.xs / 2))
                                    }
                                    Text(
                                        text = option,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
