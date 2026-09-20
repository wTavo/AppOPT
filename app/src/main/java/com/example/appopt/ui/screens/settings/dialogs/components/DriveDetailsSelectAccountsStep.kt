package com.example.appopt.ui.screens.settings.dialogs.components

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
 * Paso modal para seleccionar granularmente qué cuentas restaurar a partir del respaldo descifrado (Directivas 14 y 29).
 *
 * @param parsedAccounts Lista de cuentas previsualizadas extraídas del respaldo.
 * @param selectedAccountIds Conjunto de identificadores de cuentas seleccionadas.
 * @param onToggleAccount Callback al marcar/desmarcar una cuenta.
 * @param onSelectAll Callback para seleccionar todas las cuentas.
 * @param onDeselectAll Callback para desmarcar todas las cuentas.
 * @param onConfirmImport Callback para ejecutar la restauración de las cuentas seleccionadas.
 * @param onActionConfirmed Callback invocado cuando la animación de guardado finaliza.
 * @param onBack Callback para regresar al paso de descifrado.
 * @param backText Texto a mostrar en el botón de descarte/regreso (por defecto «Volver»).
 */
@Composable
fun DriveDetailsSelectAccountsStep(
    parsedAccounts: List<ParsedAccountPreview>,
    selectedAccountIds: Set<String>,
    onToggleAccount: (String) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onConfirmImport: () -> Boolean,
    onActionConfirmed: () -> Unit,
    onBack: () -> Unit,
    backText: String = stringResource(R.string.settings_drive_details_back)
) {
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
                onToggleAccount = onToggleAccount,
                onSelectAll = onSelectAll,
                onDeselectAll = onDeselectAll
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
                onClick = onBack,
                shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                contentPadding = PaddingValues(horizontal = Dimensions.Spacing.md, vertical = Dimensions.Spacing.none),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .heightIn(min = Dimensions.ComponentHeight.buttonDefault)
            ) {
                Text(
                    text = backText,
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center
                )
            }

            AppAnimatedButton(
                text = stringResource(R.string.drive_restore_confirm_button, selectedAccountIds.size),
                onClick = onConfirmImport,
                onActionConfirmed = onActionConfirmed,
                enabled = selectedAccountIds.isNotEmpty(),
                modifier = Modifier.weight(1f)
            )
        }
    }
}
