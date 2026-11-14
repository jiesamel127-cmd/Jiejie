package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
  primary = RosePrimaryDark,
  onPrimary = Color(0xFF4C0519),
  primaryContainer = RoseContainerDark,
  onPrimaryContainer = Color(0xFFFFE4E6),
  secondary = IndigoSecondaryDark,
  onSecondary = Color(0xFF1E1B4B),
  secondaryContainer = IndigoContainerDark,
  onSecondaryContainer = Color(0xFFE0E7FF),
  tertiary = EmeraldTertiaryDark,
  onTertiary = Color(0xFF022C22),
  tertiaryContainer = EmeraldContainerDark,
  onTertiaryContainer = Color(0xFFD1FAE5),
  background = ObsidianBackground,
  onBackground = InkOnDark,
  surface = DarkCardSurface,
  onSurface = InkOnDark,
  surfaceVariant = DarkSurfaceVariant,
  onSurfaceVariant = MutedInkDark,
  outline = OutlineDark,
  outlineVariant = Color(0xFF282435),
)

private val LightColorScheme = lightColorScheme(
  primary = RosePrimary,
  onPrimary = Color.White,
  primaryContainer = RoseContainerLight,
  onPrimaryContainer = Color(0xFF881337),
  secondary = IndigoSecondary,
  onSecondary = Color.White,
  secondaryContainer = IndigoContainerLight,
  onSecondaryContainer = IndigoSecondary,
  tertiary = EmeraldTertiary,
  onTertiary = Color.White,
  tertiaryContainer = EmeraldContainerLight,
  onTertiaryContainer = Color(0xFF064E3B),
  background = IvoryBackground,
  onBackground = InkOnLight,
  surface = PureSurface,
  onSurface = InkOnLight,
  surfaceVariant = SoftSurfaceVariant,
  onSurfaceVariant = MutedInkLight,
  outline = OutlineLight,
  outlineVariant = Color(0xFFEEE8E1),
)

val KPopGoShapes = Shapes(
  small = RoundedCornerShape(10.dp),
  medium = RoundedCornerShape(16.dp),
  large = RoundedCornerShape(22.dp),
  extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun KPopGOTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

  MaterialTheme(
    colorScheme = colorScheme,
    typography = KPopGoTypography,
    shapes = KPopGoShapes,
    content = content,
  )
}
