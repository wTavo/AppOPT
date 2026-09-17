package com.example.appopt.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Sistema centralizado de dimensiones, espaciados, elevaciones y tamaños de componentes visuales.
 *
 * Directiva del proyecto:
 * - PROHIBIDO quemar valores de padding, elevación o radios de bordes arbitrarios en los Composables.
 * - OBLIGATORIO consumir las dimensiones estándar definidas en este archivo para garantizar una interfaz coherente.
 */
object Dimensions {

    /**
     * Escala estandarizada de espaciados y márgenes interiores (*Padding / Margins*).
     */
    object Spacing {
        /** Sin espaciado ni margen (0.dp). */
        val none: Dp = 0.dp

        /** Micro espaciado entre iconos y textos adyacentes (4.dp). */
        val xs: Dp = 4.dp

        /** Espaciado pequeño entre elementos relacionados (8.dp). */
        val sm: Dp = 8.dp

        /** Espaciado medio estándar para separaciones de lista y bloques (12.dp). */
        val md: Dp = 12.dp

        /** Espaciado amplio para márgenes de pantalla y contenido de tarjetas (16.dp). */
        val lg: Dp = 16.dp

        /** Espaciado extra amplio para separaciones entre secciones (24.dp). */
        val xl: Dp = 24.dp

        /** Espaciado máximo para estados vacíos y encabezados de pantalla (32.dp). */
        val xxl: Dp = 32.dp
    }

    /**
     * Escala de elevaciones y sombras (*Z-Axis Elevation*).
     */
    object Elevation {
        /** Sin elevación (plano sobre la superficie) (0.dp). */
        val none: Dp = 0.dp

        /** Elevación sutil para tarjetas en reposo (3.dp). */
        val cardDefault: Dp = 3.dp

        /** Elevación destacada para tarjetas en modo de arrastre activo (12.dp). */
        val cardDragging: Dp = 12.dp

        /** Elevación para diálogos y ventanas modales (24.dp). */
        val modal: Dp = 24.dp
    }

    /**
     * Radios de esquinas para contornos y formas (*Shapes / Corner Radii*).
     */
    object CornerRadius {
        /** Radio pequeño para badges e indicadores (8.dp). */
        val small: Dp = 8.dp

        /** Radio medio para botones, campos de texto y diálogos (12.dp). */
        val medium: Dp = 12.dp

        /** Radio grande para tarjetas principales y modales (16.dp). */
        val large: Dp = 16.dp

        /** Radio extra grande para contenedores destacados y avatares (24.dp). */
        val pill: Dp = 24.dp
    }

    /**
     * Tamaños estándar de iconos y gráficos.
     */
    object IconSize {
        /** Iconos pequeños dentro de badges o campos compactos (16.dp). */
        val small: Dp = 16.dp

        /** Iconos estándar en botones y filas (20.dp). */
        val medium: Dp = 20.dp

        /** Iconos en barras superiores y botones de acción flotante (24.dp). */
        val large: Dp = 24.dp

        /** Iconos grandes en encabezados de autenticación o bloqueo (48.dp). */
        val hero: Dp = 48.dp

        /** Ilustraciones y contenedores destacados en estados vacíos (96.dp). */
        val illustration: Dp = 96.dp
    }

    /**
     * Alturas estándar de componentes interactivos.
     */
    object ComponentHeight {
        /** Altura estándar para botones primarios y campos de texto (50.dp). */
        val buttonDefault: Dp = 50.dp

        /** Altura compacta para botones secundarios de tarjeta o tabla (36.dp). */
        val buttonCompact: Dp = 36.dp

        /** Tamaño del indicador circular de progreso TOTP (44.dp). */
        val progressIndicator: Dp = 44.dp
    }

    /**
     * Tamaños y dimensiones especiales de componentes de interfaz.
     */
    object ComponentSize {
        /** Tamaño de visualización de código QR (240.dp). */
        val qrCodeDisplay: Dp = 240.dp

        /** Tamaño del marco visor de escaneo de cámara (260.dp). */
        val qrScannerBox: Dp = 260.dp

        /** Tamaño del botón de acción flotante hero central (56.dp). */
        val heroFab: Dp = 56.dp

        /** Tamaño estándar de botones de acción en tarjetas o modales (36.dp). */
        val actionIconButton: Dp = 36.dp

        /** Altura máxima para listas scrolleables dentro de modales (320.dp). */
        val modalListMaxHeight: Dp = 320.dp

        /** Ancho mínimo para el menú flotante Speed Dial de creación de cuentas (220.dp). */
        val speedDialMinWidth: Dp = 220.dp

        /** Ancho máximo para el menú flotante Speed Dial de creación de cuentas (280.dp). */
        val speedDialMaxWidth: Dp = 280.dp

        /** Altura de las tarjetas táctiles de selección de método (ej. contraseña vs clave de 64 dígitos, 68.dp). */
        val methodSelectorCard: Dp = 68.dp

        /** Ancho mínimo estándar para ventanas modales (280.dp). */
        val modalMinWidth: Dp = 280.dp

        /** Ancho máximo estándar para ventanas modales (400.dp). */
        val modalMaxWidth: Dp = 400.dp

        /** Radio de desenfoque de fondo para ventanas modales en hardware compositor y Skia (20.dp). */
        val modalBlurRadius: Dp = 20.dp
    }

    /**
     * Grosor estandarizado de trazos y bordes (*Stroke / Border Widths*).
     */
    object Stroke {
        /** Borde fino para contornos sutiles (1.dp). */
        val thin: Dp = 1.dp

        /** Borde regular para tarjetas en arrastre (1.5.dp). */
        val regular: Dp = 1.5.dp


        /** Grosor del arco circular de progreso TOTP (3.5.dp). */
        val progressArc: Dp = 3.5.dp
    }

}
