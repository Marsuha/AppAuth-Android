package net.openid.appauthdemo.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = Primary,
    secondary = PrimaryDark,
    tertiary = Accent
)

@Composable
fun AppAuthDemoTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = LightColorScheme // No dark theme for now

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
