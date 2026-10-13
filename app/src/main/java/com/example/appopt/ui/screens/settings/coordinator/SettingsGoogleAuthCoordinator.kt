package com.example.appopt.ui.screens.settings.coordinator

import android.accounts.AccountManager
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
import androidx.compose.ui.platform.LocalContext
import com.example.appopt.AuthenticatorApp
import com.example.appopt.data.cloud.CloudVaultKeyStore
import com.example.appopt.data.cloud.GoogleDriveManager
import com.example.appopt.ui.screens.settings.SettingsViewModel
import com.google.android.gms.auth.api.identity.AuthorizationClient
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Recuerda un solicitador reutilizable de tokens OAuth2 de Google Identity Services con soporte de cambio de cuenta (Directivas 5, 8 y 31).
 *
 * Comportamiento:
 * 1. Si ya existe un token válido en memoria ([GoogleDriveManager.currentAccessToken]), lo entrega de inmediato.
 * 2. Si no hay ninguna cuenta vinculada actualmente (`connectedDriveEmail == null`), despliega primero el selector
 *    nativo de cuentas de Google del sistema (`AccountManager.newChooseAccountIntent`) para que el usuario elija
 *    libremente con qué cuenta vincularse (o cambiar de cuenta tras desvincular).
 * 3. Si el usuario selecciona una cuenta distinta a la anterior (`lastKnownDriveEmail`), purga la clave de bóveda
 *    previa ([CloudVaultKeyStore.clearVaultKey]) para aislar criptográficamente cada cuenta.
 * 4. Solicita el token OAuth2 a [AuthorizationClient] fijando explícitamente la cuenta elegida.
 *
 * @param onAuthStarted Callback opcional invocado al iniciar la solicitud de autorización OAuth2 tras elegir cuenta.
 * @param onAuthError Callback invocado si la autorización falla o es cancelada por el usuario.
 * @return Función `((String) -> Unit) -> Unit` que ejecuta la acción recibiendo el `accessToken` válido.
 */
@Composable
fun rememberGoogleOAuthRequester(
    onAuthStarted: () -> Unit = {},
    onAuthError: () -> Unit = {}
): ((String) -> Unit) -> Unit {
    val context = LocalContext.current
    val prefsManager = remember { AuthenticatorApp.instance.preferencesManager }
    val authClient = remember(context) { GoogleDriveManager.getAuthorizationClient(context) }
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
                    action?.invoke(token)
                } else {
                    pendingAuthAction = null
                    onAuthError()
                }
            } catch (_: ApiException) {
                pendingAuthAction = null
                onAuthError()
            }
        } else {
            pendingAuthAction = null
            onAuthError()
        }
    }

    fun authorizeForAccount(accountEmail: String?) {
        authClient.authorize(GoogleDriveManager.getAuthorizationRequest(accountEmail))
            .addOnSuccessListener { result ->
                if (result.hasResolution()) {
                    val pendingIntent = result.pendingIntent
                    if (pendingIntent != null) {
                        authLauncher.launch(
                            IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                        )
                    } else {
                        pendingAuthAction = null
                        onAuthError()
                    }
                } else {
                    val token = result.accessToken
                    if (token != null) {
                        GoogleDriveManager.currentAccessToken = token
                        val action = pendingAuthAction
                        pendingAuthAction = null
                        action?.invoke(token)
                    } else {
                        pendingAuthAction = null
                        onAuthError()
                    }
                }
            }
            .addOnFailureListener {
                pendingAuthAction = null
                onAuthError()
            }
    }

    val accountPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { activityResult ->
        val selectedEmail = activityResult.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
        if (activityResult.resultCode == Activity.RESULT_OK && !selectedEmail.isNullOrBlank()) {
            onAuthStarted()
            val previousEmail = prefsManager.getLastKnownDriveEmail()
            if (!previousEmail.isNullOrBlank() && !previousEmail.equals(selectedEmail, ignoreCase = true)) {
                // El usuario cambió a otra cuenta de Google: limpiar la clave E2EE y el estado previo de paridad
                CloudVaultKeyStore.clearVaultKey(context)
                prefsManager.setLastKnownSyncedVaultHash(null)
                prefsManager.setVaultModifiedWhileDisconnected(false)
                prefsManager.setAutoSyncEnabled(false)
            } else if (previousEmail.isNullOrBlank()) {
                prefsManager.setLastKnownDriveEmail(selectedEmail)
            }
            prefsManager.setConnectedDriveEmail(selectedEmail)
            authorizeForAccount(selectedEmail)
        } else {
            pendingAuthAction = null
            onAuthError()
        }
    }

    return remember(authClient, onAuthStarted, onAuthError) {
        { onAuthorized ->
            val existingToken = GoogleDriveManager.currentAccessToken
            val connectedEmail = prefsManager.getConnectedDriveEmail()
            if (existingToken != null && prefsManager.isGoogleDriveConnected()) {
                onAuthorized(existingToken)
            } else if (prefsManager.isGoogleDriveConnected()) {
                pendingAuthAction = onAuthorized
                authorizeForAccount(connectedEmail)
            } else {
                pendingAuthAction = onAuthorized
                val chooseIntent = AccountManager.newChooseAccountIntent(
                    null,
                    null,
                    arrayOf("com.google"),
                    null,
                    null,
                    null,
                    null
                )
                accountPickerLauncher.launch(chooseIntent)
            }
        }
    }
}

/**
 * Recuerda y gestiona el launcher de actividades para la autorización OAuth2 de Google Drive en Ajustes.
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
    return rememberGoogleOAuthRequester(
        onAuthStarted = {
            viewModel.setDriveLoading(true)
        },
        onAuthError = {
            viewModel.setDriveLoading(false)
            scope.launch { snackbarHostState.showSnackbar(driveErrorText) }
        }
    )
}
