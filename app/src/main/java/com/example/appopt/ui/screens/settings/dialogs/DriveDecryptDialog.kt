package com.example.appopt.ui.screens.settings.dialogs
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.imePadding

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalContext
import com.example.appopt.AuthenticatorApp
import com.example.appopt.R
import com.example.appopt.data.cloud.CloudVaultKeyStore
import com.example.appopt.data.cloud.DriveBackupItem
import com.example.appopt.data.cloud.GoogleDriveManager
import com.example.appopt.domain.model.ParsedAccountPreview
import com.example.appopt.security.MnemonicManager
import com.example.appopt.ui.components.AccountImportSelectionList
import com.example.appopt.ui.components.AppAnimatedButton
import com.example.appopt.ui.components.AppDialogActionButtons
import com.example.appopt.ui.components.AppModalDialog
import com.example.appopt.ui.screens.settings.dialogs.components.DriveBackupDecryptForm
import com.example.appopt.ui.theme.Dimensions
import com.example.appopt.ui.theme.Motion
import com.example.appopt.ui.theme.rememberAppHaptics
import kotlinx.coroutines.launch

private enum class DriveDecryptStep {
    DECRYPT,
    SELECT_ACCOUNTS
}

/**
 * Diálogo modal para el descifrado y restauración directa del respaldo más reciente en Google Drive (Directivas 7, 14, 22 y 23).
 *
 * @param onRestore Callback ejecutado con la clave de descifrado ingresada en [CharArray].
 * @param onDismiss Callback ejecutado para descartar y cerrar el diálogo.
 * @param modifier Modificador de diseño Compose opcional.
 * @param backupDateMillis Marca de tiempo de la copia de seguridad remota.
 * @param deviceName Nombre del dispositivo emisor del respaldo.
 * @param isActual Indica si la copia remota coincide exactamente con la versión local actual.
 */
@Composable
fun DriveDecryptDialog(
    onRestore: (CharArray) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    backupDateMillis: Long? = null,
    deviceName: String? = null,
    isActual: Boolean = false
) {
    val decryptErrorText = stringResource(R.string.settings_drive_decrypt_error)
    val appHaptics = rememberAppHaptics()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { AuthenticatorApp.instance.accountRepository }

    var currentStep by remember { mutableStateOf(DriveDecryptStep.DECRYPT) }
    var restoreSecretText by remember { mutableStateOf("") }
    var isRestoreSecretVisible by remember { mutableStateOf(false) }
    var isDecrypting by remember { mutableStateOf(false) }
    var decryptErrorMessage by remember { mutableStateOf<String?>(null) }
    var downloadedSession by remember { mutableStateOf<CloudVaultKeyStore.VaultKeySession?>(null) }

    var parsedAccounts by remember { mutableStateOf<List<ParsedAccountPreview>>(emptyList()) }
    var selectedAccountIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    AppModalDialog(
        onDismissRequest = onDismiss,
        onBackStep = {
            if (!isDecrypting && currentStep == DriveDecryptStep.SELECT_ACCOUNTS) {
                currentStep = DriveDecryptStep.DECRYPT
                true
            } else {
                false
            }
        },
        modifier = modifier
    ) {
        AnimatedContent(
            targetState = currentStep,
            transitionSpec = { Motion.Spec.dialogStepContentTransform() },
            label = "DriveDecryptStepTransition"
        ) { step ->
            when (step) {
                DriveDecryptStep.DECRYPT -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Dimensions.Spacing.lg)
                            .imePadding(),
                        verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md)
                    ) {
                        // Cabecera fija
                        Text(
                            text = stringResource(R.string.settings_drive_decrypt_title),
                            style = MaterialTheme.typography.titleLarge
                        )

                        // Cuerpo central scrolleable aislado
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md)
                        ) {

                        val targetBackup = remember(backupDateMillis, deviceName, isActual) {
                            DriveBackupItem(
                                fileId = "",
                                fileName = "",
                                modifiedTimeMillis = backupDateMillis ?: 0L,
                                sizeBytes = 0L,
                                deviceName = deviceName.orEmpty(),
                                isMostRecent = isActual
                            )
                        }

                        DriveBackupDecryptForm(
                            targetBackup = targetBackup,
                            isActual = isActual,
                            restoreSecretText = restoreSecretText,
                            onRestoreSecretChange = {
                                restoreSecretText = it
                                decryptErrorMessage = null
                            },
                            isRestoreSecretVisible = isRestoreSecretVisible,
                            onToggleSecretVisibility = { isRestoreSecretVisible = !isRestoreSecretVisible }
                        )

                        androidx.compose.animation.AnimatedVisibility(
                            visible = decryptErrorMessage != null,
                            enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
                            exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()
                        ) {
                            if (decryptErrorMessage != null) {
                                Text(
                                    text = decryptErrorMessage.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        }

                        // Pie fijo de acciones
                        AppDialogActionButtons(
                            dismissText = stringResource(R.string.action_close),
                            onDismiss = {
                                if (!isDecrypting) onDismiss()
                            },
                            confirmText = stringResource(R.string.settings_drive_decrypt_and_restore),
                            onConfirm = {
                                val token = GoogleDriveManager.currentAccessToken.orEmpty()
                                val normalizedSecret = if (restoreSecretText.contains(" ")) {
                                    MnemonicManager.normalizePhrase(restoreSecretText)
                                } else {
                                    restoreSecretText.trim()
                                }
                                val passChars = normalizedSecret.toCharArray()
                                isDecrypting = true
                                decryptErrorMessage = null

                                scope.launch {
                                    try {
                                        val downloadResult = if (token.isNotBlank()) {
                                            GoogleDriveManager.downloadBackupDetailed(token, passChars)
                                        } else {
                                            null
                                        }

                                        if (downloadResult != null && downloadResult.isSuccess) {
                                            val detailed = downloadResult.getOrThrow()
                                            downloadedSession = detailed.session
                                            val jsonString = detailed.plainJson
                                            val previews = repository.parseAccountsForPreview(jsonString)
                                            if (previews.isNotEmpty()) {
                                                parsedAccounts = previews
                                                selectedAccountIds = previews.map { it.id }.toSet()
                                                appHaptics.success()
                                                currentStep = DriveDecryptStep.SELECT_ACCOUNTS
                                            } else {
                                                decryptErrorMessage = decryptErrorText
                                                appHaptics.error()
                                            }
                                        } else {
                                            onRestore(passChars)
                                            onDismiss()
                                        }
                                    } catch (_: Exception) {
                                        decryptErrorMessage = decryptErrorText
                                        appHaptics.error()
                                    } finally {
                                        passChars.fill('0')
                                        isDecrypting = false
                                    }
                                }
                            },
                            confirmEnabled = restoreSecretText.isNotBlank() && !isDecrypting
                        )
                    }
                }

                DriveDecryptStep.SELECT_ACCOUNTS -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Dimensions.Spacing.lg)
                            .imePadding(),
                        verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md)
                    ) {
                        // Cabecera fija
                        Text(
                            text = stringResource(R.string.drive_restore_selection_title),
                            style = MaterialTheme.typography.titleLarge
                        )

                        // Cuerpo central scrolleable aislado
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false),
                            verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md)
                        ) {
                            Text(
                                text = stringResource(R.string.drive_restore_selection_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                        AccountImportSelectionList(
                            accounts = parsedAccounts,
                            selectedIds = selectedAccountIds,
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
                            }
                        )
                        }

                        // Pie fijo de acciones
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = {
                                    currentStep = DriveDecryptStep.DECRYPT
                                },
                                shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                                contentPadding = PaddingValues(horizontal = Dimensions.Spacing.md, vertical = Dimensions.Spacing.none),
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .heightIn(min = Dimensions.ComponentHeight.buttonDefault)
                            ) {
                                Text(
                                    text = stringResource(R.string.settings_drive_details_back),
                                    style = MaterialTheme.typography.labelLarge,
                                    textAlign = TextAlign.Center
                                )
                            }

                            AppAnimatedButton(
                                text = stringResource(R.string.drive_restore_confirm_button, selectedAccountIds.size),
                                onClick = {
                                    val accountsToImport = parsedAccounts.filter { it.id in selectedAccountIds }
                                    repository.importSelectedAccounts(accountsToImport)
                                    downloadedSession?.let { session ->
                                        CloudVaultKeyStore.saveVaultKeyAndSlots(context.applicationContext, session)
                                        val prefsManager = AuthenticatorApp.instance.preferencesManager
                                        prefsManager.setGoogleDriveConnected(true)
                                        if (backupDateMillis != null && backupDateMillis > 0L) {
                                            prefsManager.setLastSyncTimestamp(backupDateMillis)
                                        }
                                    }
                                    true
                                },
                                onActionConfirmed = {
                                    onDismiss()
                                },
                                enabled = selectedAccountIds.isNotEmpty(),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}
