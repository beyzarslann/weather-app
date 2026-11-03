package com.kampplus.hava.ui.forecast

import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kampplus.hava.model.WeatherCondition
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random

/*
 * Tahmin ekranının hava durumuna göre arka plan efektleri: güneş, bulut, yağmur, kar, şimşek.
 *
 * Nasıl çalışır?
 *
 * 1. Zaman: [rememberElapsedSeconds] her karede `withFrameNanos` ile ekranın kare saatini okur
 *    ve geçen süreyi bir state'e yazar. Bu state **yalnızca Canvas'ın çizim lambda'sında**
 *    okunur. Compose bir state'in nerede okunduğunu takip eder: okuma çizim aşamasında
 *    olduğu için her karede yalnızca çizim tekrarlanır; composition ve layout hiç
 *    yeniden çalışmaz. (State'i composable gövdesinde okusaydık saniyede 60 kez
 *    bütün fonksiyon yeniden çalışırdı.)
 *
 * 2. Parçacıklar: yağmur damlası, kar tanesi ve bulutların başlangıç konumu ve hızı, sabit
 *    seed'li bir `Random` ile **bir kez** üretilir (`remember`). Liste sonra hiç değişmez;
 *    her karede konum, zamandan hesaplanır: `ilerleme = (başlangıç + zaman × hız) mod 1`.
 *    Böylece kare başına nesne üretilmez, çöp toplayıcı (GC) rahatsız edilmez. Sabit seed,
 *    ekran her açıldığında aynı dağılımı verir; @Preview da her seferinde aynı görünür.
 *
 * 3. Konumlar 0..1 arası oranlarla saklanır ve çizimde ekran boyutuyla çarpılır; telefon
 *    döndürülse ya da ekran boyutu değişse de efekt kendiliğinden uyar.
 *
 * 4. Erişilebilirlik ve okunabilirlik: sistemde animasyonlar kapalıysa (Geliştirici
 *    seçenekleri → "Animatör süre ölçeği: kapalı") efekt hiç çizilmez. Opaklıklar düşük
 *    tutulur, koyu temada [DARK_THEME_DIM] ile ayrıca söndürülür; yazıların önüne geçmez.
 */

/** Ekranda hangi efektin çizileceği. Birkaç hava durumu aynı efekti paylaşır. */
private enum class Effect { SUN, CLOUDS, DRIZZLE, RAIN, SNOW, STORM }

/** Hava durumunu efekte çevirir. Bilinmeyen durumda efekt yoktur (null). */
private fun WeatherCondition.toEffect(): Effect? = when (this) {
    WeatherCondition.CLEAR, WeatherCondition.MAINLY_CLEAR -> Effect.SUN
    WeatherCondition.PARTLY_CLOUDY, WeatherCondition.OVERCAST, WeatherCondition.FOG -> Effect.CLOUDS
    WeatherCondition.DRIZZLE -> Effect.DRIZZLE
    WeatherCondition.RAIN, WeatherCondition.RAIN_SHOWERS -> Effect.RAIN
    WeatherCondition.SNOW, WeatherCondition.SNOW_SHOWERS -> Effect.SNOW
    WeatherCondition.THUNDERSTORM -> Effect.STORM
    WeatherCondition.UNKNOWN -> null
}

/**
 * Hava durumuna göre arka plan efekti.
 *
 * Gradient arka planın üstüne, ekran içeriğinin altına yerleştirilir.
 *
 * @param condition null ise (yükleniyor, hata) ya da [WeatherCondition.UNKNOWN] ise hiçbir şey çizilmez.
 */
@Composable
fun WeatherEffects(condition: WeatherCondition?, modifier: Modifier = Modifier) {
    val effect = condition?.toEffect() ?: return
    if (!rememberAnimationsEnabled()) return

    // Arka plan rengine bakarak koyu temayı anlıyoruz (WeatherBackground ile aynı yöntem).
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val palette = EffectPalette(isDark)
    // Değeri değil, okuyan fonksiyonu aşağı geçiriyoruz: okuma çizim lambda'sında yapılsın diye.
    val time = rememberElapsedSeconds()

    when (effect) {
        Effect.SUN -> SunEffect(time, palette, modifier)
        Effect.CLOUDS -> CloudEffect(time, palette, modifier)
        Effect.DRIZZLE -> RainEffect(time, palette, RainStyle.Drizzle, modifier)
        Effect.RAIN -> RainEffect(time, palette, RainStyle.Heavy, modifier)
        Effect.SNOW -> SnowEffect(time, palette, modifier)
        Effect.STORM -> {
            RainEffect(time, palette, RainStyle.Heavy, modifier)
            LightningEffect(time, palette, modifier)
        }
    }
}

// ---------------------------------------------------------------------------------------
// Zaman ve ayarlar
// ---------------------------------------------------------------------------------------

/**
 * Efekt ekrana geldiğinden beri geçen süreyi (saniye) okuyan bir fonksiyon döndürür.
 *
 * `withFrameNanos` bir sonraki kare çizilmeden hemen önce devam eder; yani döngü ekranın
 * yenileme hızına kilitlidir ve uygulama arka plandayken kendiliğinden durur.
 */
@Composable
private fun rememberElapsedSeconds(): () -> Float {
    val seconds = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) {
            withFrameNanos { now -> seconds.floatValue = (now - start) / 1_000_000_000f }
        }
    }
    return remember { { seconds.floatValue } }
}

/**
 * Sistem animasyonları açık mı?
 *
 * Hareket hassasiyeti olan kullanıcılar animasyonları kapatabilir. Bu ayar sıfırsa
 * süs niteliğindeki efektleri hiç çizmeyiz; arka planın gradient'i yeterli.
 */
@Composable
private fun rememberAnimationsEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    }
}

/**
 * Efektlerin renkleri ve opaklık çarpanı.
 *
 * Açık temada zemin zaten açık olduğu için beyaz yağmur/kar görünmez; orada mavi-gri
 * tonlar kullanılır. Koyu temada beyaz tonlar, ama [DARK_THEME_DIM] ile söndürülmüş hâlde.
 */
private class EffectPalette(isDark: Boolean) {
    val dim = if (isDark) DARK_THEME_DIM else 1f
    val sun = Color(0xFFFFB300)
    val cloud = Color.White
    val rain = if (isDark) Color(0xFFCFE0F0) else Color(0xFF4F6B85)
    val snow = if (isDark) Color.White else Color(0xFF8EAFCB)
    val flash = Color.White

    /** Temel opaklığı tema çarpanıyla birleştirir. */
    fun alpha(base: Float) = base * dim
}

// ---------------------------------------------------------------------------------------
// Güneş
// ---------------------------------------------------------------------------------------

/**
 * Sağ üstte yavaşça dönen ışınlar ve ortalarında soluk bir güneş dairesi.
 *
 * "Nefes alma": ışınların boyu sinüs dalgasıyla birkaç saniyede bir %8 uzayıp kısalır.
 */
@Composable
private fun SunEffect(time: () -> Float, palette: EffectPalette, modifier: Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val t = time()
        val center = Offset(size.width * 0.85f, size.height * 0.08f)
        val inner = 70.dp.toPx()
        val breath = 1f + 0.08f * sin(t * 2f * PI.toFloat() / SUN_BREATH_SECONDS)

        // Ortadaki soluk daire: merkezden kenara doğru şeffaflaşan radyal gradient.
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(palette.sun.copy(alpha = palette.alpha(0.35f)), Color.Transparent),
                center = center,
                radius = 150.dp.toPx()
            ),
            radius = 150.dp.toPx(),
            center = center
        )

        // Bütün ışın demeti birlikte döner; tek tek açı hesaplamak yerine tuvali döndürüyoruz.
        rotate(degrees = t * SUN_DEGREES_PER_SECOND, pivot = center) {
            repeat(SUN_RAY_COUNT) { i ->
                // Uzun ve kısa ışınlar sırayla gelir; tek düze bir taç gibi durmasın.
                val length = (if (i % 2 == 0) 90.dp.toPx() else 55.dp.toPx()) * breath
                rotate(degrees = i * 360f / SUN_RAY_COUNT, pivot = center) {
                    drawLine(
                        color = palette.sun.copy(alpha = palette.alpha(0.22f)),
                        start = Offset(center.x, center.y - inner),
                        end = Offset(center.x, center.y - inner - length),
                        strokeWidth = 6.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------
// Bulutlar
// ---------------------------------------------------------------------------------------

/** Tek bir bulut: dikey konumu, hızı, boyutu ve yatay başlangıç noktası (0..1 oranları). */
private class Cloud(val y: Float, val speed: Float, val scale: Float, val phase: Float, val alpha: Float)

/**
 * Farklı yükseklik ve hızlarda soldan sağa süzülen bulutlar.
 *
 * Her bulut üç ovalden oluşan tek bir [Path]'tir. Ovalleri ayrı ayrı yarı saydam
 * çizseydik üst üste bindikleri yerler daha koyu görünürdü; tek path olarak
 * doldurulunca birleşim alanı bir kez boyanır.
 *
 * `drawWithCache`: path'ler yalnızca ekran boyutu değişince yeniden kurulur;
 * her karede sadece `onDrawBehind` (konumlandırma ve çizim) çalışır.
 */
@Composable
private fun CloudEffect(time: () -> Float, palette: EffectPalette, modifier: Modifier) {
    val clouds = remember {
        val random = Random(CLOUD_SEED)
        List(CLOUD_COUNT) {
            Cloud(
                y = random.range(0.02f, 0.40f),
                // Saniyede ekran genişliğinin %1–3'ü: yavaş bir süzülüş.
                speed = random.range(0.010f, 0.028f),
                scale = random.range(0.8f, 1.5f),
                phase = random.nextFloat(),
                alpha = random.range(0.18f, 0.32f)
            )
        }
    }
    Spacer(
        modifier = modifier
            .fillMaxSize()
            .drawWithCache {
                val baseWidth = 170.dp.toPx()
                val baseHeight = 60.dp.toPx()
                val paths = clouds.map { cloudPath(baseWidth * it.scale, baseHeight * it.scale) }
                onDrawBehind {
                    val t = time()
                    clouds.forEachIndexed { i, cloud ->
                        val width = baseWidth * cloud.scale
                        // Bulut ekranın solundan tamamen dışarıdan girer, sağdan tamamen çıkar.
                        val progress = (cloud.phase + t * cloud.speed) % 1f
                        val x = progress * (size.width + width) - width
                        translate(left = x, top = cloud.y * size.height) {
                            drawPath(paths[i], color = palette.cloud.copy(alpha = palette.alpha(cloud.alpha)))
                        }
                    }
                }
            }
    )
}

/** Üç oval: solda alçak, ortada yüksek, sağda orta boy. Hepsi aynı path'e eklenir. */
private fun cloudPath(width: Float, height: Float) = Path().apply {
    addOval(Rect(left = 0f, top = height * 0.40f, right = width * 0.60f, bottom = height))
    addOval(Rect(left = width * 0.25f, top = 0f, right = width * 0.75f, bottom = height * 0.85f))
    addOval(Rect(left = width * 0.45f, top = height * 0.30f, right = width, bottom = height))
}

// ---------------------------------------------------------------------------------------
// Yağmur
// ---------------------------------------------------------------------------------------

/** Yağmurun yoğunluğu: damla sayısı, hız aralığı, damla boyu ve kalınlığı. */
private enum class RainStyle(
    val count: Int,
    val minSpeed: Float,
    val maxSpeed: Float,
    val minLengthDp: Float,
    val maxLengthDp: Float,
    val strokeDp: Float,
    val alpha: Float
) {
    Drizzle(count = 40, minSpeed = 0.45f, maxSpeed = 0.65f, minLengthDp = 8f, maxLengthDp = 14f, strokeDp = 1f, alpha = 0.30f),
    Heavy(count = 140, minSpeed = 0.9f, maxSpeed = 1.4f, minLengthDp = 16f, maxLengthDp = 28f, strokeDp = 1.5f, alpha = 0.35f)
}

/** Tek bir damla. [x] ve [phase] 0..1 oranı, [speed] saniyede kaç ekran boyu düştüğü. */
private class RainDrop(val x: Float, val phase: Float, val speed: Float, val lengthDp: Float)

/**
 * Hafif eğik düşen yağmur.
 *
 * Damla düştükçe [RAIN_SLANT] oranında sağa kayar. Sol kenarın boş kalmaması için
 * damlalar ekranın biraz solundan da başlatılır.
 */
@Composable
private fun RainEffect(time: () -> Float, palette: EffectPalette, style: RainStyle, modifier: Modifier) {
    val drops = remember(style) {
        val random = Random(RAIN_SEED)
        List(style.count) {
            RainDrop(
                x = random.range(-RAIN_SLANT * 0.5f, 1f),
                phase = random.nextFloat(),
                speed = random.range(style.minSpeed, style.maxSpeed),
                lengthDp = random.range(style.minLengthDp, style.maxLengthDp)
            )
        }
    }
    Canvas(modifier = modifier.fillMaxSize()) {
        val t = time()
        val color = palette.rain.copy(alpha = palette.alpha(style.alpha))
        val stroke = style.strokeDp.dp.toPx()
        drops.forEach { drop ->
            val length = drop.lengthDp.dp.toPx()
            // Damla ekranın üstünden tamamen dışarıdan girer, altından tamamen çıkar.
            val progress = (drop.phase + t * drop.speed) % 1f
            val endY = progress * (size.height + length)
            val endX = drop.x * size.width + endY * RAIN_SLANT
            drawLine(
                color = color,
                start = Offset(endX - length * RAIN_SLANT, endY - length),
                end = Offset(endX, endY),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
        }
    }
}

// ---------------------------------------------------------------------------------------
// Kar
// ---------------------------------------------------------------------------------------

/** Tek bir kar tanesi. Salınım genliği ve frekansı her tanede farklı; hepsi aynı ritimde sallanmasın. */
private class Snowflake(
    val x: Float,
    val phase: Float,
    val speed: Float,
    val radiusDp: Float,
    val swayDp: Float,
    val swayFrequency: Float,
    val swayPhase: Float
)

/** Yavaşça düşen, sinüs dalgasıyla sağa sola salınan kar taneleri. */
@Composable
private fun SnowEffect(time: () -> Float, palette: EffectPalette, modifier: Modifier) {
    val flakes = remember {
        val random = Random(SNOW_SEED)
        List(SNOW_COUNT) {
            Snowflake(
                x = random.nextFloat(),
                phase = random.nextFloat(),
                // Saniyede ekran boyunun %5–11'i: bir tane ekranı 9–20 saniyede geçer.
                speed = random.range(0.05f, 0.11f),
                radiusDp = random.range(1.5f, 3.5f),
                swayDp = random.range(8f, 24f),
                swayFrequency = random.range(0.25f, 0.6f),
                swayPhase = random.range(0f, 2f * PI.toFloat())
            )
        }
    }
    Canvas(modifier = modifier.fillMaxSize()) {
        val t = time()
        flakes.forEach { flake ->
            val radius = flake.radiusDp.dp.toPx()
            val progress = (flake.phase + t * flake.speed) % 1f
            val y = progress * (size.height + radius * 2) - radius
            val sway = sin(t * flake.swayFrequency * 2f * PI.toFloat() + flake.swayPhase) * flake.swayDp.dp.toPx()
            // Büyük taneler biraz daha belirgin: yakında duruyormuş hissi verir.
            val alpha = palette.alpha(0.35f + 0.1f * flake.radiusDp)
            drawCircle(color = palette.snow.copy(alpha = alpha), radius = radius, center = Offset(flake.x * size.width + sway, y))
        }
    }
}

// ---------------------------------------------------------------------------------------
// Şimşek
// ---------------------------------------------------------------------------------------

/**
 * Birkaç saniyede bir çift çakma şimşek: önce güçlü, hemen ardından zayıf bir parlama.
 *
 * Zaman [LIGHTNING_CYCLE_SECONDS]'lık dilimlere bölünür. Her dilimde çakmanın başladığı
 * an, dilimin sırasından üretilen sabit bir sözde-rastgele sayıyla belirlenir; böylece
 * şimşekler düzenli bir metronom gibi değil, düzensiz aralıklarla gelir.
 */
@Composable
private fun LightningEffect(time: () -> Float, palette: EffectPalette, modifier: Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val t = time()
        val cycle = floor(t / LIGHTNING_CYCLE_SECONDS)
        val flashStart = cycle * LIGHTNING_CYCLE_SECONDS + hash(cycle) * LIGHTNING_JITTER_SECONDS
        val alpha = flashAlpha(t - flashStart)
        // Bütün ekranı kaplayan beyaz bir perde; opaklığı parlamanın şiddetidir.
        if (alpha > 0f) drawRect(color = palette.flash.copy(alpha = palette.alpha(alpha)))
    }
}

/**
 * Çakmadan bu yana geçen süreye göre parlaklık.
 * 0.00–0.12 sn: güçlü parlama, sönerek; 0.20–0.32 sn: zayıf ikinci parlama; sonrası karanlık.
 */
private fun flashAlpha(sinceFlash: Float): Float = when {
    sinceFlash < 0f -> 0f
    sinceFlash < 0.12f -> 0.45f * (1f - sinceFlash / 0.12f)
    sinceFlash in 0.20f..0.32f -> 0.20f * (1f - (sinceFlash - 0.20f) / 0.12f)
    else -> 0f
}

/** 0..1 arası sabit sözde-rastgele sayı. Aynı girdi her zaman aynı sonucu verir. */
private fun hash(n: Float): Float {
    val x = sin(n * 12.9898f) * 43758.547f
    return x - floor(x)
}

/** [min] ile [max] arasında rastgele bir sayı. */
private fun Random.range(min: Float, max: Float) = min + nextFloat() * (max - min)

// ---------------------------------------------------------------------------------------
// Ayarlar
// ---------------------------------------------------------------------------------------

/** Koyu temada tüm opaklıklar bu oranla çarpılır. */
private const val DARK_THEME_DIM = 0.55f

private const val SUN_RAY_COUNT = 12
private const val SUN_DEGREES_PER_SECOND = 4f
private const val SUN_BREATH_SECONDS = 5f

private const val CLOUD_COUNT = 4
private const val CLOUD_SEED = 7

/** Damlanın her 1 px düşüşte kaç px sağa kaydığı. */
private const val RAIN_SLANT = 0.22f
private const val RAIN_SEED = 42

private const val SNOW_COUNT = 70
private const val SNOW_SEED = 2026

private const val LIGHTNING_CYCLE_SECONDS = 6f

/** Her dilimde çakmanın başlayabileceği en geç an; dilim sonunda bir sonrakiyle çakışmasın diye kısa tutulur. */
private const val LIGHTNING_JITTER_SECONDS = 4f
