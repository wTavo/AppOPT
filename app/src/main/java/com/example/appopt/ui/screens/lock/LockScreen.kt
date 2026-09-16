package com.example.appopt.ui.screens.lock

import android.content.Intent
import android.provider.Settings
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
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.appopt.AuthenticatorApp
import com.example.appopt.R
import com.example.appopt.ui.theme.Dimensions
import com.example.appopt.ui.theme.rememberAppHaptics

/**
 * Pantalla de bloqueo de seguridad con escala tipográfica estandarizada, respuesta háptica y dimensiones del sistema.
 *
 * Principio de diseño y seguridad:
 * - Se presenta al iniciar la aplicación o cuando el usuario / timeout del ciclo de vida bloquea la bóveda criptográfica.
 * - Detecta proactivamente si el dispositivo cuenta con bloqueo de pantalla (PIN, patrón o biometría).
 * - Si el dispositivo no tiene credenciales configuradas, orienta al usuario para proteger su teléfono en los Ajustes del sistema.
 * - Al detectar credenciales activas, invoca automáticamente la autenticación biométrica de forma instantánea.
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
    val lifecycleOwner = LocalLifecycleOwner.current
    val appHaptics = rememberAppHaptics()
    val biometricAuthManager = AuthenticatorApp.instance.biometricAuthManager
    val appLockManager = AuthenticatorApp.instance.appLockManager

    val promptTitle = stringResource(R.string.lock_biometric_prompt_title)
    val promptSubtitle = stringResource(R.string.lock_biometric_prompt_subtitle)

    var isDeviceSecure by remember { mutableStateOf(biometricAuthManager.isDeviceSecure(context)) }

    /**
     * Inicia el diálogo de autenticación biométrica o credencial del sistema operativo.
     */
    fun triggerAuth() {
        val activity = context as? FragmentActivity ?: return
        if (!biometricAuthManager.isDeviceSecure(context)) {
            isDeviceSecure = false
            return
        }

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

    // Re-evaluar proactivamente el estado de seguridad al volver a primer plano
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val secure = biometricAuthManager.isDeviceSecure(context)
            isDeviceSecure = secure
            if (secure) {
                triggerAuth()
            }
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        if (isDeviceSecure) {
            // Estado 1: Dispositivo seguro con PIN/Biometría activa
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
        } else {
            // Estado 2: Dispositivo sin bloqueo de pantalla configurado
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
                            imageVector = Icons.Filled.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(Dimensions.IconSize.hero)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Dimensions.Spacing.xl))

                Text(
                    text = stringResource(R.string.lock_security_required_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(Dimensions.Spacing.sm))

                Text(
                    text = stringResource(R.string.lock_security_required_description),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(Dimensions.Spacing.xxl))

                Button(
                    onClick = {
                        appHaptics.click()
                        try {
                            val intent = Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            try {
                                val fallbackIntent = Intent(Settings.ACTION_SETTINGS).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(fallbackIntent)
                            } catch (_: Exception) {}
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(Dimensions.ComponentHeight.buttonDefault),
                    shape = RoundedCornerShape(Dimensions.CornerRadius.medium)
                ) {
                    Text(
                        text = stringResource(R.string.lock_security_configure_action),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}
