package com.example.appopt.ui.screens.settings.dialogs

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.example.appopt.AuthenticatorApp
import com.example.appopt.R
import com.example.appopt.data.cloud.CloudVaultKeyStore
import com.example.appopt.data.cloud.DriveBackupItem
import com.example.appopt.data.cloud.GoogleDriveManager
import com.example.appopt.domain.model.ParsedAccountPreview
import com.example.appopt.security.MnemonicManager
import com.example.appopt.security.SecurityConfig
import com.example.appopt.ui.components.AppDialogActionButtons
import com.example.appopt.ui.components.AppModalDialog
import com.example.appopt.ui.components.ModalTone
import com.example.appopt.ui.screens.settings.dialogs.components.DriveBackupItemCard
import com.example.appopt.ui.screens.settings.dialogs.components.DriveDetailsDecryptStep
import com.example.appopt.ui.screens.settings.dialogs.components.DriveDetailsSelectAccountsStep
import com.example.appopt.ui.theme.Dimensions
import com.example.appopt.ui.theme.Motion
import com.example.appopt.ui.theme.rememberAppHaptics
import com.example.appopt.util.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * Sub-estados de navegación interna dentro de la máquina de estados del diálogo de advertencia de sobrescritura.
 */
private enum class OverwriteSubState {
    WARNING,
    RESTORE_DECRYPT,
    RESTORE_SELECT_ACCOUNTS
}

/**
 * Sub-estados de contenido visual dentro de la pantalla de advertencia.
 */
private enum class OverwriteContentState {
    LOADING,
    CARD
}

/**
 * Diálogo modal de advertencia preventiva cuando existe un respaldo previo en la nube y se intenta sobrescribir (Directivas 14, 22 y 23).
 *
 * Unifica en una única máquina de estados fluida:
 * 1. Advertencia destructiva con renderizado de la versión remota existente, botón de actualización y botón de restauración in-situ ([OverwriteSubState.WARNING]).
 * 2. Paso de descifrado con frase de recuperación o contraseña ([OverwriteSubState.RESTORE_DECRYPT]).
 * 3. Paso de selección granular de cuentas e importación ([OverwriteSubState.RESTORE_SELECT_ACCOUNTS]).
 *
 * @param backupItems Lista de versiones de respaldo existentes en Google Drive.
 * @param backupInfo Metadatos de la copia de seguridad existente en Google Drive como respaldo alternativo.
 * @param formattedLastSync Marca de tiempo de sincronización formateada alternativa.
 * @param isLoading Indica si la consulta remota de versiones está en progreso.
 * @param lastFetchTimestamp Marca de tiempo de la última consulta para control de enfriamiento (cooldown).
 * @param onForceRefresh Callback para forzar la sincronización remota de las copias existentes.
 * @param onConfirmOverwrite Callback invocado al confirmar la sobrescritura del respaldo.
 * @param onDismiss Callback invocado para cerrar el modal sin realizar cambios.
 * @param modifier Modificador de diseño Compose opcional.
 */
@Composable
fun DriveOverwriteWarningDialog(
    backupItems: List<DriveBackupItem>,
    backupInfo: DriveBackupItem?,
    formattedLastSync: String?,
    isLoading: Boolean = false,
    lastFetchTimestamp: Long = 0L,
    onForceRefresh: () -> Unit = {},
    onConfirmOverwrite: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appHaptics = rememberAppHaptics()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { AuthenticatorApp.instance.accountRepository }
    val decryptErrorText = stringResource(R.string.settings_drive_decrypt_error)

    var currentSubState by remember { mutableStateOf(OverwriteSubState.WARNING) }
    var isRestoring by remember { mutableStateOf(false) }
    var restoreSecretText by remember { mutableStateOf("") }
    var isRestoreSecretVisible by remember { mutableStateOf(false) }
    var decryptErrorMessage by remember { mutableStateOf<String?>(null) }
    var downloadedSession by remember { mutableStateOf<CloudVaultKeyStore.VaultKeySession?>(null) }
    var parsedAccounts by remember { mutableStateOf<List<ParsedAccountPreview>>(emptyList()) }
    var selectedAccountIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    val targetItem = backupItems.firstOrNull() ?: backupInfo

    var currentTick by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(lastFetchTimestamp) {
        val initialElapsed = (System.currentTimeMillis() - lastFetchTimestamp).coerceAtLeast(0L)
        val initialRemaining = (SecurityConfig.BACKUP_HISTORY_CACHE_TTL_MILLIS - initialElapsed).coerceAtLeast(0L)
        if (initialRemaining > 0L) {
            while (isActive) {
                val now = System.currentTimeMillis()
                currentTick = now
                val elapsed = (now - lastFetchTimestamp).coerceAtLeast(0L)
                val remaining = (SecurityConfig.BACKUP_HISTORY_CACHE_TTL_MILLIS - elapsed).coerceAtLeast(0L)
                if (remaining <= 0L) {
                    break
                }
                delay(1_000L.milliseconds)
            }
        }
    }

    val elapsed = (currentTick - lastFetchTimestamp).coerceAtLeast(0L)
    val remainingMillis = (SecurityConfig.BACKUP_HISTORY_CACHE_TTL_MILLIS - elapsed).coerceAtLeast(0L)
    val secondsRemaining = (remainingMillis + 999L) / 1000L
    val isCooldownActive = secondsRemaining > 0L

    AppModalDialog(
        onDismissRequest = {
            if (isRestoring && currentSubState != OverwriteSubState.RESTORE_SELECT_ACCOUNTS) return@AppModalDialog
            isRestoring = false
            onDismiss()
        },
        onBackStep = {
            when (currentSubState) {
                OverwriteSubState.RESTORE_SELECT_ACCOUNTS -> {
                    isRestoring = false
                    if (targetItem?.isEncrypted == false) {
                        currentSubState = OverwriteSubState.WARNING
                    } else {
                        currentSubState = OverwriteSubState.RESTORE_DECRYPT
                    }
                    true
                }
                OverwriteSubState.RESTORE_DECRYPT -> {
                    if (isRestoring) return@AppModalDialog true
                    isRestoring = false
                    restoreSecretText = ""
                    decryptErrorMessage = null
                    currentSubState = OverwriteSubState.WARNING
                    true
                }
                OverwriteSubState.WARNING -> {
                    if (isRestoring) return@AppModalDialog true
                    false
                }
            }
        },
        tone = if (currentSubState == OverwriteSubState.WARNING) ModalTone.DESTRUCTIVE else ModalTone.STANDARD,
        modifier = modifier
    ) {
        AnimatedContent(
            targetState = currentSubState,
            transitionSpec = { Motion.Spec.dialogStepContentTransform() },
            contentAlignment = Alignment.TopCenter,
            label = "overwriteSubStateTransition",
            modifier = Modifier.fillMaxWidth()
        ) { subState ->
            when (subState) {
                OverwriteSubState.WARNING -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Dimensions.Spacing.lg)
                            .imePadding(),
                        verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md)
                    ) {
                        // 1. Cabecera fija superior con icono de advertencia y título centrado
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(Dimensions.IconSize.large)
                            )

                            Text(
                                text = stringResource(R.string.settings_drive_overwrite_title),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center
                            )
                        }

                        // 2. Cuerpo central scrolleable aislado (renderiza la última copia a sobrescribir o indicador de carga líquido)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md)
                        ) {
                            Text(
                                text = stringResource(R.string.settings_drive_overwrite_msg),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )

                            val contentState = if (isLoading) OverwriteContentState.LOADING else OverwriteContentState.CARD

                            AnimatedContent(
                                targetState = contentState,
                                transitionSpec = { Motion.Spec.dialogStepContentTransform() },
                                contentAlignment = Alignment.TopCenter,
                                label = "overwriteCardTransition",
                                modifier = Modifier.fillMaxWidth()
                            ) { state ->
                                when (state) {
                                    OverwriteContentState.LOADING -> {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = Dimensions.Spacing.xl),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(Dimensions.IconSize.large)
                                            )
                                        }
                                    }
                                    OverwriteContentState.CARD -> {
                                        if (targetItem != null) {
                                            val formattedDate = DateTimeFormatter.formatRelativeSyncTime(context, targetItem.modifiedTimeMillis)
                                            DriveBackupItemCard(
                                                item = targetItem,
                                                isActual = false,
                                                formattedDate = formattedDate,
                                                onRestoreClick = {
                                                    appHaptics.click()
                                                    if (!targetItem.isEncrypted) {
                                                        isRestoring = true
                                                        decryptErrorMessage = null
                                                        scope.launch {
                                                            try {
                                                                val token = GoogleDriveManager.currentAccessToken.orEmpty()
                                                                val downloadResult = if (token.isNotBlank() && targetItem.fileId.isNotBlank()) {
                                                                    GoogleDriveManager.downloadBackupDetailedById(token, targetItem.fileId, null)
                                                                } else if (token.isNotBlank()) {
                                                                    GoogleDriveManager.downloadBackupDetailed(token, null)
                                                                } else {
                                                                    null
                                                                }
                                                                if (downloadResult != null && downloadResult.isSuccess) {
                                                                    val detailed = downloadResult.getOrThrow()
                                                                    downloadedSession = null
                                                                    val jsonString = detailed.plainJson
                                                                    val previews = repository.parseAccountsForPreview(jsonString)
                                                                    if (previews.isNotEmpty()) {
                                                                        parsedAccounts = previews
                                                                        selectedAccountIds = previews.filter { !it.isAlreadyInVault }.map { it.id }.toSet()
                                                                        appHaptics.success()
                                                                        currentSubState = OverwriteSubState.RESTORE_SELECT_ACCOUNTS
                                                                    } else {
                                                                        decryptErrorMessage = decryptErrorText
                                                                        appHaptics.error()
                                                                        isRestoring = false
                                                                    }
                                                                } else {
                                                                    decryptErrorMessage = decryptErrorText
                                                                    appHaptics.error()
                                                                    isRestoring = false
                                                                }
                                                            } catch (_: Exception) {
                                                                decryptErrorMessage = decryptErrorText
                                                                appHaptics.error()
                                                                isRestoring = false
                                                            }
                                                        }
                                                    } else {
                                                        restoreSecretText = ""
                                                        decryptErrorMessage = null
                                                        currentSubState = OverwriteSubState.RESTORE_DECRYPT
                                                    }
                                                },
                                                onRefreshClick = {
                                                    appHaptics.click()
                                                    onForceRefresh()
                                                },
                                                isRefreshing = isLoading,
                                                isCooldownActive = isCooldownActive,
                                                secondsRemaining = secondsRemaining,
                                                isRestoring = isRestoring,
                                                isAnyOperationRunning = isLoading || isRestoring
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 3. Pie fijo inferior de acciones simétricas con botón Cerrar a la izquierda
                        AppDialogActionButtons(
                            dismissText = stringResource(R.string.action_close),
                            onDismiss = {
                                if (!isLoading && !isRestoring) onDismiss()
                            },
                            confirmText = stringResource(R.string.settings_drive_overwrite_confirm_btn),
                            onConfirm = onConfirmOverwrite,
                            confirmEnabled = !isLoading && !isRestoring,
                            isDestructive = true
                        )
                    }
                }

                OverwriteSubState.RESTORE_DECRYPT -> {
                    DriveDetailsDecryptStep(
                        targetBackup = targetItem,
                        secretText = restoreSecretText,
                        onSecretTextChange = {
                            restoreSecretText = it
                            decryptErrorMessage = null
                        },
                        isSecretVisible = isRestoreSecretVisible,
                        onToggleSecretVisibility = { isRestoreSecretVisible = !isRestoreSecretVisible },
                        decryptErrorMessage = decryptErrorMessage,
                        isDecrypting = isRestoring,
                        onConfirm = {
                            val activeItem = targetItem ?: return@DriveDetailsDecryptStep
                            val token = GoogleDriveManager.currentAccessToken.orEmpty()
                            val normalizedSecret = if (restoreSecretText.contains(" ")) {
                                MnemonicManager.normalizePhrase(restoreSecretText)
                            } else {
                                restoreSecretText.trim()
                            }
                            val passChars = normalizedSecret.toCharArray()
                            isRestoring = true
                            decryptErrorMessage = null

                            scope.launch {
                                try {
                                    val downloadResult = if (token.isNotBlank() && activeItem.fileId.isNotBlank()) {
                                        GoogleDriveManager.downloadBackupDetailedById(token, activeItem.fileId, passChars)
                                    } else if (token.isNotBlank()) {
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
                                            selectedAccountIds = previews.filter { !it.isAlreadyInVault }.map { it.id }.toSet()
                                            appHaptics.success()
                                            currentSubState = OverwriteSubState.RESTORE_SELECT_ACCOUNTS
                                        } else {
                                            decryptErrorMessage = decryptErrorText
                                            appHaptics.error()
                                            isRestoring = false
                                        }
                                    } else {
                                        decryptErrorMessage = decryptErrorText
                                        appHaptics.error()
                                        isRestoring = false
                                    }
                                } catch (_: Exception) {
                                    decryptErrorMessage = decryptErrorText
                                    appHaptics.error()
                                    isRestoring = false
                                } finally {
                                    passChars.fill('0')
                                }
                            }
                        },
                        onBack = {
                            isRestoring = false
                            restoreSecretText = ""
                            decryptErrorMessage = null
                            currentSubState = OverwriteSubState.WARNING
                        }
                    )
                }

                OverwriteSubState.RESTORE_SELECT_ACCOUNTS -> {
                    DriveDetailsSelectAccountsStep(
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
                        onConfirmImport = {
                            val accountsToImport = parsedAccounts.filter { it.id in selectedAccountIds }
                            scope.launch {
                                repository.importSelectedAccounts(accountsToImport)
                                val prefsManager = AuthenticatorApp.instance.preferencesManager
                                prefsManager.setGoogleDriveConnected(true)
                                val session = downloadedSession
                                if (session != null) {
                                    CloudVaultKeyStore.saveVaultKeyAndSlots(context.applicationContext, session)
                                    prefsManager.setDriveBackupEncrypted(true)
                                } else if (targetItem?.isEncrypted == false) {
                                    prefsManager.setDriveBackupEncrypted(false)
                                }
                                targetItem?.let { prefsManager.setLastSyncTimestamp(it.modifiedTimeMillis) }
                            }
                            true
                        },
                        onActionConfirmed = {
                            onDismiss()
                        },
                        onBack = {
                            isRestoring = false
                            if (targetItem?.isEncrypted == false) {
                                currentSubState = OverwriteSubState.WARNING
                            } else {
                                currentSubState = OverwriteSubState.RESTORE_DECRYPT
                            }
                        }
                    )
                }
            }
        }
    }
}

