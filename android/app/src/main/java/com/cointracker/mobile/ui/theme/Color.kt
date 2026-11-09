package com.cointracker.mobile.ui.theme

import androidx.compose.ui.graphics.Color

// ── Gold & Graphite Design System (aligned with design-tokens.json v4.0.0) ──

// Dark Theme (Default)
val DarkCanvas = Color(0xFF11151B)
val DarkSurface = Color(0xFF191F27)
val DarkSurfaceRaised = Color(0xFF222B35)
val DarkSurfaceSubtle = Color(0xFF151B22)
val DarkTextPrimary = Color(0xFFF1F3F6)
val DarkTextSecondary = Color(0xFFA7B0BC)
val DarkTextMuted = Color(0xFF7F8A98)
val DarkBorder = Color(0xFF303A46)
val DarkBorderStrong = Color(0xFF465260)
val DarkBrand = Color(0xFFE8B84A)
val DarkBrandHover = Color(0xFFF2C968)
val DarkBrandSoft = Color(0xFF352C1B)
val DarkOnBrand = Color(0xFF241A05)
val DarkIncome = Color(0xFF47D7A0)
val DarkIncomeSoft = Color(0xFF16352D)
val DarkIncomeHover = Color(0xFF6EE7B7)
val DarkExpense = Color(0xFFF28B82)
val DarkExpenseSoft = Color(0xFF3A2426)
val DarkExpenseHover = Color(0xFFFF9E96)
val DarkInfo = Color(0xFF8AB4F8)
val DarkOnInfo = Color(0xFF11151B)
val DarkWarning = Color(0xFFF4C56A)
val DarkFocus = Color(0xFFF5D889)

// Light Theme
val LightCanvas = Color(0xFFF5F6F8)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceRaised = Color(0xFFFFFFFF)
val LightSurfaceSubtle = Color(0xFFEDF0F3)
val LightTextPrimary = Color(0xFF19212B)
val LightTextSecondary = Color(0xFF526071)
val LightTextMuted = Color(0xFF5F6C7D)
val LightBorder = Color(0xFFD8DEE6)
val LightBorderStrong = Color(0xFFB7C1CD)
val LightBrand = Color(0xFF9A6A08)
val LightBrandHover = Color(0xFF7F5706)
val LightBrandSoft = Color(0xFFFFF2CC)
val LightOnBrand = Color(0xFFFFFFFF)
val LightIncome = Color(0xFF06734F)
val LightIncomeSoft = Color(0xFFDDF7EC)
val LightIncomeHover = Color(0xFF045C3F)
val LightExpense = Color(0xFFB93832)
val LightExpenseSoft = Color(0xFFFCE7E5)
val LightExpenseHover = Color(0xFF9C2B25)
val LightInfo = Color(0xFF245FC7)
val LightOnInfo = Color(0xFFFFFFFF)
val LightWarning = Color(0xFF865900)
val LightFocus = Color(0xFF805B00)

// ── Material 3 Color Scheme Mappings ──
val DarkPrimary = DarkBrand
val DarkPrimaryContainer = DarkBrandSoft
val DarkOnPrimary = DarkOnBrand
val DarkOnPrimaryContainer = DarkBrand
val DarkSecondary = DarkIncome
val DarkSecondaryContainer = DarkIncomeSoft
val DarkOnSecondary = Color(0xFF003822)
val DarkOnSecondaryContainer = DarkIncome
val DarkTertiary = DarkInfo
val DarkTertiaryContainer = Color(0xFF1E3A5F)
val DarkOnTertiary = Color(0xFF002F6C)
val DarkOnTertiaryContainer = DarkInfo
val DarkError = DarkExpense
val DarkErrorContainer = DarkExpenseSoft
val DarkOnError = Color(0xFF601410)
val DarkOnErrorContainer = DarkExpense
val DarkBackground = DarkCanvas
val DarkOnBackground = DarkTextPrimary
val DarkOnSurface = DarkTextPrimary
val DarkSurfaceVariant = DarkSurfaceRaised
val DarkOnSurfaceVariant = DarkTextSecondary
val DarkOutline = DarkBorderStrong
val DarkOutlineVariant = DarkBorder
val DarkShadow = Color(0xFF000000)
val DarkScrim = Color(0xFF000000)
val DarkInverseSurface = LightSurface
val DarkInverseOnSurface = LightTextPrimary
val DarkInversePrimary = LightBrand

val LightPrimary = LightBrand
val LightPrimaryContainer = LightBrandSoft
val LightOnPrimary = LightOnBrand
val LightOnPrimaryContainer = LightBrandHover
val LightSecondary = LightIncome
val LightSecondaryContainer = LightIncomeSoft
val LightOnSecondary = Color(0xFFFFFFFF)
val LightOnSecondaryContainer = LightIncomeHover
val LightTertiary = LightInfo
val LightTertiaryContainer = Color(0xFFD6E4FF)
val LightOnTertiary = LightOnInfo
val LightOnTertiaryContainer = Color(0xFF001C3D)
val LightError = LightExpense
val LightErrorContainer = LightExpenseSoft
val LightOnError = Color(0xFFFFFFFF)
val LightOnErrorContainer = LightExpenseHover
val LightBackground = LightCanvas
val LightOnBackground = LightTextPrimary
val LightOnSurface = LightTextPrimary
val LightSurfaceVariant = LightSurfaceSubtle
val LightOnSurfaceVariant = LightTextSecondary
val LightOutline = LightBorderStrong
val LightOutlineVariant = LightBorder
val LightShadow = Color(0xFF000000)
val LightScrim = Color(0xFF000000)
val LightInverseSurface = DarkSurface
val LightInverseOnSurface = DarkTextPrimary
val LightInversePrimary = DarkBrand

// ── Semantic Aliases & Compatibility Helpers ──
val WebPrimary = LightBrand
val WebSuccess = LightIncome
val WebDanger = LightExpense
val WebPrimaryDark = DarkBrand
val WebSuccessDark = DarkIncome
val WebDangerDark = DarkExpense
val Midnight = DarkCanvas
val Slate = DarkSurface
val TextLight = LightTextPrimary
val TextDark = DarkTextPrimary
