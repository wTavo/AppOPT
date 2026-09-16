package com.example.appopt

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.appopt.ui.screens.add.AddAccountDialog
import com.example.appopt.ui.theme.AppTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pruebas instrumentadas de interfaz para el formulario y validaciones en [AddAccountDialog].
 */
@RunWith(AndroidJUnit4::class)
class AddAccountDialogUiTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<TestActivity>()

    @Test
    fun displaysFormElementsAndHandlesInteractions() {
        var dismissClicked = false

        composeTestRule.setContent {
            AppTheme {
                AddAccountDialog(
                    onDismiss = { dismissClicked = true }
                )
            }
        }

        // Verificar título y etiquetas principales
        composeTestRule.onNodeWithText("Ingresar clave manual").assertIsDisplayed()
        composeTestRule.onNodeWithText("Servicio o emisor").assertIsDisplayed()
        composeTestRule.onNodeWithText("Clave secreta").assertIsDisplayed()
        composeTestRule.onNodeWithText("Opciones avanzadas").assertIsDisplayed()

        // Probar desplegar opciones avanzadas
        composeTestRule.onNodeWithText("Opciones avanzadas").performClick()
        composeTestRule.onNodeWithText("Algoritmo HMAC").assertIsDisplayed()
        composeTestRule.onNodeWithText("Dígitos").assertIsDisplayed()

        // Probar botón de cerrar y esperar transición de repliegue
        composeTestRule.onNodeWithText("Cerrar").performClick()
        composeTestRule.waitUntil(timeoutMillis = 3000) { dismissClicked }
        assertTrue("El callback onDismiss debió haberse ejecutado tras la animación", dismissClicked)
    }

    @Test
    fun invalidSecret_displaysInvalidIndicator() {
        composeTestRule.setContent {
            AppTheme {
                AddAccountDialog(
                    onDismiss = {}
                )
            }
        }

        // Ingresar un secreto con caracteres no Base32 (e.g. '890!')
        composeTestRule.onNodeWithText("Clave secreta").performTextInput("890!")
        composeTestRule.onNodeWithContentDescription("Inválido").assertIsDisplayed()
        composeTestRule.onNodeWithText("Clave Base32 no válida").assertIsDisplayed()
    }

    @Test
    fun validSecret_displaysValidIndicator() {
        composeTestRule.setContent {
            AppTheme {
                AddAccountDialog(
                    onDismiss = {}
                )
            }
        }

        // Ingresar un secreto Base32 válido (e.g. 'JBSWY3DPEHPK3PXP')
        composeTestRule.onNodeWithText("Clave secreta").performTextInput("JBSWY3DPEHPK3PXP")
        composeTestRule.onNodeWithContentDescription("Válido").assertIsDisplayed()
    }
}
