package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val HighDensityDarkColorScheme = darkColorScheme(
    primary = HDPrimary,
    onPrimary = HDOnPrimary,
    primaryContainer = HDPrimaryContainer,
    onPrimaryContainer = HDOnPrimaryContainer,
    
    secondary = HDSecondary,
    onSecondary = HDOnSecondary,
    secondaryContainer = HDSecondaryContainer,
    onSecondaryContainer = HDOnSecondaryContainer,
    
    tertiary = HDTertiary,
    onTertiary = HDOnTertiary,
    
    background = HDBackground,
    onBackground = HDTextOnDark,
    
    surface = HDSurface,
    onSurface = HDTextOnDark,
    surfaceVariant = HDSurfaceVariant,
    onSurfaceVariant = HDLabelMuted,
    
    outline = HDOutline,
    outlineVariant = HDOutlineVariant
)

private val HighDensityLightColorScheme = lightColorScheme(
    primary = HDPrimary,
    onPrimary = HDOnPrimary,
    primaryContainer = HDPrimaryContainer,
    secondary = HDSecondary,
    background = HDBackground,
    surface = HDSurface,
    onBackground = HDTextOnDark,
    onSurface = HDTextOnDark
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Force our high-density theme for custom styling signature
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> HighDensityDarkColorScheme // Default to our premium high-density dark mode
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
