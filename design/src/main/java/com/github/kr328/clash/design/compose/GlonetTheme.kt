package com.github.kr328.clash.design.compose

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Light = lightColorScheme(primary = Color(0xFF365BCE), secondary = Color(0xFF526078), tertiary = Color(0xFF006B62), background = Color(0xFFF7F8FC), surface = Color(0xFFF7F8FC))
private val Dark = darkColorScheme(primary = Color(0xFFB4C5FF), secondary = Color(0xFFBCC7DE), tertiary = Color(0xFF79D8C8), background = Color(0xFF111318), surface = Color(0xFF111318))

@Composable
fun GlonetXTheme(mode: String = "Auto", dynamic: Boolean = true, content: @Composable () -> Unit) {
    val dark = when(mode) { "ForceLight", "Light" -> false; "ForceDark", "ForceBlack", "Dark", "Black" -> true; else -> isSystemInDarkTheme() }
    var colors = if (dynamic && Build.VERSION.SDK_INT >= 31) {
        if(dark) dynamicDarkColorScheme(LocalContext.current) else dynamicLightColorScheme(LocalContext.current)
    } else if(dark) Dark else Light
    if(mode == "ForceBlack" || mode == "Black") colors = colors.copy(background = Color.Black, surface = Color.Black, surfaceContainerLowest = Color.Black)
    MaterialTheme(colorScheme = colors, typography = Typography(
        headlineLarge = TextStyle(fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold),
        headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold),
        titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
        titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
        bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
        labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    ), content = content)
}
