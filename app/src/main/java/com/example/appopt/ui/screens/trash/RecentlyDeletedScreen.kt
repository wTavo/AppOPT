package com.example.appopt.ui.screens.trash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.appopt.AuthenticatorApp
import com.example.appopt.R
import com.example.appopt.domain.model.TotpAccount
import com.example.appopt.ui.components.AppDestructiveConfirmDialog
import com.example.appopt.ui.components.AppDialogActionButtons
import com.example.appopt.ui.navigation.NavigationOriginTracker
import com.example.appopt.ui.screens.trash.components.DeletedAccountCard
import com.example.appopt.ui.theme.Dimensions
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

    val appLockManager = remember { AuthenticatorApp.instance.appLockManager }
    val isUnlocked by appLockManager.isUnlocked.collectAsStateWithLifecycle()

    // Cierre defensivo de modales de eliminación al bloquearse la bóveda
    LaunchedEffect(isUnlocked) {
        if (!isUnlocked) {
            showEmptyTrashConfirmDialog = false
            accountPendingPermanentDelete = null
        }
    }

    // Textos centralizados
    val restoreSuccessText = stringResource(R.string.trash_restore_success)
    val emptyTrashSuccessText = stringResource(R.string.trash_empty_success)
    val permanentDeleteSuccessText = stringResource(R.string.trash_permanent_delete_success)

    var emptyTrashCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

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
                                NavigationOriginTracker.updateFromCoordinates(emptyTrashCoordinates)
                                appHaptics.click()
                                showEmptyTrashConfirmDialog = true
                            },
                            modifier = Modifier.onGloballyPositioned { emptyTrashCoordinates = it }
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
    if (isUnlocked && showEmptyTrashConfirmDialog) {
        AppDestructiveConfirmDialog(
            title = stringResource(R.string.trash_empty_title),
            message = stringResource(R.string.trash_empty_confirm_msg),
            confirmText = stringResource(R.string.trash_empty_button),
            onConfirm = {
                showEmptyTrashConfirmDialog = false
                scope.launch {
                    repository.emptyTrash()
                    appHaptics.success()
                    snackbarHostState.showSnackbar(emptyTrashSuccessText)
                }
            },
            onDismiss = { showEmptyTrashConfirmDialog = false },
            icon = Icons.Filled.DeleteSweep
        )
    }

    // Modal: Confirmación de eliminación definitiva de 1 cuenta
    if (isUnlocked) {
        accountPendingPermanentDelete?.let { account ->
            AppDestructiveConfirmDialog(
                title = stringResource(R.string.trash_permanent_delete_title),
                message = stringResource(R.string.trash_permanent_delete_confirm_msg),
                confirmText = stringResource(R.string.trash_permanent_delete_button),
                onConfirm = {
                    val targetAccountId = account.id
                    accountPendingPermanentDelete = null
                    scope.launch {
                        repository.permanentlyDelete(targetAccountId)
                        appHaptics.success()
                        snackbarHostState.showSnackbar(permanentDeleteSuccessText)
                    }
                },
                onDismiss = { accountPendingPermanentDelete = null },
                icon = Icons.Filled.DeleteForever
            )
        }
    }
}
