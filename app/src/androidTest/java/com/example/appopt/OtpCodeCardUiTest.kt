package com.example.appopt

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.appopt.domain.model.OtpAlgorithm
import com.example.appopt.domain.model.OtpType
import com.example.appopt.domain.model.TotpAccount
import com.example.appopt.domain.repository.AccountWithCode
import com.example.appopt.ui.components.OtpCodeCard
import com.example.appopt.ui.theme.AppTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pruebas instrumentadas de interfaz para [OtpCodeCard].
 */
@RunWith(AndroidJUnit4::class)
class OtpCodeCardUiTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<TestActivity>()

    @Test
    fun displaysAccountDataAndFormattedOtpCode() {
        val sampleAccount = TotpAccount(
            id = "test-id-1",
            issuer = "GitHub",
            accountName = "octocat@github.com",
            type = OtpType.TOTP,
            algorithm = OtpAlgorithm.SHA1,
            digits = 6,
            period = 30
        )
        val sampleWithCode = AccountWithCode(sampleAccount, "123456")

        var cardClicked = false
        var copiedCode = ""
        var favoriteToggledId = ""

        composeTestRule.setContent {
            AppTheme {
                OtpCodeCard(
                    accountWithCode = sampleWithCode,
                    hideCodes = false,
                    onCardClick = { cardClicked = true },
                    onCopyCode = { code -> copiedCode = code },
                    onToggleFavorite = { id -> favoriteToggledId = id },
                    onNextHotpCode = {}
                )
            }
        }

        // Verificar que emisor y cuenta sean visibles
        composeTestRule.onNodeWithText("GitHub").assertIsDisplayed()
        composeTestRule.onNodeWithText("octocat@github.com").assertIsDisplayed()

        // Verificar que el código formateado "123 456" esté visible
        composeTestRule.onNodeWithText("123 456").assertIsDisplayed()

        // Probar clic en la tarjeta
        composeTestRule.onNodeWithText("GitHub").performClick()
        assertTrue("onCardClick debió ejecutarse", cardClicked)
    }

    @Test
    fun hideCodes_collapsesCodeDisplay() {
        val sampleAccount = TotpAccount(
            id = "test-id-2",
            issuer = "Google",
            accountName = "user@gmail.com"
        )
        val sampleWithCode = AccountWithCode(sampleAccount, "654321")

        composeTestRule.setContent {
            AppTheme {
                OtpCodeCard(
                    accountWithCode = sampleWithCode,
                    hideCodes = true,
                    onCardClick = {},
                    onCopyCode = {},
                    onToggleFavorite = {},
                    onNextHotpCode = {}
                )
            }
        }

        // El emisor debe estar visible
        composeTestRule.onNodeWithText("Google").assertIsDisplayed()
        composeTestRule.onNodeWithText("user@gmail.com").assertIsDisplayed()

        // El código "654 321" no debe existir en la jerarquía visual activa
        composeTestRule.onNodeWithText("654 321").assertDoesNotExist()
    }
}
