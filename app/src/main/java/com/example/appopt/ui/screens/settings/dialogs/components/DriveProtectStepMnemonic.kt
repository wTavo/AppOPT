package com.example.appopt.ui.screens.settings.dialogs.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import com.example.appopt.R
import com.example.appopt.ui.theme.Dimensions

/**
 * Sub-componente para el Paso 2 del diálogo de protección: Visualización y exportación/impresión del Kit de Emergencia BIP-39.
 *
 * Muestra la cuadrícula de las 12 palabras semilla ordenadas y permite copiarlas o imprimirlas en un documento PDF estructurado.
 *
 * @param mnemonicWords Lista de las 12 palabras semilla BIP-39 generadas.
 * @param onCopyWords Callback invocado para copiar la frase completa al portapapeles seguro.
 * @param onPrintPdf Callback invocado para iniciar la impresión/exportación del PDF del Kit de Emergencia.
 * @param modifier Modificador de diseño Compose opcional.
 */
@Composable
fun DriveProtectStepMnemonic(
    mnemonicWords: List<String>,
    onCopyWords: () -> Unit,
    onPrintPdf: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md)
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
}
