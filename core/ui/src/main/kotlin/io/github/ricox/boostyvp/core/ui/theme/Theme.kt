package io.github.ricox.boostyvp.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Shapes
import androidx.tv.material3.Typography
import androidx.tv.material3.darkColorScheme
import io.github.ricox.boostyvp.core.ui.R
import io.github.ricox.boostyvp.core.ui.appearance.ProvideCardSize
import io.github.ricox.boostyvp.core.ui.appearance.ProvideInterfaceOptions
import io.github.ricox.boostyvp.core.ui.appearance.LocalInterfaceOptions

private val BoostyTypography = Typography().let {
    it.copy(
        displaySmall = it.displaySmall.copy(fontWeight = FontWeight.Bold),
        headlineLarge = it.headlineLarge.copy(fontWeight = FontWeight.Bold),
        headlineMedium = it.headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = it.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = it.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = it.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = it.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

/** Accent for small text/outlines, distinct from the saturated brand fill. */
val LocalAccentText = staticCompositionLocalOf { Color(0xFFFF7C82) }

/** Shared resource colors also theme the native Media3 controls. */
@Composable
fun BoostyVPTheme(content: @Composable () -> Unit) {
    ProvideInterfaceOptions { io.github.ricox.boostyvp.core.ui.appearance.ProvideMenuOptions { StyledContent(content) } }
}

@Composable
private fun StyledContent(content: @Composable () -> Unit) {
    val options = LocalInterfaceOptions.current
    val scheme = darkColorScheme(
        primary = Color(options.accent.argb),
        onPrimary = Color(options.accent.onArgb),
        secondary = Color(options.accent.argb),
        background = Color(options.theme.background),
        onBackground = colorResource(R.color.boostyvp_text),
        surface = Color(options.theme.surface),
        onSurface = colorResource(R.color.boostyvp_text),
        surfaceVariant = Color(options.theme.raised),
        onSurfaceVariant = colorResource(R.color.boostyvp_text_secondary),
        border = colorResource(R.color.boostyvp_border),
        error = colorResource(R.color.boostyvp_error),
    )
    MaterialTheme(
        colorScheme = scheme,
        typography = BoostyTypography,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(6.dp),
            small = RoundedCornerShape(10.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(20.dp),
            extraLarge = RoundedCornerShape(28.dp),
        ),
    ) {
        ProvideCardSize {
            CompositionLocalProvider(LocalContentColor provides scheme.onBackground,
                LocalAccentText provides Color(options.accent.textArgb), content = content)
        }
    }
}

/** Keep interactive content inside the TV's overscan safe area. */
object Overscan {
    val Horizontal = 48.dp
    val Vertical = 27.dp
}
