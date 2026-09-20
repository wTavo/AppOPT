package com.example.appopt.ui.theme

import androidx.compose.ui.graphics.Color

/** Paleta de colores primarios y de acento de la aplicación. */
val PrimaryBlue = Color(0xFF2563EB)
val PrimaryBlueDark = Color(0xFF4B82DB) // Azul medio refinado y luminoso para tema oscuro
val AccentCyan = Color(0xFF06B6D4)

/** Colores semánticos de estado (Urgencia, Advertencia, Éxito, Espera). */
val UrgentRed = Color(0xFFEF4444)
val WarningOrange = Color(0xFFF59E0B)
val WarningYellow = Color(0xFFEAB308)
val WarningYellowLight = Color(0xFFFDE047)
val SafeGreen = Color(0xFF10B981)

/** Colores para el tema oscuro (Dark Theme) con alto contraste. */
val BackgroundDark = Color(0xFF0B1120) // Fondo profundo azabache
val SurfaceDark = Color(0xFF1E293B)    // Superficie de tarjeta con alto contraste
val SurfaceVariantDark = Color(0xFF334155)
val OutlineDark = Color(0xFF64748B) // Slate-500: bordes visibles en modo oscuro
val OutlineVariantDark = Color(0xFF334155) // Slate-700: divisores sutiles en modo oscuro
val TextPrimaryDark = Color(0xFFF8FAFC)
val TextSecondaryDark = Color(0xFF94A3B8)
val PrimaryContainerDark = Color(0xFF1E3A8A)
val OnPrimaryContainerDark = Color(0xFFDBEAFE)
val ErrorContainerDark = Color(0xFF450A0A)
val OnErrorContainerDark = Color(0xFFFEE2E2)

/** Colores para el tema claro (Light Theme) con alto contraste y luminosidad limpia. */
val BackgroundLight = Color(0xFFE8EEF7) // Azul hielo suave saturado: fondo de pantalla con presencia cromática y relieve frente a tarjetas
val SurfaceLight = Color(0xFFFFFFFF)    // Superficie de tarjeta blanco puro desaturado de alto contraste
val SurfaceVariantLight = Color(0xFFDCE6F5) // Contenedor suave con sutil matiz zafiro hielo
val OutlineLight = Color(0xFF94A3B8)    // Slate-400: contorno visible y nítido para campos de texto
val OutlineVariantLight = Color(0xFFCAD7E8) // Bordes secundarios y divisores sutiles zafiro hielo
val TextPrimaryLight = Color(0xFF0F172A) // Slate-900: texto de alto impacto y máxima legibilidad
val TextSecondaryLight = Color(0xFF475569) // Slate-600: texto secundario nítido con alto contraste
val PrimaryContainerLight = Color(0xFFDBEAFE) // Blue-100: contenedor primario claro y limpio
val OnPrimaryContainerLight = Color(0xFF1E40AF) // Blue-800: texto sobre contenedor primario
val ErrorContainerLight = Color(0xFFFEE2E2) // Red-100: contenedor de error suave y visible
val OnErrorContainerLight = Color(0xFF991B1B) // Red-800: texto sobre contenedor de error

/** Colores de superficie especializados para diálogos y modales flotantes (Slate Frío Equilibrado). */
val ModalSurfaceLight = Color(0xFFFFFFFF) // Blanco puro sobre velo oscurecido al 50%
val ModalSurfaceDark = Color(0xFF1A2436)  // Slate-850: superficie modal carbón azulado con alto contraste
