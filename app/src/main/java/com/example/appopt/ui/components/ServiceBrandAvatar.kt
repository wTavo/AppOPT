package com.example.appopt.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.appopt.ui.brand.ServiceBrandProvider
import com.example.appopt.ui.theme.Dimensions

/**
 * Avatar gráfico que representa la marca o servicio de una cuenta 2FA.
 *
 * Características visuales:
 * - Detección automática del color de fondo oficial y siglas de la marca.
 * - Forma redondeada con esquinas suaves ([Dimensions.CornerRadius.medium]).
 * - Tipografía en negrita de alto contraste para una lectura visual rápida.
 *
 * @param issuer Nombre del emisor o servicio (ej. "GitHub", "Google", "Discord").
 * @param modifier Modificador de layout.
 * @param size Tamaño cuadrado del contenedor del avatar.
 */
@Composable
fun ServiceBrandAvatar(
    issuer: String,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp
) {
    val brandInfo = remember(issuer) {
        ServiceBrandProvider.getBrandInfo(issuer)
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(Dimensions.CornerRadius.medium))
            .background(brandInfo.backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = brandInfo.shortInitials,
            color = brandInfo.textColor,
            style = if (size >= 48.dp) {
                MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            } else {
                MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
            },
            maxLines = 1
        )
    }
}
