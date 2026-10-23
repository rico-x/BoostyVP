package io.github.ricox.boostyvp.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Shapes
import androidx.tv.material3.Typography
import androidx.tv.material3.darkColorScheme
import io.github.ricox.boostyvp.core.ui.R

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

/** Shared resource colors also theme the native Media3 controls. */
@Composable
fun BoostyVPTheme(content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = colorResource(R.color.boostyvp_accent),
        onPrimary = colorResource(R.color.boostyvp_on_accent),
        secondary = colorResource(R.color.boostyvp_accent),
        background = colorResource(R.color.boostyvp_background),
        onBackground = colorResource(R.color.boostyvp_text),
        surface = colorResource(R.color.boostyvp_surface),
        onSurface = colorResource(R.color.boostyvp_text),
        surfaceVariant = colorResource(R.color.boostyvp_surface_raised),
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
        CompositionLocalProvider(LocalContentColor provides scheme.onBackground, content = content)
    }
}

/** Keep interactive content inside the TV's overscan safe area. */
object Overscan {
    val Horizontal = 48.dp
    val Vertical = 27.dp
}
