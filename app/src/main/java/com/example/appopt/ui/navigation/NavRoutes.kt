package com.example.appopt.ui.navigation

/**
 * Rutas de navegación de primer nivel en Jetpack Compose.
 */
sealed class Screen(val route: String) {
    /** Pantalla principal con la lista de cuentas y códigos en vivo. */
    data object Home : Screen("home")

    /** Pantalla de diagnóstico de seguridad y generación de Recovery Key. */
    data object Settings : Screen("settings")

    /** Pantalla de papelera y servicios eliminados recientemente (30 días). */
    data object RecentlyDeleted : Screen("recently_deleted")
}
