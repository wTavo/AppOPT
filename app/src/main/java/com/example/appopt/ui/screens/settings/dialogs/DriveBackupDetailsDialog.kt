package com.example.appopt.ui.screens.settings.dialogs

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.appopt.data.cloud.DriveBackupItem
import com.example.appopt.data.cloud.GoogleDriveManager
import com.example.appopt.domain.model.ParsedAccountPreview
import com.example.appopt.security.MnemonicManager
import com.example.appopt.security.SecurityConfig
import com.example.appopt.ui.components.AccountImportSelectionList
import com.example.appopt.ui.components.AppAnimatedButton
import com.example.appopt.ui.components.AppDialogActionButtons
import com.example.appopt.ui.components.AppModalDialog
import com.example.appopt.ui.screens.settings.dialogs.components.DriveBackupDecryptForm
import com.example.appopt.ui.screens.settings.dialogs.components.DriveBackupItemCard
import com.example.appopt.ui.theme.Dimensions
import com.example.appopt.ui.theme.Motion
import com.example.appopt.ui.theme.rememberAppHaptics
import com.example.appopt.util.DateTimeFormatter
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

private enum class DriveDetailsSubState {
    HISTORY,
    RESTORE_DECRYPT,
    RESTORE_SELECT_ACCOUNTS,
    DELETE_SINGLE
}

@Composable
fun DriveBackupDetailsDialog(
    backupItems: List<DriveBackupItem>,
    isLoading: Boolean,
    lastFetchTimestamp: Long = 0L,
    lastSyncTimestamp: Long = 0L,
    lastSyncedHash: String = "",
    hasUnsyncedChanges: Boolean = false,
    onForceRefresh: () -> Unit = {},
    onRestoreBackup: (DriveBackupItem, CharArray) -> Unit,
    onDeleteSpecificBackup: (DriveBackupItem, CharArray) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appHaptics = rememberAppHaptics()
    val scope = rememberCoroutineScope()
    val repository = remember { AuthenticatorApp.instance.accountRepository }

    var pendingRestoreBackup by remember { mutableStateOf<DriveBackupItem?>(null) }
    var restoreSecretText by remember { mutableStateOf("") }
    var isRestoreSecretVisible by remember { mutableStateOf(false) }
    var isDecryptingBackup by remember { mutableStateOf(false) }
    var decryptErrorMessage by remember { mutableStateOf<String?>(null) }

    var parsedAccounts by remember { mutableStateOf<List<ParsedAccountPreview>>(emptyList()) }
    var selectedAccountIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    var pendingDeleteBackup by remember { mutableStateOf<DriveBackupItem?>(null) }
    var deleteSecretText by remember { mutableStateOf("") }
    var isDeleteSecretVisible by remember { mutableStateOf(false) }

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
                kotlinx.coroutines.delay(1_000L.milliseconds)
            }
        }
    }

    val elapsed = (currentTick - lastFetchTimestamp).coerceAtLeast(0L)
    val remainingMillis = (SecurityConfig.BACKUP_HISTORY_CACHE_TTL_MILLIS - elapsed).coerceAtLeast(0L)
    val secondsRemaining = (remainingMillis + 999L) / 1000L
    val isCooldownActive = secondsRemaining > 0L

    var currentSubState by remember { mutableStateOf(DriveDetailsSubState.HISTORY) }

    LaunchedEffect(pendingRestoreBackup, pendingDeleteBackup) {
        if (pendingRestoreBackup != null && currentSubState != DriveDetailsSubState.RESTORE_SELECT_ACCOUNTS) {
            currentSubState = DriveDetailsSubState.RESTORE_DECRYPT
        } else if (pendingDeleteBackup != null) {
            currentSubState = DriveDetailsSubState.DELETE_SINGLE
        } else if (currentSubState != DriveDetailsSubState.RESTORE_SELECT_ACCOUNTS) {
            currentSubState = DriveDetailsSubState.HISTORY
        }
    }

    AppModalDialog(
        onDismissRequest = {
            if (!isDecryptingBackup) {
                when (currentSubState) {
                    DriveDetailsSubState.RESTORE_SELECT_ACCOUNTS -> {
                        currentSubState = DriveDetailsSubState.RESTORE_DECRYPT
                    }
                    DriveDetailsSubState.RESTORE_DECRYPT -> {
                        pendingRestoreBackup = null
                        restoreSecretText = ""
                        decryptErrorMessage = null
                        currentSubState = DriveDetailsSubState.HISTORY
                    }
                    DriveDetailsSubState.DELETE_SINGLE -> {
                        pendingDeleteBackup = null
                        deleteSecretText = ""
                        currentSubState = DriveDetailsSubState.HISTORY
                    }
                    DriveDetailsSubState.HISTORY -> onDismiss()
                }
            }
        },
        modifier = modifier
    ) {
        AnimatedContent(
            targetState = currentSubState,
            transitionSpec = { Motion.Spec.dialogStepContentTransform() },
            contentAlignment = Alignment.TopCenter,
            modifier = Modifier.fillMaxWidth(),
            label = "driveDetailsSubStateTransition"
        ) { subState ->
            when (subState) {
                DriveDetailsSubState.RESTORE_DECRYPT -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Dimensions.Spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_drive_decrypt_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        val backupToRestore = pendingRestoreBackup
                        if (backupToRestore != null) {
                            DriveBackupDecryptForm(
                                targetBackup = backupToRestore,
                                isActual = false,
                                restoreSecretText = restoreSecretText,
                                onRestoreSecretChange = {
                                    restoreSecretText = it
                                    decryptErrorMessage = null
                                },
                                isRestoreSecretVisible = isRestoreSecretVisible,
                                onToggleSecretVisibility = { isRestoreSecretVisible = !isRestoreSecretVisible },
                                hintText = stringResource(R.string.settings_drive_decrypt_hint)
                            )
                        }

                        if (decryptErrorMessage != null) {
                            Text(
                                text = decryptErrorMessage.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        AppDialogActionButtons(
                            confirmText = stringResource(R.string.settings_drive_decrypt_and_restore),
                            onConfirm = {
                                val targetItem = pendingRestoreBackup ?: return@AppDialogActionButtons
                                val token = GoogleDriveManager.currentAccessToken.orEmpty()
                                val normalizedSecret = if (restoreSecretText.contains(" ")) {
                                    MnemonicManager.normalizePhrase(restoreSecretText)
                                } else {
                                    restoreSecretText.trim()
                                }
                                val passChars = normalizedSecret.toCharArray()
                                isDecryptingBackup = true
                                decryptErrorMessage = null

                                scope.launch {
                                    try {
                                        val downloadResult = if (token.isNotBlank() && targetItem.fileId.isNotBlank()) {
                                            GoogleDriveManager.downloadBackupById(token, targetItem.fileId, passChars)
                                        } else if (token.isNotBlank()) {
                                            GoogleDriveManager.downloadBackup(token, passChars)
                                        } else {
                                            null
                                        }

                                        if (downloadResult != null && downloadResult.isSuccess) {
                                            val jsonString = downloadResult.getOrThrow()
                                            val previews = repository.parseAccountsForPreview(jsonString)
                                            if (previews.isNotEmpty()) {
                                                parsedAccounts = previews
                                                selectedAccountIds = previews.map { it.id }.toSet()
                                                appHaptics.success()
                                                currentSubState = DriveDetailsSubState.RESTORE_SELECT_ACCOUNTS
                                            } else {
                                                decryptErrorMessage = context.getString(R.string.settings_drive_decrypt_error)
                                                appHaptics.error()
                                            }
                                        } else {
                                            onRestoreBackup(targetItem, passChars)
                                            pendingRestoreBackup = null
                                            restoreSecretText = ""
                                            currentSubState = DriveDetailsSubState.HISTORY
                                        }
                                    } catch (e: Exception) {
                                        decryptErrorMessage = e.localizedMessage ?: context.getString(R.string.settings_drive_decrypt_error)
                                        appHaptics.error()
                                    } finally {
                                        passChars.fill('0')
                                        isDecryptingBackup = false
                                    }
                                }
                            },
                            dismissText = stringResource(R.string.settings_drive_details_back),
                            onDismiss = {
                                pendingRestoreBackup = null
                                restoreSecretText = ""
                                decryptErrorMessage = null
                                currentSubState = DriveDetailsSubState.HISTORY
                            },
                            confirmEnabled = restoreSecretText.isNotBlank() && !isDecryptingBackup
                        )
                    }
                }

                DriveDetailsSubState.RESTORE_SELECT_ACCOUNTS -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Dimensions.Spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md)
                    ) {
                        Text(
                            text = stringResource(R.string.drive_restore_selection_title),
                            style = MaterialTheme.typography.titleLarge
                        )

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

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = {
                                    currentSubState = DriveDetailsSubState.RESTORE_DECRYPT
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
                                    true
                                },
                                onActionConfirmed = {
                                    pendingRestoreBackup = null
                                    restoreSecretText = ""
                                    onDismiss()
                                },
                                enabled = selectedAccountIds.isNotEmpty(),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                DriveDetailsSubState.DELETE_SINGLE -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Dimensions.Spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_drive_delete_version_confirm_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.error
                        )

                        val backupToDelete = pendingDeleteBackup
                        if (backupToDelete != null) {
                            DriveBackupDecryptForm(
                                targetBackup = backupToDelete,
                                isActual = false,
                                restoreSecretText = deleteSecretText,
                                onRestoreSecretChange = { deleteSecretText = it },
                                isRestoreSecretVisible = isDeleteSecretVisible,
                                onToggleSecretVisibility = { isDeleteSecretVisible = !isDeleteSecretVisible },
                                hintText = stringResource(R.string.settings_drive_delete_version_auth_hint)
                            )
                        }

                        AppDialogActionButtons(
                            confirmText = stringResource(R.string.action_delete),
                            onConfirm = {
                                val targetItem = pendingDeleteBackup
                                if (targetItem != null) {
                                    val normalizedSecret = if (deleteSecretText.contains(" ")) {
                                        MnemonicManager.normalizePhrase(deleteSecretText)
                                    } else {
                                        deleteSecretText.trim()
                                    }
                                    val passChars = normalizedSecret.toCharArray()
                                    onDeleteSpecificBackup(targetItem, passChars)
                                    pendingDeleteBackup = null
                                    deleteSecretText = ""
                                    currentSubState = DriveDetailsSubState.HISTORY
                                }
                            },
                            dismissText = stringResource(R.string.settings_drive_details_back),
                            onDismiss = {
                                pendingDeleteBackup = null
                                deleteSecretText = ""
                                currentSubState = DriveDetailsSubState.HISTORY
                            },
                            confirmEnabled = deleteSecretText.isNotBlank(),
                            isDestructive = true
                        )
                    }
                }

                DriveDetailsSubState.HISTORY -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Dimensions.Spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.settings_drive_history_title),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            IconButton(
                                onClick = {
                                    appHaptics.click()
                                    onForceRefresh()
                                },
                                enabled = !isLoading && !isCooldownActive,
                                modifier = Modifier.size(Dimensions.ComponentSize.actionIconButton)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = stringResource(R.string.settings_drive_history_refresh_action),
                                    tint = if (isCooldownActive) {
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    }
                                )
                            }
                        }

                        Text(
                            text = stringResource(R.string.settings_drive_history_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (isLoading) {
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
                        } else if (backupItems.isEmpty()) {
                            Text(
                                text = stringResource(R.string.settings_drive_history_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = Dimensions.Spacing.lg)
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = Dimensions.ComponentSize.modalListMaxHeight),
                                verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm),
                                contentPadding = PaddingValues(bottom = Dimensions.Spacing.xs)
                            ) {
                                items(
                                    items = backupItems,
                                    key = { it.fileId.ifEmpty { it.modifiedTimeMillis.toString() } }
                                ) { item ->
                                    val isActual = (lastSyncTimestamp > 0L &&
                                            item.modifiedTimeMillis == lastSyncTimestamp &&
                                            lastSyncedHash.isNotEmpty() &&
                                            !hasUnsyncedChanges)

                                    DriveBackupItemCard(
                                        item = item,
                                        isActual = isActual,
                                        formattedDate = DateTimeFormatter.formatRelativeSyncTime(context, item.modifiedTimeMillis),
                                        onRestoreClick = {
                                            appHaptics.click()
                                            pendingRestoreBackup = item
                                            restoreSecretText = ""
                                            currentSubState = DriveDetailsSubState.RESTORE_DECRYPT
                                        },
                                        onDeleteClick = {
                                            appHaptics.click()
                                            pendingDeleteBackup = item
                                            deleteSecretText = ""
                                            currentSubState = DriveDetailsSubState.DELETE_SINGLE
                                        }
                                    )
                                }
                            }
                        }

                        AppDialogActionButtons(
                            dismissText = stringResource(R.string.action_close),
                            onDismiss = onDismiss,
                            confirmText = null,
                            onConfirm = null
                        )
                    }
                }
            }
        }
    }
}
