package com.example.appopt

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.appopt.ui.screens.home.components.EmptyAccountsState
import com.example.appopt.ui.theme.AppTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pruebas instrumentadas de interfaz para [EmptyAccountsState].
 */
@RunWith(AndroidJUnit4::class)
class EmptyAccountsStateUiTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<TestActivity>()

    @Test
    fun displaysEmptyStateInformationAndButtons() {
        var scanQrClicked = false
        var addManualClicked = false

        composeTestRule.setContent {
            AppTheme {
                EmptyAccountsState(
                    onScanQr = { scanQrClicked = true },
                    onAddManual = { addManualClicked = true }
                )
            }
        }

        // Verificar textos del estado vacío
        composeTestRule.onNodeWithText("Sin cuentas configuradas").assertIsDisplayed()
        composeTestRule.onNodeWithText(
            "Protege tus servicios con autenticación de dos factores (2FA). Agrega tu primera cuenta escaneando un código QR."
        ).assertIsDisplayed()

        // Verificar botones de acción
        composeTestRule.onNodeWithText("Escanear código QR").assertIsDisplayed()
        composeTestRule.onNodeWithText("Ingresar clave manualmente").assertIsDisplayed()

        // Probar clic en escanear código QR
        composeTestRule.onNodeWithText("Escanear código QR").performClick()
        assertTrue("El callback onScanQr debió haberse ejecutado", scanQrClicked)

        // Probar clic en ingresar clave manual
        composeTestRule.onNodeWithText("Ingresar clave manualmente").performClick()
        assertTrue("El callback onAddManual debió haberse ejecutado", addManualClicked)
    }
}
