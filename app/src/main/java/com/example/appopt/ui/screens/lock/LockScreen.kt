package com.example.appopt.ui.screens.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.fragment.app.FragmentActivity
import com.example.appopt.AuthenticatorApp
import com.example.appopt.R
import com.example.appopt.ui.theme.Dimensions
import com.example.appopt.ui.theme.rememberAppHaptics

/**
 * Pantalla de bloqueo de seguridad con escala tipográfica estandarizada, respuesta háptica y dimensiones del sistema.
 *
 * Se presenta al iniciar la aplicación o cuando el usuario / timeout del ciclo de vida
 * bloquea la bóveda criptográfica. Exige autenticación biométrica o PIN.
 *
 * @param onUnlocked Callback invocado al autenticar exitosamente la bóveda.
 * @param modifier Modificador de layout.
 */
@Composable
fun LockScreen(
    onUnlocked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appHaptics = rememberAppHaptics()
    val biometricAuthManager = AuthenticatorApp.instance.biometricAuthManager
    val appLockManager = AuthenticatorApp.instance.appLockManager

    val promptTitle = stringResource(R.string.lock_biometric_prompt_title)
    val promptSubtitle = stringResource(R.string.lock_biometric_prompt_subtitle)

    fun triggerAuth() {
        val activity = context as? FragmentActivity ?: return
        biometricAuthManager.authenticate(
            activity = activity,
            title = promptTitle,
            subtitle = promptSubtitle,
            onSuccess = {
                appHaptics.success()
                appLockManager.unlock()
                onUnlocked()
            },
            onError = { _, _ ->
                appHaptics.error()
            },
            onFailed = {
                appHaptics.error()
            }
        )
    }

    LaunchedEffect(Unit) {
        triggerAuth()
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(Dimensions.Spacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = RoundedCornerShape(Dimensions.CornerRadius.pill),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(Dimensions.IconSize.illustration)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(Dimensions.IconSize.hero)
                    )
                }
            }

            Spacer(modifier = Modifier.height(Dimensions.Spacing.xl))

            Text(
                text = stringResource(R.string.lock_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(Dimensions.Spacing.sm))

            Text(
                text = stringResource(R.string.lock_description),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(Dimensions.Spacing.xxl))

            Button(
                onClick = {
                    appHaptics.click()
                    triggerAuth()
                },
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(Dimensions.ComponentHeight.buttonDefault),
                shape = RoundedCornerShape(Dimensions.CornerRadius.medium)
            ) {
                Icon(
                    imageVector = Icons.Filled.Fingerprint,
                    contentDescription = null,
                    modifier = Modifier.size(Dimensions.IconSize.large)
                )
                Spacer(modifier = Modifier.width(Dimensions.Spacing.sm))
                Text(
                    text = stringResource(R.string.action_unlock),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}
