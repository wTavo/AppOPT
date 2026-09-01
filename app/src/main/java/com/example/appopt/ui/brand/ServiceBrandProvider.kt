package com.example.appopt.ui.brand

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import com.example.appopt.R
import kotlin.math.abs

/**
 * Información visual de marca para representar servicios 2FA reconocidos.
 *
 * @param brandName Nombre canónico de la marca.
 * @param shortInitials Siglas o iniciales de la marca (1 o 2 caracteres).
 * @param backgroundColor Color de fondo característico de la marca.
 * @param iconResId Recurso vectorial oficial del logotipo de la marca si está disponible.
 * @param textColor Color de texto con alto contraste sobre el fondo.
 */
data class BrandInfo(
    val brandName: String,
    val shortInitials: String,
    val backgroundColor: Color,
    @param:DrawableRes val iconResId: Int? = null,
    val textColor: Color = Color.White
)

/**
 * Proveedor centralizado de identificación de marcas para emisores 2FA.
 *
 * Principio de diseño:
 * - Detección local sin llamadas a red para preservar la privacidad y funcionalidad offline.
 * - Reconocimiento de más de 25 plataformas con logotipos vectoriales de alta definición.
 * - Generador determinista de iniciales y colores de reserva para emisores personalizados.
 */
object ServiceBrandProvider {

    private val KnownBrands = listOf(
        BrandInfo("Google", "G", Color(0xFFFFFFFF), R.drawable.ic_brand_google, Color.Black),
        BrandInfo("GitHub", "GH", Color(0xFF24292E), R.drawable.ic_brand_github),
        BrandInfo("Microsoft", "MS", Color(0xFFFFFFFF), R.drawable.ic_brand_microsoft, Color.Black),
        BrandInfo("Discord", "D", Color(0xFF5865F2), R.drawable.ic_brand_discord),
        BrandInfo("Amazon", "AMZ", Color(0xFF232F3E), R.drawable.ic_brand_amazon),
        BrandInfo("AWS", "AWS", Color(0xFF232F3E), R.drawable.ic_brand_amazon),
        BrandInfo("Steam", "ST", Color(0xFF171A21), R.drawable.ic_brand_steam),
        BrandInfo("Apple", "AP", Color(0xFF000000), R.drawable.ic_brand_apple),
        BrandInfo("Twitter", "X", Color(0xFF000000), R.drawable.ic_brand_x),
        BrandInfo("Spotify", "SP", Color(0xFF1DB954), R.drawable.ic_brand_spotify),
        BrandInfo("GitLab", "GL", Color(0xFF292961), R.drawable.ic_brand_gitlab),
        BrandInfo("Reddit", "RD", Color(0xFFFF4500), R.drawable.ic_brand_reddit),
        BrandInfo("Twitch", "TW", Color(0xFF9146FF), R.drawable.ic_brand_twitch),
        BrandInfo("Bitwarden", "BW", Color(0xFF175DDC), R.drawable.ic_brand_bitwarden),
        BrandInfo("Facebook", "FB", Color(0xFF1877F2), R.drawable.ic_brand_facebook),
        BrandInfo("Meta", "META", Color(0xFF1877F2), R.drawable.ic_brand_facebook),
        BrandInfo("PayPal", "PP", Color(0xFF003087)),
        BrandInfo("Binance", "BN", Color(0xFFF0B90B), null, Color.Black),
        BrandInfo("Instagram", "IG", Color(0xFFE4405F)),
        BrandInfo("Epic Games", "EG", Color(0xFF313131)),
        BrandInfo("Proton", "PR", Color(0xFF6D4AFF)),
        BrandInfo("Uber", "UB", Color(0xFF000000)),
        BrandInfo("LinkedIn", "IN", Color(0xFF0A66C2)),
        BrandInfo("Cloudflare", "CF", Color(0xFFF38020)),
        BrandInfo("Coinbase", "CB", Color(0xFF0052FF)),
        BrandInfo("OpenAI", "AI", Color(0xFF10A37F)),
        BrandInfo("Notion", "NO", Color(0xFF000000)),
        BrandInfo("Slack", "SL", Color(0xFF4A154B))
    )

    private val FallbackPalette = listOf(
        Color(0xFF3F51B5), // Indigo
        Color(0xFF009688), // Teal
        Color(0xFF00BCD4), // Cyan
        Color(0xFF673AB7), // Deep Purple
        Color(0xFFE91E63), // Pink
        Color(0xFF2196F3), // Blue
        Color(0xFF4CAF50), // Green
        Color(0xFFFF5722), // Deep Orange
        Color(0xFF607D8B), // Blue Grey
        Color(0xFF9C27B0)  // Purple
    )

    /**
     * Resuelve la información de marca para un emisor dado.
     *
     * @param issuer Nombre del servicio o emisor configurado en el token 2FA.
     * @return [BrandInfo] con las iniciales, logotipo vectorial y colores correspondientes.
     */
    fun getBrandInfo(issuer: String): BrandInfo {
        val trimmed = issuer.trim()
        if (trimmed.isEmpty()) {
            return BrandInfo(
                brandName = "Servicio",
                shortInitials = "OTP",
                backgroundColor = Color(0xFF546E7A)
            )
        }

        // Búsqueda por coincidencia en marcas conocidas
        val matched = KnownBrands.firstOrNull { brand ->
            trimmed.contains(brand.brandName, ignoreCase = true)
        }

        if (matched != null) {
            return matched
        }

        // Generación determinista para emisores personalizados
        val initials = extractInitials(trimmed)
        val paletteIndex = abs(trimmed.lowercase().hashCode()) % FallbackPalette.size
        val color = FallbackPalette[paletteIndex]

        return BrandInfo(
            brandName = trimmed,
            shortInitials = initials,
            backgroundColor = color
        )
    }

    /**
     * Extrae hasta 2 letras iniciales limpias de un nombre de servicio.
     */
    private fun extractInitials(name: String): String {
        val words = name.split(" ", "-", "_", ".").filter { it.isNotBlank() }
        return when {
            words.size >= 2 -> "${words[0].first().uppercaseChar()}${words[1].first().uppercaseChar()}"
            name.length >= 2 -> name.substring(0, 2).uppercase()
            name.isNotEmpty() -> name.substring(0, 1).uppercase()
            else -> "OTP"
        }
    }
}
