package com.autoscribe.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * The desktop palette, verbatim (main.py theme constants). The desktop app
 * is dark-only, so this app is too.
 */
object AutoScribeColors {
    val Bg = Color(0xFF0B0D10)
    val Sidebar = Color(0xFF101318)
    val Card = Color(0xFF12151A)
    val Input = Color(0xFF161A20)
    val Deep = Color(0xFF0E1014)
    val Row = Color(0xFF141820)
    val RowAlt = Color(0xFF15191F)
    val Border = Color(0xFF1F242C)
    val Border2 = Color(0xFF262C35)
    val Hover = Color(0xFF1C222B)
    val Text = Color(0xFFE8EAED)
    val Text2 = Color(0xFFAEB6C1)
    val Muted = Color(0xFF8E97A3)
    val Dim = Color(0xFF5B6470)
    val Accent = Color(0xFF7CC4FF)
    val AccentHover = Color(0xFF9BD2FF)
    val OnAccent = Color(0xFF0B0D10)
    val AccentSoft = Color(0xFF18212C)
    val AccentLine = Color(0xFF2E3B4C)
    val Chip = Color(0xFF1A2330)
    val ChipText = Color(0xFFA9D8FF)
    val Selected = Color(0xFF1E2B3B)
    val Good = Color(0xFF6EE7A8)
    val GoodSoft = Color(0xFF12201B)
    val GoodLine = Color(0xFF1E3A2E)
    val GoodText = Color(0xFFB7F0D2)
    val Warn = Color(0xFFFBBF24)
    val WarnSoft = Color(0xFF2A2312)
    val WarnText = Color(0xFFFCD98A)
    val Bad = Color(0xFFF87171)
    val BadSoft = Color(0xFF2A1517)
    val BadLine = Color(0xFF5A2A2E)
    val BadText = Color(0xFFFFB4B4)
    val RecBg = Color(0xFF111A22)
    val RecLine = Color(0xFF23364A)
}

private val ColorScheme = darkColorScheme(
    primary = AutoScribeColors.Accent,
    onPrimary = AutoScribeColors.OnAccent,
    primaryContainer = AutoScribeColors.AccentSoft,
    onPrimaryContainer = AutoScribeColors.ChipText,
    secondary = AutoScribeColors.ChipText,
    background = AutoScribeColors.Bg,
    surface = AutoScribeColors.Card,
    surfaceVariant = AutoScribeColors.Input,
    onSurface = AutoScribeColors.Text,
    onSurfaceVariant = AutoScribeColors.Text2,
    outline = AutoScribeColors.Border,
    outlineVariant = AutoScribeColors.Border2,
    error = AutoScribeColors.Bad,
    onError = AutoScribeColors.OnAccent,
    errorContainer = AutoScribeColors.BadSoft,
    onErrorContainer = AutoScribeColors.BadText,
)

@Composable
fun AutoScribeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ColorScheme, content = content)
}

/** Desktop ping_color(). */
fun pingColor(ping: Double?): Color = when {
    ping == null -> AutoScribeColors.Muted
    ping < 50 -> AutoScribeColors.Good
    ping < 100 -> AutoScribeColors.Warn
    else -> AutoScribeColors.Bad
}

/** Desktop rate_color(). */
fun rateColor(rate: Double): Color = when {
    rate >= 0.75 -> AutoScribeColors.Good
    rate >= 0.45 -> AutoScribeColors.Warn
    else -> AutoScribeColors.Bad
}
