package com.example.appopt.ui.screens.add.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import com.example.appopt.R
import com.example.appopt.domain.model.OtpAlgorithm
import com.example.appopt.ui.theme.Dimensions
import com.example.appopt.ui.theme.rememberAppHaptics

/**
 * Panel desplegable de opciones avanzadas para la creación manual de cuenta (algoritmo HMAC y cantidad de dígitos) (Directiva 29).
 *
 * @param showAdvancedOptions Estado de expansión del panel.
 * @param onToggleAdvancedOptions Callback para alternar la expansión.
 * @param selectedAlgorithm Algoritmo HMAC seleccionado.
 * @param onAlgorithmChange Callback al cambiar el algoritmo.
 * @param selectedDigits Cantidad de dígitos seleccionada.
 * @param onDigitsChange Callback al cambiar los dígitos.
 * @param modifier Modificador de diseño.
 */
@Composable
fun AddAccountAdvancedOptions(
    showAdvancedOptions: Boolean,
    onToggleAdvancedOptions: () -> Unit,
    selectedAlgorithm: OtpAlgorithm,
    onAlgorithmChange: (OtpAlgorithm) -> Unit,
    selectedDigits: Int,
    onDigitsChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val appHaptics = rememberAppHaptics()

    Card(
        shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimensions.CornerRadius.medium))
            .clickable {
                appHaptics.click()
                onToggleAdvancedOptions()
            }
    ) {
        Column(
            modifier = Modifier.padding(Dimensions.Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.xs)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.add_account_advanced_options),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    imageVector = if (showAdvancedOptions) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(
                visible = showAdvancedOptions,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier.padding(top = Dimensions.Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm)
                ) {
                    Text(
                        text = stringResource(R.string.add_account_algorithm_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm)
                    ) {
                        OtpAlgorithm.entries.forEach { algorithm ->
                            FilterChip(
                                selected = selectedAlgorithm == algorithm,
                                onClick = {
                                    appHaptics.click()
                                    onAlgorithmChange(algorithm)
                                },
                                label = { Text(algorithm.name, style = MaterialTheme.typography.labelMedium) }
                            )
                        }
                    }

                    Text(
                        text = stringResource(R.string.add_account_digits_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm)
                    ) {
                        listOf(6, 8).forEach { digits ->
                            FilterChip(
                                selected = selectedDigits == digits,
                                onClick = {
                                    appHaptics.click()
                                    onDigitsChange(digits)
                                },
                                label = {
                                    Text(
                                        stringResource(R.string.add_account_digits_format, digits),
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
