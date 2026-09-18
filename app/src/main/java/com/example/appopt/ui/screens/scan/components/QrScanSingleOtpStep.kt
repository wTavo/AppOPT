package com.example.appopt.ui.screens.scan.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.example.appopt.R
import com.example.appopt.domain.model.OtpType
import com.example.appopt.domain.model.TotpAccount
import com.example.appopt.domain.totp.ParsedOtpData
import com.example.appopt.ui.components.AppAnimatedButton
import com.example.appopt.ui.components.ServiceBrandAvatar
import com.example.appopt.ui.theme.Dimensions
import com.example.appopt.util.SecurityAnalysisUtils

/**
 * Sub-pantalla de confirmación y diagnóstico de seguridad para el alta de una cuenta OTP individual.
 *
 * Características:
 * - Diagnóstico proactivo de homóglifos o caracteres invisibles (*Unicode Spoofing*).
 * - Detección de cuentas preexistentes o duplicadas.
 * - Tarjeta informativa y educativa sobre prevención de phishing.
 * - Acciones simétricas de regreso y guardado animado con confirmación háptica.
 *
 * @param otp Datos analizados del código OTP (`otpauth://`).
 * @param existingAccounts Lista de cuentas existentes en la bóveda para detectar duplicados.
 * @param onBack Callback para descartar y regresar a la vista de cámara.
 * @param onSave Callback asíncrono para persistir la nueva cuenta en Room de forma segura.
 * @param onSaveConfirmed Callback invocado tras confirmar el guardado para desmontar el diálogo.
 * @param modifier Modificador de diseño Compose.
 */
@Composable
fun QrScanSingleOtpStep(
    otp: ParsedOtpData,
    existingAccounts: List<TotpAccount>,
    onBack: () -> Unit,
    onSave: suspend () -> Boolean,
    onSaveConfirmed: () -> Unit,
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
            text = stringResource(R.string.scan_confirm_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth()
        )

        // 2. Cuerpo central scrolleable aislado
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.scan_confirm_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            // Tarjeta visual de identidad del servicio
            Surface(
                shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Dimensions.Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md)
                ) {
                    ServiceBrandAvatar(
                        issuer = otp.issuer,
                        size = Dimensions.IconSize.hero
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = otp.issuer,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = otp.accountName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(Dimensions.Spacing.xs))
                        val technicalInfo = if (otp.type == OtpType.TOTP) {
                            stringResource(
                                R.string.scan_confirm_type_format,
                                otp.type.name,
                                otp.digits,
                                otp.period
                            )
                        } else {
                            stringResource(
                                R.string.scan_confirm_type_hotp_format,
                                otp.type.name,
                                otp.digits
                            )
                        }
                        Text(
                            text = technicalInfo,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Diagnóstico de homóglifos o caracteres invisibles
            val spoofingResult = remember(otp.issuer, otp.accountName) {
                val issuerCheck = SecurityAnalysisUtils.detectUnicodeSpoofing(otp.issuer)
                if (issuerCheck.isSuspicious) issuerCheck else SecurityAnalysisUtils.detectUnicodeSpoofing(otp.accountName)
            }
            if (spoofingResult.isSuspicious) {
                Surface(
                    shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Dimensions.Spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(Dimensions.IconSize.medium)
                        )
                        Text(
                            text = stringResource(R.string.scan_confirm_warning_spoofing),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // Diagnóstico de cuenta existente o duplicada
            val existingDuplicate = remember(otp.issuer, otp.accountName, existingAccounts) {
                SecurityAnalysisUtils.findExistingDuplicate(existingAccounts, otp.issuer, otp.accountName)
            }
            if (existingDuplicate != null) {
                Surface(
                    shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Dimensions.Spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(Dimensions.IconSize.medium)
                        )
                        Text(
                            text = stringResource(
                                R.string.scan_confirm_warning_duplicate,
                                otp.issuer,
                                otp.accountName
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            // Tarjeta educativa antiphishing
            Surface(
                shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Dimensions.Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm)
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(Dimensions.IconSize.medium)
                    )
                    Text(
                        text = stringResource(R.string.scan_confirm_phishing_tip),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
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
                text = stringResource(R.string.scan_confirm_save_button),
                onClick = onSave,
                onActionConfirmed = onSaveConfirmed,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
