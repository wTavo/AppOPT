package com.example.appopt.ui.screens.settings.coordinator

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.appopt.data.cloud.GoogleDriveManager
import com.example.appopt.ui.screens.settings.SettingsViewModel
import com.google.android.gms.auth.api.identity.AuthorizationClient
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Recuerda y gestiona el launcher de actividades para la autorización OAuth2 de Google Drive.
 *
 * Desacopla la interacción con Google Identity Services y [ActivityResultContracts] (Directivas 8 y 29).
 *
 * @param authClient Cliente de autorización de Google Identity Services.
 * @param viewModel ViewModel de ajustes para notificar conexión exitosa.
 * @param scope Alcance de corrutinas para emitir mensajes.
 * @param snackbarHostState Estado del Snackbar para retroalimentación visual.
 * @param driveErrorText Mensaje de error localizado.
 * @return Función lambda para solicitar autorización de Google.
 */
@Composable
fun rememberGoogleDriveAuth(
    authClient: AuthorizationClient,
    viewModel: SettingsViewModel,
    scope: CoroutineScope,
    snackbarHostState: SnackbarHostState,
    driveErrorText: String
): ((String) -> Unit) -> Unit {
    var pendingAuthAction by remember { mutableStateOf<((String) -> Unit)?>(null) }
    val authLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { activityResult ->
        if (activityResult.resultCode == Activity.RESULT_OK) {
            try {
                val authResult = authClient.getAuthorizationResultFromIntent(activityResult.data)
                val token = authResult.accessToken
                if (token != null) {
                    GoogleDriveManager.currentAccessToken = token
                    val action = pendingAuthAction
                    pendingAuthAction = null
                    if (action != null) {
                        action(token)
                    } else {
                        viewModel.onGoogleDriveConnected(token)
                    }
                }
            } catch (_: ApiException) {
                pendingAuthAction = null
                scope.launch { snackbarHostState.showSnackbar(driveErrorText) }
            }
        } else {
            pendingAuthAction = null
        }
    }

    return remember(authClient, viewModel, scope, snackbarHostState, driveErrorText) {
        { onAuthorized ->
            pendingAuthAction = onAuthorized
            authClient.authorize(GoogleDriveManager.getAuthorizationRequest())
                .addOnSuccessListener { result ->
                    if (result.hasResolution()) {
                        val pendingIntent = result.pendingIntent
                        if (pendingIntent != null) {
                            authLauncher.launch(
                                IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                            )
                        }
                    } else {
                        val token = result.accessToken
                        if (token != null) {
                            val action = pendingAuthAction
                            pendingAuthAction = null
                            action?.invoke(token)
                        }
                    }
                }
                .addOnFailureListener {
                    pendingAuthAction = null
                    scope.launch { snackbarHostState.showSnackbar(driveErrorText) }
                }
        }
    }
}
