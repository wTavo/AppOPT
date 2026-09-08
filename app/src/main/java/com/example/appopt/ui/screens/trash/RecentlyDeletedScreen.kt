package com.example.appopt.ui.screens.trash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoDelete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.appopt.AuthenticatorApp
import com.example.appopt.R
import com.example.appopt.domain.model.TotpAccount
import com.example.appopt.security.SecurityConfig
import com.example.appopt.ui.components.AppAnimatedButton
import com.example.appopt.ui.components.ServiceBrandAvatar
import com.example.appopt.ui.theme.Dimensions
import com.example.appopt.ui.theme.UrgentRed
import com.example.appopt.ui.theme.rememberAppHaptics
import kotlinx.coroutines.launch

/**
 * Pantalla dedicada de Papelera y Servicios Eliminados Recientemente (30 Días).
 *
 * Características principales:
 * - Listado de servicios eliminados con avatar de marca y conteo regresivo de retención.
 * - Acción de restauración inmediata que devuelve el servicio a la bóveda activa y sincroniza con Google Drive.
 * - Eliminación definitiva individual o vaciado completo de la papelera con diálogos de confirmación defensivos.
 *
 * @param onNavigateBack Callback para regresar a la pantalla anterior.
 * @param modifier Modificador de diseño Compose opcional.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentlyDeletedScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val repository = AuthenticatorApp.instance.accountRepository
    val deletedAccounts by repository.getDeletedAccounts().collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val appHaptics = rememberAppHaptics()

    // Estados para modales de confirmación
    var showEmptyTrashConfirmDialog by remember { mutableStateOf(false) }
    var accountPendingPermanentDelete by remember { mutableStateOf<TotpAccount?>(null) }

    // Textos centralizados
    val restoreSuccessText = stringResource(R.string.trash_restore_success)
    val emptyTrashSuccessText = stringResource(R.string.trash_empty_success)
    val permanentDeleteSuccessText = stringResource(R.string.trash_permanent_delete_success)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.trash_nav_title),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                actions = {
                    if (deletedAccounts.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                appHaptics.click()
                                showEmptyTrashConfirmDialog = true
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeleteSweep,
                                contentDescription = stringResource(R.string.trash_empty_button),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        modifier = modifier
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (deletedAccounts.isEmpty()) {
                // Estado vacío amigable
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = Dimensions.Spacing.xxl),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        modifier = Modifier.size(Dimensions.IconSize.hero + Dimensions.Spacing.lg),
                        shape = RoundedCornerShape(Dimensions.CornerRadius.pill),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.AutoDelete,
                                contentDescription = null,
                                modifier = Modifier.size(Dimensions.IconSize.large),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(Dimensions.Spacing.lg))

                    Text(
                        text = stringResource(R.string.trash_empty_state_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(Dimensions.Spacing.xs))

                    Text(
                        text = stringResource(R.string.trash_empty_state_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = Dimensions.Spacing.lg,
                        end = Dimensions.Spacing.lg,
                        top = Dimensions.Spacing.sm,
                        bottom = Dimensions.Spacing.xxl
                    ),
                    verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md)
                ) {
                    // Tarjeta informativa superior
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            shape = RoundedCornerShape(Dimensions.CornerRadius.large),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(Dimensions.Spacing.md),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AutoDelete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(Dimensions.IconSize.medium)
                                )
                                Spacer(modifier = Modifier.width(Dimensions.Spacing.sm))
                                Text(
                                    text = stringResource(R.string.trash_description),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Lista de servicios eliminados
                    items(deletedAccounts, key = { it.id }) { account ->
                        DeletedAccountCard(
                            account = account,
                            onRestore = {
                                scope.launch {
                                    val result = repository.restoreFromTrash(account.id)
                                    if (result.isSuccess) {
                                        appHaptics.success()
                                        snackbarHostState.showSnackbar(restoreSuccessText)
                                    }
                                }
                            },
                            onPermanentDelete = {
                                appHaptics.click()
                                accountPendingPermanentDelete = account
                            }
                        )
                    }
                }
            }
        }
    }

    // Modal: Confirmación de vaciado total de papelera
    if (showEmptyTrashConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showEmptyTrashConfirmDialog = false },
            shape = RoundedCornerShape(Dimensions.CornerRadius.large),
            icon = {
                Icon(
                    imageVector = Icons.Filled.DeleteSweep,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(Dimensions.IconSize.large)
                )
            },
            title = {
                Text(
                    text = stringResource(R.string.trash_empty_title),
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.trash_empty_confirm_msg),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                var isProcessing by remember { mutableStateOf(false) }
                Button(
                    onClick = {
                        if (!isProcessing) {
                            isProcessing = true
                            scope.launch {
                                try {
                                    repository.emptyTrash()
                                    appHaptics.success()
                                    snackbarHostState.showSnackbar(emptyTrashSuccessText)
                                } finally {
                                    showEmptyTrashConfirmDialog = false
                                }
                            }
                        }
                    },
                    enabled = !isProcessing,
                    shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text(
                        text = stringResource(R.string.trash_empty_button),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showEmptyTrashConfirmDialog = false }) {
                    Text(
                        text = stringResource(R.string.action_cancel),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        )
    }

    // Modal: Confirmación de eliminación definitiva de 1 cuenta
    accountPendingPermanentDelete?.let { account ->
        AlertDialog(
            onDismissRequest = { accountPendingPermanentDelete = null },
            shape = RoundedCornerShape(Dimensions.CornerRadius.large),
            icon = {
                Icon(
                    imageVector = Icons.Filled.DeleteForever,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(Dimensions.IconSize.large)
                )
            },
            title = {
                Text(
                    text = stringResource(R.string.trash_permanent_delete_title),
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.trash_permanent_delete_confirm_msg),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                var isProcessing by remember { mutableStateOf(false) }
                Button(
                    onClick = {
                        if (!isProcessing) {
                            isProcessing = true
                            scope.launch {
                                try {
                                    repository.permanentlyDelete(account.id)
                                    appHaptics.success()
                                    snackbarHostState.showSnackbar(permanentDeleteSuccessText)
                                } finally {
                                    accountPendingPermanentDelete = null
                                }
                            }
                        }
                    },
                    enabled = !isProcessing,
                    shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text(
                        text = stringResource(R.string.trash_permanent_delete_button),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { accountPendingPermanentDelete = null }) {
                    Text(
                        text = stringResource(R.string.action_cancel),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        )
    }
}

/**
 * Tarjeta individual representativa de una cuenta en la papelera de reciclaje.
 *
 * @param account Modelo de la cuenta eliminada.
 * @param onRestore Callback para restaurar la cuenta.
 * @param onPermanentDelete Callback para solicitar la eliminación definitiva.
 */
@Composable
private fun DeletedAccountCard(
    account: TotpAccount,
    onRestore: () -> Unit,
    onPermanentDelete: () -> Unit
) {
    val now = remember { System.currentTimeMillis() }
    val deletedTime = account.deletedAt ?: account.updatedAt
    val elapsedMillis = (now - deletedTime).coerceAtLeast(0L)
    val remainingMillis = (SecurityConfig.TRASH_RETENTION_MILLIS - elapsedMillis).coerceAtLeast(0L)
    val remainingDays = (remainingMillis / (24L * 60L * 60L * 1000L)).toInt()

    val countdownText = if (remainingDays > 0) {
        stringResource(R.string.trash_days_remaining, remainingDays)
    } else {
        stringResource(R.string.trash_hours_remaining)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimensions.CornerRadius.large),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = Dimensions.Elevation.cardDefault)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimensions.Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ServiceBrandAvatar(
                    issuer = account.issuer,
                    size = Dimensions.IconSize.hero
                )

                Spacer(modifier = Modifier.width(Dimensions.Spacing.sm))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = account.issuer.ifEmpty { stringResource(R.string.home_default_issuer) },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (account.accountName.isNotBlank()) {
                        Text(
                            text = account.accountName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(Dimensions.Spacing.xs))
                    Text(
                        text = countdownText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            // Acciones: Restaurar y Eliminar definitivamente
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm)
            ) {
                OutlinedButton(
                    onClick = onPermanentDelete,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = UrgentRed
                    )
                ) {
                    Text(
                        text = stringResource(R.string.trash_permanent_delete_button),
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                AppAnimatedButton(
                    text = stringResource(R.string.trash_restore_button),
                    onClick = {
                        onRestore()
                        true
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
