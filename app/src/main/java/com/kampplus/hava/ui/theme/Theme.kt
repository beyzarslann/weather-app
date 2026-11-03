package com.kampplus.hava.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * Açık tema paleti. Her Material rolü ([Color.kt]'deki) bir renge bağlanır:
 * `primary` ana vurgu, `surface` kart zemini, `onSurface` o zemindeki yazı rengidir.
 *
 * Neden **bütün** roller yazılı? `lightColorScheme` verilmeyen rollere Material'in
 * kendi varsayılan (mor) paletini koyar. Card, TextField ve Chip gibi bileşenler
 * zemin için `surfaceContainer*` rollerini okuduğundan, bunları atlamak
 * kartlarda morumsu bir ton olarak geri dönüyordu.
 */
private val LightColors = lightColorScheme(
    primary = Blue40,
    onPrimary = Neutral100,
    primaryContainer = Blue90,
    onPrimaryContainer = Blue10,
    inversePrimary = Blue80,
    secondary = BlueGrey40,
    onSecondary = Neutral100,
    secondaryContainer = BlueGrey90,
    onSecondaryContainer = BlueGrey10,
    tertiary = Sand40,
    onTertiary = Neutral100,
    tertiaryContainer = Sand90,
    onTertiaryContainer = Sand10,
    background = Neutral98,
    onBackground = Neutral10,
    surface = Neutral98,
    onSurface = Neutral10,
    surfaceVariant = NeutralVariant90,
    onSurfaceVariant = NeutralVariant30,
    surfaceTint = Blue40,
    inverseSurface = Neutral20,
    inverseOnSurface = Neutral95,
    error = Red40,
    onError = Neutral100,
    errorContainer = Red90,
    onErrorContainer = Red10,
    outline = NeutralVariant50,
    outlineVariant = NeutralVariant80,
    // Zeminden kartlara doğru: Lowest en açık (beyaz), Highest en koyu.
    surfaceBright = Neutral98,
    surfaceDim = Neutral87,
    surfaceContainerLowest = Neutral100,
    surfaceContainerLow = Neutral96,
    surfaceContainer = Neutral94,
    surfaceContainerHigh = Neutral92,
    surfaceContainerHighest = Neutral90
)

/**
 * Koyu tema paleti. Aynı roller, koyu zemine uygun tonlarla.
 *
 * Koyu temada sıra tersine döner: Lowest en koyu, Highest en açık.
 * Böylece "yükselen" kartlar zeminden biraz daha aydınlık görünür.
 */
private val DarkColors = darkColorScheme(
    primary = Blue80,
    onPrimary = Blue20,
    primaryContainer = Blue30,
    onPrimaryContainer = Blue90,
    inversePrimary = Blue40,
    secondary = BlueGrey80,
    onSecondary = BlueGrey20,
    secondaryContainer = BlueGrey30,
    onSecondaryContainer = BlueGrey90,
    tertiary = Sand80,
    onTertiary = Sand20,
    tertiaryContainer = Sand30,
    onTertiaryContainer = Sand90,
    background = Neutral6,
    onBackground = Neutral90,
    surface = Neutral6,
    onSurface = Neutral90,
    surfaceVariant = NeutralVariant30,
    onSurfaceVariant = NeutralVariant80,
    surfaceTint = Blue80,
    inverseSurface = Neutral90,
    inverseOnSurface = Neutral20,
    error = Red80,
    onError = Red20,
    errorContainer = Red30,
    onErrorContainer = Red90,
    outline = NeutralVariant60,
    outlineVariant = NeutralVariant30,
    surfaceBright = Neutral24,
    surfaceDim = Neutral6,
    surfaceContainerLowest = Neutral4,
    surfaceContainerLow = Neutral10,
    surfaceContainer = Neutral12,
    surfaceContainerHigh = Neutral17,
    surfaceContainerHighest = Neutral22
)

/**
 * Uygulamanın teması. Bütün ekranlar bunun içine sarmalanır.
 *
 * @param darkTheme Varsayılan olarak telefonun sistem ayarını izler
 *   ([isSystemInDarkTheme]). @Preview'larda elle `true` verip koyu temayı deneyebilirsin.
 */
@Composable
fun HavaTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = HavaTypography,
        content = content
    )
}
