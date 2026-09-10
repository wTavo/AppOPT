package com.example.appopt.ui.screens.scan.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.example.appopt.R
import com.example.appopt.ui.theme.Dimensions

/**
 * Superposición visual con marco guía para encuadrar códigos QR y tarjeta de estado reactiva.
 *
 * @param totalExpectedChunks Cantidad total esperada de fragmentos en transferencia multi-QR.
 * @param currentChunksCount Cantidad de fragmentos capturados hasta el momento.
 * @param totalImportedAccountsCount Total de cuentas importadas exitosamente en la sesión actual.
 * @param modifier Modificador de diseño Compose.
 */
@Composable
fun QrScannerOverlay(
    totalExpectedChunks: Int,
    currentChunksCount: Int,
    totalImportedAccountsCount: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Dimensions.Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(Dimensions.ComponentSize.qrScannerBox)
                .clip(RoundedCornerShape(Dimensions.CornerRadius.large))
                .border(
                    width = Dimensions.Stroke.thick,
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(Dimensions.CornerRadius.large)
                )
        )

        Spacer(modifier = Modifier.height(Dimensions.Spacing.xl))

        Card(
            colors = CardDefaults.cardColors(
                containerColor = Color.Black.copy(alpha = 0.7f)
            ),
            shape = RoundedCornerShape(Dimensions.CornerRadius.medium)
        ) {
            val statusText = when {
                totalExpectedChunks > 1 && currentChunksCount < totalExpectedChunks -> {
                    stringResource(R.string.scan_transfer_chunk_progress, currentChunksCount, totalExpectedChunks)
                }
                totalImportedAccountsCount > 0 -> {
                    stringResource(R.string.scan_transfer_import_success, totalImportedAccountsCount)
                }
                else -> {
                    stringResource(R.string.scan_hint)
                }
            }
            Text(
                text = statusText,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(
                    horizontal = Dimensions.Spacing.lg,
                    vertical = Dimensions.Spacing.sm
                )
            )
        }
    }
}
