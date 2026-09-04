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
import androidx.compose.foundation.layout.height
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
    val quizErrorMsg = stringResource(R.string.settings_drive_quiz_error)

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
                    1 -> {
                        Text(
                            text = stringResource(R.string.settings_drive_protect_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        PrimaryTabRow(selectedTabIndex = selectedProtectionTab) {
                            Tab(
                                selected = selectedProtectionTab == 0,
                                onClick = { selectedProtectionTab = 0 },
                                text = { Text(stringResource(R.string.settings_drive_method_password), style = MaterialTheme.typography.labelSmall) },
                                icon = { Icon(Icons.Filled.Password, contentDescription = null, modifier = Modifier.size(Dimensions.IconSize.small)) }
                            )
                            Tab(
                                selected = selectedProtectionTab == 1,
                                onClick = { selectedProtectionTab = 1 },
                                text = { Text(stringResource(R.string.settings_drive_method_key), style = MaterialTheme.typography.labelSmall) },
                                icon = { Icon(Icons.Filled.Key, contentDescription = null, modifier = Modifier.size(Dimensions.IconSize.small)) }
                            )
                        }

                        if (selectedProtectionTab == 0) {
                            Column(verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm)) {
                                OutlinedTextField(
                                    value = masterPasswordText,
                                    onValueChange = { masterPasswordText = it },
                                    label = { Text(stringResource(R.string.settings_drive_password_label)) },
                                    visualTransformation = if (isMasterPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    trailingIcon = {
                                        IconButton(onClick = { isMasterPasswordVisible = !isMasterPasswordVisible }) {
                                            Icon(
                                                imageVector = if (isMasterPasswordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                                contentDescription = null
                                            )
                                        }
                                    },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                OutlinedTextField(
                                    value = masterPasswordConfirmText,
                                    onValueChange = { masterPasswordConfirmText = it },
                                    label = { Text(stringResource(R.string.settings_drive_password_confirm_label)) },
                                    visualTransformation = if (isMasterPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    singleLine = true,
                                    isError = masterPasswordConfirmText.isNotEmpty() && masterPasswordText != masterPasswordConfirmText,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                if (masterPasswordText.isNotEmpty() && masterPasswordText.length < 10) {
                                    Text(
                                        text = stringResource(R.string.settings_drive_password_too_short),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                } else if (masterPasswordConfirmText.isNotEmpty() && masterPasswordText != masterPasswordConfirmText) {
                                    Text(
                                        text = stringResource(R.string.settings_drive_password_mismatch),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(Dimensions.CornerRadius.small),
                                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                                ) {
                                    Text(
                                        text = stringResource(R.string.settings_drive_password_warning),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.padding(Dimensions.Spacing.sm)
                                    )
                                }
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm)) {
                                Surface(
                                    shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(Dimensions.Spacing.sm)) {
                                        Text(
                                            text = generated64Key,
                                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(
                                        onClick = {
                                            appHaptics.copy()
                                            secureClipboard.copyToClipboard(
                                                label = "AppOPT-BackupKey",
                                                text = generated64Key,
                                                autoClearSeconds = 60
                                            )
                                            Toast.makeText(context, keyCopiedMsg, Toast.LENGTH_LONG).show()
                                        }
                                    ) {
                                        Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(Dimensions.IconSize.small))
                                        Spacer(modifier = Modifier.width(Dimensions.Spacing.xs))
                                        Text(stringResource(R.string.settings_drive_copy_60s), style = MaterialTheme.typography.labelMedium)
                                    }

                                    TextButton(
                                        onClick = { generated64Key = GoogleDriveManager.generate64DigitKey() }
                                    ) {
                                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(Dimensions.IconSize.small))
                                        Spacer(modifier = Modifier.width(Dimensions.Spacing.xs))
                                        Text(stringResource(R.string.settings_drive_key_regenerate), style = MaterialTheme.typography.labelMedium)
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(Dimensions.CornerRadius.small),
                                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                                ) {
                                    Text(
                                        text = stringResource(R.string.settings_drive_key_warning),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.padding(Dimensions.Spacing.sm)
                                    )
                                }
                            }
                        }
                    }
                    2 -> {
                        Text(
                            text = stringResource(R.string.settings_drive_emergency_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm)) {
                            Text(
                                text = stringResource(R.string.settings_drive_summary_primary_title),
                                style = MaterialTheme.typography.titleSmall
                            )

                            Surface(
                                shape = RoundedCornerShape(Dimensions.CornerRadius.small),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(Dimensions.Spacing.sm),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (selectedProtectionTab == 0) Icons.Filled.Password else Icons.Filled.Key,
                                        contentDescription = null,
                                        modifier = Modifier.size(Dimensions.IconSize.small),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(Dimensions.Spacing.xs))
                                    Text(
                                        text = if (selectedProtectionTab == 0) {
                                            "•••••••••••• (${masterPasswordText.length} caracteres)"
                                        } else {
                                            generated64Key
                                        },
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(Dimensions.Spacing.xs))

                            Text(
                                text = stringResource(R.string.settings_drive_summary_emergency_title),
                                style = MaterialTheme.typography.titleSmall
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm)
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.xs)
                                ) {
                                    generatedMnemonicWords.take(6).forEachIndexed { index, word ->
                                        Surface(
                                            shape = RoundedCornerShape(Dimensions.CornerRadius.small),
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = "${index + 1}. $word",
                                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                                modifier = Modifier.padding(horizontal = Dimensions.Spacing.sm, vertical = Dimensions.Spacing.xs)
                                            )
                                        }
                                    }
                                }
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.xs)
                                ) {
                                    generatedMnemonicWords.drop(6).forEachIndexed { index, word ->
                                        Surface(
                                            shape = RoundedCornerShape(Dimensions.CornerRadius.small),
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = "${index + 7}. $word",
                                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                                modifier = Modifier.padding(horizontal = Dimensions.Spacing.sm, vertical = Dimensions.Spacing.xs)
                                            )
                                        }
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = {
                                        appHaptics.copy()
                                        val fullPhrase = generatedMnemonicWords.joinToString(" ")
                                        secureClipboard.copyToClipboard(
                                            label = "AppOPT-MnemonicWords",
                                            text = fullPhrase,
                                            autoClearSeconds = 60
                                        )
                                        Toast.makeText(context, wordsCopiedMsg, Toast.LENGTH_LONG).show()
                                    }
                                ) {
                                    Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(Dimensions.IconSize.small))
                                    Spacer(modifier = Modifier.width(Dimensions.Spacing.xs))
                                    Text(stringResource(R.string.settings_drive_copy_60s), style = MaterialTheme.typography.labelMedium)
                                }

                                TextButton(
                                    onClick = { generatedMnemonicWords = MnemonicManager.generate12WordPhrase() }
                                ) {
                                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(Dimensions.IconSize.small))
                                    Spacer(modifier = Modifier.width(Dimensions.Spacing.xs))
                                    Text(stringResource(R.string.settings_drive_words_regenerate), style = MaterialTheme.typography.labelMedium)
                                }
                            }

                            OutlinedButton(
                                onClick = {
                                    appHaptics.click()
                                    val methodTitle = if (selectedProtectionTab == 0) {
                                        passwordLabel
                                    } else {
                                        keyLabel
                                    }
                                    val methodValue = if (selectedProtectionTab == 0) {
                                        "•••••••••••• (${masterPasswordText.length} caracteres)"
                                    } else {
                                        generated64Key
                                    }
                                    EmergencyKitPdfGenerator.printEmergencyKit(
                                        context = context,
                                        primaryMethodTitle = methodTitle,
                                        primaryMethodValue = methodValue,
                                        mnemonicWords = generatedMnemonicWords
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(Dimensions.CornerRadius.medium)
                            ) {
                                Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(Dimensions.IconSize.small))
                                Spacer(modifier = Modifier.width(Dimensions.Spacing.xs))
                                Text(stringResource(R.string.settings_drive_print_pdf), style = MaterialTheme.typography.labelMedium)
                            }

                            Text(
                                text = stringResource(R.string.settings_drive_print_pdf_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Surface(
                                shape = RoundedCornerShape(Dimensions.CornerRadius.small),
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                            ) {
                                Text(
                                    text = stringResource(R.string.settings_drive_words_warning),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(Dimensions.Spacing.sm)
                                )
                            }
                        }
                    }
                    else -> {
                        Text(
                            text = stringResource(R.string.settings_drive_quiz_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md)) {
                            quizQuestions.forEach { question ->
                                Column(verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.xs)) {
                                    Text(
                                        text = stringResource(R.string.settings_drive_quiz_question_label, question.position),
                                        style = MaterialTheme.typography.titleSmall
                                    )

                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.xs)
                                    ) {
                                        question.options.forEach { option ->
                                            val isSelected = quizSelectedAnswers[question.position] == option
                                            Surface(
                                                shape = RoundedCornerShape(Dimensions.CornerRadius.small),
                                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                                border = if (isSelected) BorderStroke(Dimensions.Stroke.regular, MaterialTheme.colorScheme.primary) else null,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        appHaptics.click()
                                                        isQuizError = false
                                                        quizSelectedAnswers = quizSelectedAnswers + (question.position to option)
                                                    }
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = Dimensions.Spacing.md, vertical = Dimensions.Spacing.sm),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(
                                                        text = option,
                                                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 1
                                                    )
                                                    if (isSelected) {
                                                        Icon(
                                                            imageVector = Icons.Filled.CheckCircle,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(Dimensions.IconSize.small),
                                                            tint = MaterialTheme.colorScheme.primary
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            if (isQuizError) {
                                Surface(
                                    shape = RoundedCornerShape(Dimensions.CornerRadius.small),
                                    color = MaterialTheme.colorScheme.errorContainer
                                ) {
                                    Text(
                                        text = stringResource(R.string.settings_drive_quiz_error),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.padding(Dimensions.Spacing.sm)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            when (step) {
                1 -> {
                    Button(
                        onClick = { step = 2 },
                        enabled = isStep1Valid,
                        shape = RoundedCornerShape(Dimensions.CornerRadius.medium)
                    ) {
                        Text(stringResource(R.string.settings_drive_next_step), style = MaterialTheme.typography.labelLarge)
                    }
                }
                2 -> {
                    Button(
                        onClick = {
                            quizQuestions = MnemonicManager.generateQuiz(generatedMnemonicWords, 2)
                            quizSelectedAnswers = emptyMap()
                            isQuizError = false
                            step = 3
                        },
                        enabled = generatedMnemonicWords.size == 12,
                        shape = RoundedCornerShape(Dimensions.CornerRadius.medium)
                    ) {
                        Text(stringResource(R.string.settings_drive_to_quiz_step), style = MaterialTheme.typography.labelLarge)
                    }
                }
                else -> {
                    Button(
                        onClick = {
                            val isAllCorrect = quizQuestions.all { quizSelectedAnswers[it.position] == it.correctWord }
                            if (!isAllCorrect) {
                                appHaptics.error()
                                isQuizError = true
                                Toast.makeText(context, quizErrorMsg, Toast.LENGTH_LONG).show()
                                return@Button
                            }

                            val primaryPassChars = if (selectedProtectionTab == 0) {
                                masterPasswordText.toCharArray()
                            } else {
                                generated64Key.toCharArray()
                            }
                            val emergencyMnemonicChars = MnemonicManager.normalizePhrase(
                                generatedMnemonicWords.joinToString(" ")
                            ).toCharArray()

                            try {
                                onProtectAndSync(primaryPassChars, emergencyMnemonicChars)
                            } finally {
                                primaryPassChars.fill('0')
                                emergencyMnemonicChars.fill('0')
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
                    text = when (step) {
                        3 -> stringResource(R.string.settings_drive_quiz_review_words)
                        2 -> stringResource(R.string.settings_drive_details_back)
                        else -> stringResource(R.string.action_cancel)
                    },
                    style = MaterialTheme.typography.labelLarge
                )
            }
        },
        modifier = modifier
    )
}
