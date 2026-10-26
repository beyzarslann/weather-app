package com.kampplus.hava.ui.forecast

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.kampplus.hava.model.WeatherCondition

/**
 * Hava durumuna göre dikey gradient arka plan.
 *
 * Renk geçişi `animateColorAsState` ile yumuşar: yükleniyor ekranından
 * veriye geçerken arka plan birden değil, yarım saniyede değişir.
 *
 * @param condition null ise (yükleniyor, hata) temanın düz arka planı kullanılır.
 */
@Composable
fun weatherBackground(condition: WeatherCondition?): Brush {
    // Tema koyu mu? Arka planın parlaklığına bakarak anlıyoruz; böylece
    // @Preview'da HavaTheme(darkTheme = true) verince de doğru çalışır.
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val fallback = MaterialTheme.colorScheme.background
    val (top, bottom) = condition?.gradientColors(isDark) ?: (fallback to fallback)

    val animatedTop by animateColorAsState(top, tween(ANIMATION_MS), label = "bgTop")
    val animatedBottom by animateColorAsState(bottom, tween(ANIMATION_MS), label = "bgBottom")
    return Brush.verticalGradient(listOf(animatedTop, animatedBottom))
}

/** Her hava durumu için (üst, alt) renk çifti. Koyu temada koyu tonlar. */
private fun WeatherCondition.gradientColors(isDark: Boolean): Pair<Color, Color>? = when (this) {
    WeatherCondition.CLEAR, WeatherCondition.MAINLY_CLEAR ->
        if (isDark) Color(0xFF4A3A12) to Color(0xFF1B1A16) else Color(0xFFFFE29F) to Color(0xFFFFF6E5)

    WeatherCondition.PARTLY_CLOUDY ->
        if (isDark) Color(0xFF1F3347) to Color(0xFF15191E) else Color(0xFFCFE3F5) to Color(0xFFF4F8FB)

    WeatherCondition.OVERCAST, WeatherCondition.FOG ->
        if (isDark) Color(0xFF2E3236) to Color(0xFF17191B) else Color(0xFFD5D9DE) to Color(0xFFF2F3F5)

    WeatherCondition.DRIZZLE, WeatherCondition.RAIN, WeatherCondition.RAIN_SHOWERS ->
        if (isDark) Color(0xFF1E2A36) to Color(0xFF121619) else Color(0xFFAFC0CF) to Color(0xFFE6ECF1)

    WeatherCondition.SNOW, WeatherCondition.SNOW_SHOWERS ->
        if (isDark) Color(0xFF2A3A48) to Color(0xFF16191C) else Color(0xFFE3F1FB) to Color(0xFFFFFFFF)

    WeatherCondition.THUNDERSTORM ->
        if (isDark) Color(0xFF2B2540) to Color(0xFF121018) else Color(0xFF9AA0B5) to Color(0xFFDCDFE8)

    WeatherCondition.UNKNOWN -> null
}

private const val ANIMATION_MS = 600
