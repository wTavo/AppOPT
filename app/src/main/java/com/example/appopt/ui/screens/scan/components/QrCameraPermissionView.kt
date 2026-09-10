package com.example.appopt.ui.screens.scan.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.example.appopt.R
import com.example.appopt.ui.theme.Dimensions
import com.example.appopt.ui.theme.rememberAppHaptics

/**
 * Vista informativa y accionable mostrada cuando no se ha otorgado el permiso de cámara.
 *
 * @param onRequestPermission Callback para lanzar el diálogo de solicitud de permisos del sistema.
 * @param onNavigateToManual Callback para optar por la adición manual de cuentas.
 * @param modifier Modificador de diseño Compose.
 */
@Composable
fun QrCameraPermissionView(
    onRequestPermission: () -> Unit,
    onNavigateToManual: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appHaptics = rememberAppHaptics()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Dimensions.Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.CameraAlt,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(Dimensions.IconSize.hero)
        )

        Spacer(modifier = Modifier.height(Dimensions.Spacing.lg))

        Text(
            text = stringResource(R.string.scan_permission_required_title),
            color = Color.White,
            style = MaterialTheme.typography.titleLarge
        )

        Spacer(modifier = Modifier.height(Dimensions.Spacing.sm))

        Text(
            text = stringResource(R.string.scan_permission_required_description),
            color = Color.LightGray,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(Dimensions.Spacing.xl))

        Button(
            onClick = {
                appHaptics.click()
                onRequestPermission()
            },
            shape = RoundedCornerShape(Dimensions.CornerRadius.medium)
        ) {
            Text(
                text = stringResource(R.string.action_grant_permission),
                style = MaterialTheme.typography.labelLarge
            )
        }

        Spacer(modifier = Modifier.height(Dimensions.Spacing.md))

        OutlinedButton(
            onClick = {
                appHaptics.click()
                onNavigateToManual()
            },
            shape = RoundedCornerShape(Dimensions.CornerRadius.medium)
        ) {
            Text(
                text = stringResource(R.string.home_add_manual_option),
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}
