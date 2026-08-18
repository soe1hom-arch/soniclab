/*
 * Copyright 2026 soe1hom-arch
 * SPDX-License-Identifier: Apache-2.0
 */

package com.soniclab.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SonicDarkColorScheme = darkColorScheme(
    primary = PurpleAccent,
    onPrimary = Color.White,
    secondary = CyanAccent,
    onSecondary = Color.Black,
    background = BackgroundDark,
    onBackground = TextPrimaryDark,
    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = TextSecondaryDark,
    outline = OutlineDark,
    error = ErrorAccent
)

@Composable
fun SonicLabTheme(
    amoled: Boolean = false,
    content: @Composable () -> Unit
) {
    // Force the premium dark look (spec: gray/black tones); AMOLED deepens blacks.
    val colorScheme = if (amoled) {
        SonicDarkColorScheme.copy(
            background = Color.Black,
            surface = Color(0xFF0A0A0A),
            surfaceVariant = Color(0xFF0E0E0E)
        )
    } else {
        SonicDarkColorScheme
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = SonicTypography,
        content = content
    )
}
