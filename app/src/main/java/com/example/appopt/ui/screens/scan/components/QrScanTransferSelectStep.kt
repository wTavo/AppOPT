package com.example.appopt.ui.screens.scan.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.example.appopt.R
import com.example.appopt.domain.model.ParsedAccountPreview
import com.example.appopt.ui.components.AccountImportSelectionList
import com.example.appopt.ui.components.AppAnimatedButton
import com.example.appopt.ui.theme.Dimensions

/**
 * Sub-pantalla de selección granular e importación de cuentas desde un lote descifrado.
 *
 * @param parsedAccounts Lista de cuentas previsualizadas extraídas del paquete descifrado.
 * @param selectedAccountIds Conjunto de identificadores de cuentas actualmente seleccionadas.
 * @param onToggleAccount Callback para alternar la selección de una cuenta específica.
 * @param onSelectAll Callback para marcar todas las cuentas.
 * @param onDeselectAll Callback para desmarcar todas las cuentas.
 * @param onBack Callback para retroceder a la cámara o resetear la sesión.
 * @param onImportAccounts Callback asíncrono que realiza la inserción de las cuentas seleccionadas.
 * @param onImportConfirmed Callback invocado tras la confirmación de la importación para cerrar el modal.
 * @param modifier Modificador de diseño Compose.
 */
@Composable
fun QrScanTransferSelectStep(
    parsedAccounts: List<ParsedAccountPreview>,
    selectedAccountIds: Set<String>,
    onToggleAccount: (String) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onBack: () -> Unit,
    onImportAccounts: suspend () -> Boolean,
    onImportConfirmed: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(Dimensions.Spacing.lg)
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md)
    ) {
        // 1. Cabecera fija
        Text(
            text = stringResource(R.string.import_selection_title),
            style = MaterialTheme.typography.titleLarge
        )

        // 2. Cuerpo central scrolleable aislado
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md)
        ) {
            Text(
                text = stringResource(R.string.import_selection_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            AccountImportSelectionList(
                accounts = parsedAccounts,
                selectedIds = selectedAccountIds,
                onToggleAccount = onToggleAccount,
                onSelectAll = onSelectAll,
                onDeselectAll = onDeselectAll
            )
        }

        // 3. Pie fijo de acciones simétricas
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onBack,
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
                text = stringResource(R.string.import_selection_confirm_button, selectedAccountIds.size),
                onClick = onImportAccounts,
                onActionConfirmed = onImportConfirmed,
                enabled = selectedAccountIds.isNotEmpty(),
                modifier = Modifier.weight(1f)
            )
        }
    }
}
