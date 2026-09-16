package com.example.appopt

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.appopt.ui.screens.home.components.EmptyAccountsState
import com.example.appopt.ui.theme.AppTheme
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
    fun displaysEmptyStateInformation() {
        composeTestRule.setContent {
            AppTheme {
                EmptyAccountsState()
            }
        }

        // Verificar textos del estado vacío
        composeTestRule.onNodeWithText("Sin servicios registrados").assertIsDisplayed()
        composeTestRule.onNodeWithText(
            "Protege tus servicios con autenticación de dos factores (2FA)."
        ).assertIsDisplayed()
    }
}
