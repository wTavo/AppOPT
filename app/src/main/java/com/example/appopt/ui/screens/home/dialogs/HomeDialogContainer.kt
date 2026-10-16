package com.example.appopt.ui.screens.home.dialogs

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.example.appopt.data.cloud.SharedBatchKeyStore
import com.example.appopt.domain.model.AccountWithCode
import com.example.appopt.domain.model.IncomingSharedService
import com.example.appopt.ui.components.AccountDetailsDialog
import com.example.appopt.ui.screens.add.AddAccountDialog
import com.example.appopt.ui.screens.home.dialogs.IncomingSharedServiceDetailsDialog
import com.example.appopt.ui.screens.scan.QrScannerDialog
import com.example.appopt.ui.screens.scan.QrScannerMode

/**
 * Contenedor desacoplado de modales y diálogos para la pantalla principal (Directivas 14 y 29).
 *
 * Centraliza la visualización segura, cierre defensivo y eventos de los diálogos de alta,
 * escaneo de códigos QR, detalle y edición de cuentas propias y detalles de servicios compartidos.
 *
 * @param isUnlocked Indica si la bóveda se encuentra actualmente desbloqueada.
 * @param isManualAddDialogOpen Estado del modal de alta manual de cuentas.
 * @param onDismissManualAdd Callback para cerrar el modal de alta manual.
 * @param isQrScannerDialogOpen Estado del modal de escáner de códigos QR.
 * @param onDismissQrScanner Callback para cerrar el modal de escáner QR.
 * @param selectedAccountWithCode Cuenta propia seleccionada para consultar o editar sus detalles.
 * @param onDismissAccountDetails Callback para cerrar el diálogo de detalles de cuenta propia.
 * @param onCopyCode Callback para copiar un código numérico al portapapeles.
 * @param onUpdateAccount Callback para actualizar emisor y nombre de una cuenta.
 * @param onDeleteAccount Callback para trasladar una cuenta a la papelera.
 * @param selectedSharedServiceForDetails Servicio compartido seleccionado para consultar detalles.
 * @param loadingSharedGrantId Identificador de la petición en curso sobre servicios compartidos.
 * @param nowMillis Marca de tiempo UNIX actual para validar la expiración de lotes en el modal.
 * @param onDismissSharedDetails Callback para cerrar el modal de detalles de servicio compartido.
 * @param onRequestSharedCode Callback para renovar lote o solicitar código en tiempo real.
 * @param onRejectSharedInvitation Callback para rechazar o abandonar el servicio compartido.
 */
@Composable
fun HomeDialogContainer(
    isUnlocked: Boolean,
    isManualAddDialogOpen: Boolean,
    onDismissManualAdd: () -> Unit,
    isQrScannerDialogOpen: Boolean,
    onDismissQrScanner: () -> Unit,
    selectedAccountWithCode: AccountWithCode?,
    onDismissAccountDetails: () -> Unit,
    onCopyCode: (String, String) -> Unit,
    onUpdateAccount: (String, String, String) -> Unit,
    onDeleteAccount: (String) -> Unit,
    selectedSharedServiceForDetails: IncomingSharedService?,
    loadingSharedGrantId: String?,
    nowMillis: Long,
    onDismissSharedDetails: () -> Unit,
    onRequestSharedCode: (IncomingSharedService) -> Unit,
    onRejectSharedInvitation: (IncomingSharedService) -> Unit
) {
    if (!isUnlocked) return
    val context = LocalContext.current

    // Modal de Ingreso Manual
    if (isManualAddDialogOpen) {
        AddAccountDialog(
            onDismiss = onDismissManualAdd
        )
    }

    // Modal de Escaneo de Códigos QR (Alta individual de cuentas)
    if (isQrScannerDialogOpen) {
        QrScannerDialog(
            mode = QrScannerMode.SINGLE_ACCOUNT,
            onDismiss = onDismissQrScanner
        )
    }

    // Modal / Popup de Edición y Detalles de la Cuenta propia seleccionada
    selectedAccountWithCode?.let { item ->
        AccountDetailsDialog(
            accountWithCode = item,
            onDismiss = onDismissAccountDetails,
            onCopyCode = { code -> onCopyCode(code, item.account.issuer) },
            onUpdateAccount = onUpdateAccount,
            onDeleteAccount = onDeleteAccount
        )
    }

    // Modal de Detalles para Servicio Compartido recibido
    selectedSharedServiceForDetails?.let { service ->
        val batch = SharedBatchKeyStore.getValidBatch(context, service.grantId, nowMillis)
        IncomingSharedServiceDetailsDialog(
            service = service,
            hasValidBatch = batch != null && !batch.isExpired(nowMillis),
            isLoading = loadingSharedGrantId == service.grantId,
            onDismiss = onDismissSharedDetails,
            onRenewOfflineBatch = { onRequestSharedCode(service) },
            onConfirmLeave = { onRejectSharedInvitation(service) }
        )
    }
}
