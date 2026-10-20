package com.example.appopt.ui.screens.home.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.example.appopt.R
import com.example.appopt.domain.model.AppUpdateInfo
import com.example.appopt.ui.components.AppModalDialog
import com.example.appopt.ui.components.ModalTone
import com.example.appopt.ui.theme.Dimensions
import com.example.appopt.ui.theme.isDarkTheme
import com.example.appopt.ui.theme.modalCardContainerColor
import com.example.appopt.ui.theme.rememberAppHaptics

/**
 * Diálogo emergente modal presentado en la pantalla principal al detectar una nueva versión disponible (Directivas 1, 2, 4, 7, 10, 14, 23 y 34).
 *
 * Estructura tripartita inmutable:
 * 1. Cabecera fija superior: Icono hero de actualización, título de versión disponible y peso del paquete.
 * 2. Cuerpo central scrolleable aislado: Novedades de la versión formateadas de manera limpia sin marcas Markdown crudas.
 * 3. Pie fijo inferior con simetría equitativa (50/50): Botón de descarte («Más tarde») y acción («Actualizar ahora»).
 *
 * @param updateInfo Metadatos de la actualización remota disponible.
 * @param onDismiss Callback invocado al descartar el diálogo («Más tarde» o pulsar afuera).
 * @param onUpdateNow Callback invocado para navegar a Ajustes e iniciar la instalación de la actualización.
 * @param modifier Modificador de diseño Compose opcional.
 */
@Composable
fun AppUpdateAvailableDialog(
    updateInfo: AppUpdateInfo,
    onDismiss: () -> Unit,
    onUpdateNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appHaptics = rememberAppHaptics()
    val formattedNotes = remember(updateInfo) {
        updateInfo.getFormattedReleaseNotes()
    }

    AppModalDialog(
        onDismissRequest = onDismiss,
        tone = ModalTone.STANDARD,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimensions.Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md)
        ) {
            // 1. Cabecera fija superior
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.xs)
            ) {
                Surface(
                    shape = RoundedCornerShape(Dimensions.CornerRadius.pill),
                    color = if (MaterialTheme.colorScheme.isDarkTheme) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(Dimensions.IconSize.hero)
                ) {
                    Icon(
                        imageVector = Icons.Filled.SystemUpdate,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(Dimensions.Spacing.sm)
                            .size(Dimensions.IconSize.medium)
                    )
                }

                Text(
                    text = stringResource(R.string.update_dialog_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = stringResource(
                        R.string.settings_update_status_available,
                        updateInfo.tagName,
                        updateInfo.formattedSizeMb()
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
            }

            // 2. Cuerpo central scrolleable aislado
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.xs)
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                    color = modalCardContainerColor(),
                    border = BorderStroke(
                        width = Dimensions.Stroke.thin,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(Dimensions.Spacing.md),
                        verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.xs)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_update_release_notes_title),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        if (formattedNotes.isNotEmpty()) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.xs)
                            ) {
                                formattedNotes.forEach { noteLine ->
                                    Text(
                                        text = noteLine,
                                        style = if (noteLine.endsWith(":")) {
                                            MaterialTheme.typography.labelMedium
                                        } else {
                                            MaterialTheme.typography.bodySmall
                                        },
                                        fontWeight = if (noteLine.endsWith(":")) {
                                            FontWeight.SemiBold
                                        } else {
                                            FontWeight.Normal
                                        },
                                        color = if (noteLine.endsWith(":")) {
                                            MaterialTheme.colorScheme.onSurface
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = stringResource(R.string.settings_update_release_notes_fallback),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 3. Pie fijo inferior con simetría equitativa (50/50, Directivas 14 y 23)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm)
            ) {
                TextButton(
                    onClick = {
                        appHaptics.click()
                        onDismiss()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(Dimensions.ComponentHeight.buttonDefault),
                    shape = RoundedCornerShape(Dimensions.CornerRadius.medium)
                ) {
                    Text(
                        text = stringResource(R.string.update_dialog_later),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }

                Button(
                    onClick = {
                        appHaptics.click()
                        onUpdateNow()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(Dimensions.ComponentHeight.buttonDefault),
                    shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(
                        text = stringResource(R.string.update_dialog_action),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
