package com.kampplus.hava.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * Bir şehrin anlık durumu + önümüzdeki saatler ve günler.
 *
 * Tahmin ekranının veri modeli: bir şehre dokunulduğunda gösterilen bilgiler.
 * Zamanlar `timezone=auto` sayesinde şehrin **yerel** saatindedir.
 */
data class Forecast(
    val current: CurrentWeather,
    val hourly: List<HourlyForecast>,
    val daily: List<DailyForecast>
)

// ---------------------------------------------------------------------------------------
// "Şu an" ve "bugün" şehrin saatine göre
//
// Servis tüm zamanları şehrin yerel saatinde gönderir (timezone=auto) ve LocalDateTime
// saat dilimi taşımaz. Bu yüzden "şu an"ı telefonun saatinden (LocalDateTime.now())
// almak yanlıştır: İstanbul'daki bir telefonla Tokyo'ya bakınca saatlik liste 6 saat kayar.
// Doğru referans, servisin aynı yanıtta gönderdiği gözlem zamanıdır: current.observedAt.
// Aşağıdaki fonksiyonlar saf Kotlin'dir ve sistem saatine hiç dokunmaz; test edilebilmeleri
// de bundandır.
// ---------------------------------------------------------------------------------------

/** Gözlem zamanının saat başı: 14:45 → 14:00. Saatlik listede "Şimdi" kartının saati budur. */
fun currentHourOf(observedAt: LocalDateTime): LocalDateTime = observedAt.truncatedTo(ChronoUnit.HOURS)

/**
 * Şehrin şu anki saatinden itibaren en fazla [limit] saatlik tahmin.
 *
 * Servis günün başından itibaren veri gönderir; geçmiş saatler elenir. İçinde
 * bulunulan saat ("Şimdi") listede kalır.
 */
fun List<HourlyForecast>.upcomingFrom(observedAt: LocalDateTime, limit: Int): List<HourlyForecast> {
    val currentHour = currentHourOf(observedAt)
    return filter { !it.time.isBefore(currentHour) }.take(limit)
}

/**
 * Şehrin bugününe ait günlük tahmin.
 *
 * Open-Meteo listeyi şehrin bugününden başlatır, yani çoğu zaman bu ilk öğedir.
 * Yine de tarihe bakarak seçiyoruz: ileride `past_days` gibi bir parametre eklenirse
 * ilk öğe dün olur ve "Bugün" etiketi sessizce yanlış güne kayardı. Eşleşen tarih
 * yoksa ilk öğeye düşülür.
 */
fun List<DailyForecast>.todayAt(observedAt: LocalDateTime): DailyForecast? {
    val date = observedAt.toLocalDate()
    return firstOrNull { it.date == date } ?: firstOrNull()
}

/**
 * Şu anki hava. Son üç alan servis tarafından her zaman gönderilmez,
 * bu yüzden nullable'dır ve ekranda yalnızca varsa gösterilir.
 */
data class CurrentWeather(
    val temperatureC: Double,
    val weatherCode: Int,
    val observedAt: LocalDateTime,
    val apparentTemperatureC: Double? = null,
    val humidityPercent: Int? = null,
    val windSpeedKmh: Double? = null
)

/** Tek bir saatlik tahmin satırı. */
data class HourlyForecast(
    val time: LocalDateTime,
    val temperatureC: Double,
    val weatherCode: Int,
    val precipitationProbability: Int? = null
)

/**
 * Tek bir günlük tahmin satırı: o günün en düşük ve en yüksek sıcaklığı.
 *
 * Son üç alan servis tarafından gönderilmeyebilir; nullable oldukları için eksik
 * olduklarında uygulama bozulmaz, yalnızca ilgili öneri (şemsiye, güneş kremi,
 * rüzgârlık) verilmez.
 *
 * @param uvIndexMax Günün en yüksek UV indeksi (0–11+).
 * @param maxWindSpeedKmh Günün en yüksek rüzgâr hızı (10 m yükseklikte, km/sa).
 */
data class DailyForecast(
    val date: LocalDate,
    val minTemperatureC: Double,
    val maxTemperatureC: Double,
    val weatherCode: Int,
    val precipitationProbability: Int? = null,
    val uvIndexMax: Double? = null,
    val maxWindSpeedKmh: Double? = null
)
