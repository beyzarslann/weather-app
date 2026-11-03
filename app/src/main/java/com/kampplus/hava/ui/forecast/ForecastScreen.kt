package com.kampplus.hava.ui.forecast

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kampplus.hava.R
import com.kampplus.hava.model.CurrentWeather
import com.kampplus.hava.model.DailyForecast
import com.kampplus.hava.model.Forecast
import com.kampplus.hava.model.HourlyForecast
import com.kampplus.hava.model.WeatherCondition
import com.kampplus.hava.ui.LoadState
import com.kampplus.hava.ui.component.ErrorView
import com.kampplus.hava.ui.theme.HavaTheme
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Tahmin ekranının bağlayıcı katmanı; ViewModel'i edinir ve state'i aktarır.
 * (Bkz. [com.kampplus.hava.ui.city.CityRoute] — aynı desen.)
 */
@Composable
fun ForecastRoute(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: ForecastViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ForecastScreen(
        cityName = viewModel.cityName,
        subtitle = viewModel.subtitle,
        uiState = uiState,
        onBack = onBack,
        onRetry = viewModel::onRetry,
        modifier = modifier
    )
}

/**
 * Bir şehrin hava tahmini.
 *
 * Üç bölüm: anlık durum başlığı, saatlik yatay liste, günlük dikey liste.
 * Dört durumun tamamı burada da karşılanır.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForecastScreen(
    cityName: String,
    subtitle: String,
    uiState: LoadState<Forecast>,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val condition = (uiState as? LoadState.Success)?.data?.current?.let {
        WeatherCondition.fromWeatherCode(it.weatherCode)
    }

    Scaffold(
        modifier = modifier.background(weatherBackground(condition)),
        // Scaffold ve üst bar kendi düz rengini çizmesin ki gradient görünsün.
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = { Text(cityName.ifEmpty { stringResource(R.string.detail_title) }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            when (uiState) {
                // Tek bir kart beklendiği için burada Shimmer yerine basit gösterge yeterli.
                LoadState.Loading -> CircularProgressIndicator()
                LoadState.Empty -> ErrorView(message = "Bu şehir için tahmin bulunamadı.")
                is LoadState.Error -> ErrorView(message = uiState.message, onRetry = onRetry)
                is LoadState.Success -> ForecastContent(subtitle = subtitle, forecast = uiState.data)
            }
        }
    }
}

@Composable
private fun ForecastContent(subtitle: String, forecast: Forecast, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            // İçerik ekrana sığmayabilir; dikey kaydırma ekliyoruz.
            .verticalScroll(rememberScrollState())
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        CurrentWeatherHeader(
            subtitle = subtitle,
            current = forecast.current,
            today = forecast.daily.firstOrNull(),
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        SectionTitle(stringResource(R.string.detail_hourly))
        HourlyRow(hours = forecast.hourly)
        SectionTitle(stringResource(R.string.detail_daily))
        Card(modifier = Modifier.padding(horizontal = 16.dp), colors = translucentCardColors()) {
            forecast.daily.forEachIndexed { index, day ->
                // İlk satır hariç her satırın üstüne ayırıcı çizgi.
                if (index > 0) HorizontalDivider()
                DailyRow(day = day, isToday = index == 0)
            }
        }
    }
}

/**
 * Ortalanmış dikey başlık: şehir → emoji → büyük sıcaklık → durum →
 * bugünün en yüksek/en düşüğü; en altta hissedilen/nem/rüzgâr kartı.
 */
@Composable
private fun CurrentWeatherHeader(subtitle: String, current: CurrentWeather, today: DailyForecast?, modifier: Modifier = Modifier) {
    val condition = WeatherCondition.fromWeatherCode(current.weatherCode)
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Text(
            text = condition.emoji,
            style = MaterialTheme.typography.displayMedium,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            text = degrees(current.temperatureC),
            style = MaterialTheme.typography.displayLarge.copy(
                fontSize = 96.sp,
                lineHeight = 96.sp,
                fontWeight = FontWeight.Light
            )
        )
        Text(text = condition.label, style = MaterialTheme.typography.titleMedium)
        // Günlük liste boş gelebilir; o zaman satırı hiç çizmiyoruz.
        today?.let {
            Text(
                text = stringResource(
                    R.string.detail_high_low,
                    it.maxTemperatureC.roundToInt(),
                    it.minTemperatureC.roundToInt()
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            colors = translucentCardColors()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Bu üç alan servis tarafından gönderilmeyebilir; yalnızca varsa çizilir.
                current.apparentTemperatureC?.let {
                    Metric(stringResource(R.string.detail_feels_like), degrees(it))
                }
                current.humidityPercent?.let {
                    Metric(stringResource(R.string.detail_humidity), "%$it")
                }
                current.windSpeedKmh?.let {
                    Metric(stringResource(R.string.detail_wind), "${it.roundToInt()} km/sa")
                }
            }
        }
    }
}

/**
 * Önümüzdeki 24 saat, yatay kaydırılabilir kartlar hâlinde.
 *
 * Servis günün başından itibaren veri gönderir; geçmiş saatleri gizlemek için
 * şu anki saatten öncekiler elenir.
 */
@Composable
private fun HourlyRow(hours: List<HourlyForecast>, modifier: Modifier = Modifier) {
    val now = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS)
    val upcoming = hours.filter { !it.time.isBefore(now) }.take(HOURLY_COUNT)
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(items = upcoming, key = { _, hour -> hour.time.toString() }) { index, hour ->
            // Liste şu anki saatten başladığı için ilk kart "şimdi"yi temsil eder.
            val isNow = index == 0
            // "Şimdi" kartı tema rengiyle öne çıkar; diğerleri yarı saydam kalır.
            val colors = if (isNow) {
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            } else {
                translucentCardColors()
            }
            Card(modifier = Modifier.width(76.dp), colors = colors) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = if (isNow) stringResource(R.string.now) else hour.time.format(HOUR_FORMAT),
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        text = WeatherCondition.fromWeatherCode(hour.weatherCode).emoji,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(text = degrees(hour.temperatureC), style = MaterialTheme.typography.titleMedium)
                    Text(
                        // Boş string yerine boşluk: kartların yüksekliği eşit kalsın.
                        text = percent(hour.precipitationProbability) ?: " ",
                        style = MaterialTheme.typography.labelSmall,
                        // primary renk primaryContainer zemininde soluk kalır; orada kartın yazı rengi kullanılır.
                        color = if (isNow) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

/** Günlük tahmin satırı: gün adı · yağış · emoji · en düşük / en yüksek. */
@Composable
private fun DailyRow(day: DailyForecast, isToday: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (isToday) stringResource(R.string.today) else day.date.format(DAY_FORMAT).replaceFirstChar(Char::titlecase),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = percent(day.precipitationProbability).orEmpty(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(44.dp),
            textAlign = TextAlign.End
        )
        Text(
            text = WeatherCondition.fromWeatherCode(day.weatherCode).emoji,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        Text(
            text = degrees(day.minTemperatureC),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(44.dp),
            textAlign = TextAlign.End
        )
        Text(
            text = degrees(day.maxTemperatureC),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.width(44.dp),
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = MaterialTheme.typography.titleMedium)
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(horizontal = 16.dp)
    )
}

/**
 * Tahmin ekranındaki tüm kartların ortak zemini.
 *
 * Arka plan hava durumuna göre gradient; opak kartlar onu örtüyordu.
 * Yüzey rengini %55 opaklıkla kullanınca gradient kartların içinden de
 * hissedilir, yazılar ise okunaklı kalır. Oranı değiştirmek için tek yer burası.
 */
@Composable
private fun translucentCardColors(): CardColors = CardDefaults.cardColors(
    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = CARD_ALPHA)
)

/** 18.4 → "18°". Kullanıcıya ondalık göstermeye gerek yok. */
private fun degrees(celsius: Double) = "${celsius.roundToInt()}°"

/** Yağış olasılığı yalnızca sıfırdan büyükse anlamlıdır. */
private fun percent(value: Int?) = value?.takeIf { it > 0 }?.let { "%$it" }

private const val HOURLY_COUNT = 24
private const val CARD_ALPHA = 0.55f
private val HOUR_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE", Locale.forLanguageTag("tr"))

@Preview(showBackground = true)
@Composable
private fun ForecastScreenPreview() {
    HavaTheme {
        ForecastScreen(
            cityName = "Ankara",
            subtitle = "Ankara, Türkiye",
            uiState = LoadState.Success(
                Forecast(
                    current = CurrentWeather(
                        temperatureC = 21.0,
                        weatherCode = 0,
                        observedAt = LocalDateTime.of(2026, 1, 15, 14, 0),
                        apparentTemperatureC = 20.0,
                        humidityPercent = 45,
                        windSpeedKmh = 12.0
                    ),
                    hourly = List(8) {
                        HourlyForecast(
                            time = LocalDateTime.now().plusHours(it.toLong()),
                            temperatureC = 18.0 + it,
                            weatherCode = 2,
                            precipitationProbability = it * 5
                        )
                    },
                    daily = List(7) {
                        DailyForecast(
                            date = LocalDate.of(2026, 1, 15).plusDays(it.toLong()),
                            minTemperatureC = 9.0 + it,
                            maxTemperatureC = 19.0 + it,
                            weatherCode = 61,
                            precipitationProbability = 40
                        )
                    }
                )
            ),
            onBack = {},
            onRetry = {}
        )
    }
}
