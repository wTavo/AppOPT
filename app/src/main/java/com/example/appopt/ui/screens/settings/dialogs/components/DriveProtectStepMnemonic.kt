package com.example.appopt.ui.screens.settings.dialogs.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import com.example.appopt.R
import com.example.appopt.ui.theme.Dimensions

/**
 * Sub-componente para el Paso 2 del diálogo de protección: Visualización y exportación del Kit de Emergencia.
 *
 * Estructura claramente organizada:
 * 1. Texto descriptivo del kit de recuperación.
 * 2. Sección 1: Método principal de acceso con previsualización del paso 1 (oculto por defecto con alternancia).
 * 3. Sección 2: Frase de emergencia con cuadrícula de las 12 palabras BIP-39.
 * 4. Aviso de seguridad de respaldo.
 * 5. Botones simétricos: Copiar frase (con temporizador reactivo) e Imprimir kit.
 *
 * @param mnemonicWords Lista de las 12 palabras semilla BIP-39 generadas.
 * @param primaryMethodLabel Nombre del método principal seleccionado en el paso 1 (ej. "Contraseña maestra").
 * @param primaryMethodValue Valor del método principal: texto de contraseña o clave de 64 dígitos.
 * @param isPasswordMethod `true` si el método seleccionado es contraseña maestra; `false` si es clave de 64 dígitos.
 * @param copyCountdown Segundos restantes de retención en el portapapeles seguro (0 cuando no está copiado).
 * @param onCopyWords Callback invocado para copiar la frase completa al portapapeles seguro.
 * @param onPrintPdf Callback invocado para iniciar la impresión del Kit de Emergencia.
 * @param modifier Modificador de diseño Compose opcional.
 */
@Composable
fun DriveProtectStepMnemonic(
    mnemonicWords: List<String>,
    primaryMethodLabel: String,
    primaryMethodValue: String,
    isPasswordMethod: Boolean,
    copyCountdown: Int,
    onCopyWords: () -> Unit,
    onPrintPdf: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isPrimaryVisible by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md)
    ) {
        // Descripción general del kit de recuperación
        Text(
            text = stringResource(R.string.settings_drive_emergency_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Sección 1: Método principal de acceso (Paso 1)
        Column(verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.xs)) {
            Text(
                text = stringResource(R.string.settings_drive_summary_primary_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            Surface(
                shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f),
                border = BorderStroke(Dimensions.Stroke.thin, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(Dimensions.Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.xs)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.xs)
                        ) {
                            Icon(
                                imageVector = if (isPasswordMethod) Icons.Filled.Password else Icons.Filled.Key,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(Dimensions.IconSize.small)
                            )
                            Text(
                                text = primaryMethodLabel,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        IconButton(
                            onClick = { isPrimaryVisible = !isPrimaryVisible },
                            modifier = Modifier.size(Dimensions.ComponentSize.actionIconButton)
                        ) {
                            Icon(
                                imageVector = if (isPrimaryVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    if (isPasswordMethod) {
                        Text(
                            text = if (isPrimaryVisible) {
                                primaryMethodValue
                            } else {
                                stringResource(R.string.settings_drive_password_preview_value)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    } else {
                        Text(
                            text = if (isPrimaryVisible) {
                                primaryMethodValue.chunked(16).joinToString("\n")
                            } else {
                                "••••••••••••••••\n••••••••••••••••\n••••••••••••••••\n••••••••••••••••"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // Sección 2: Frase de emergencia (12 palabras BIP-39)
        Column(verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.xs)) {
            Text(
                text = stringResource(R.string.settings_drive_summary_emergency_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            Surface(
                shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                border = BorderStroke(Dimensions.Stroke.thin, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Dimensions.Spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm)
                ) {
                    // Columna izquierda: Palabras 1 al 6
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.xs)
                    ) {
                        for (i in 0 until 6) {
                            val word = mnemonicWords.getOrElse(i) { "" }
                            MnemonicWordCell(wordIndex = i + 1, word = word)
                        }
                    }

                    // Columna derecha: Palabras 7 al 12
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.xs)
                    ) {
                        for (i in 6 until 12) {
                            val word = mnemonicWords.getOrElse(i) { "" }
                            MnemonicWordCell(wordIndex = i + 1, word = word)
                        }
                    }
                }
            }
        }

        // Aviso de seguridad
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.xs)
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(Dimensions.IconSize.small)
            )
            Text(
                text = stringResource(R.string.settings_drive_words_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        // Botones de acción simétricos y estandarizados
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm)
        ) {
            OutlinedButton(
                onClick = onCopyWords,
                enabled = copyCountdown <= 0,
                shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                modifier = Modifier
                    .weight(1f)
                    .height(Dimensions.ComponentHeight.buttonDefault)
            ) {
                Icon(
                    imageVector = if (copyCountdown > 0) Icons.Filled.Timer else Icons.Filled.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(Dimensions.IconSize.small)
                )
                Spacer(modifier = Modifier.width(Dimensions.Spacing.xs))
                Text(
                    text = if (copyCountdown > 0) {
                        stringResource(R.string.settings_drive_copied_countdown, copyCountdown)
                    } else {
                        stringResource(R.string.settings_drive_copy_phrase_btn)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1
                )
            }

            OutlinedButton(
                onClick = onPrintPdf,
                shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                modifier = Modifier
                    .weight(1f)
                    .height(Dimensions.ComponentHeight.buttonDefault)
            ) {
                Icon(
                    imageVector = Icons.Filled.Print,
                    contentDescription = null,
                    modifier = Modifier.size(Dimensions.IconSize.small)
                )
                Spacer(modifier = Modifier.width(Dimensions.Spacing.xs))
                Text(
                    text = stringResource(R.string.settings_drive_print_pdf_short),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Celda individual de palabra mnemónica con número formateado y tipografía monoespaciada.
 *
 * @param wordIndex Posición ordinal de la palabra dentro de la frase BIP-39 (1–12).
 * @param word Texto de la palabra semilla.
 * @param modifier Modificador de diseño Compose opcional.
 */
@Composable
private fun MnemonicWordCell(
    wordIndex: Int,
    word: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(Dimensions.CornerRadius.small),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(Dimensions.Stroke.thin, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Dimensions.Spacing.sm, vertical = Dimensions.Spacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${wordIndex}.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(Dimensions.Spacing.xl)
            )
            Text(
                text = word,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
