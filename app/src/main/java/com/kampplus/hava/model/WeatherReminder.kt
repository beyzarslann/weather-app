package com.kampplus.hava.model

import java.time.LocalDate

/**
 * "Hava hatırlatıcısı": kullanıcının ileri bir tarih için takip ettiği şehir.
 *
 * Arka plandaki iş o güne kadar her gün tahmini kontrol eder; tahmin belirgin
 * değişirse bildirim gönderir (bkz. [ForecastChangeDetector]).
 *
 * Şehrin adı ve koordinatları burada kopyalanır, [City] nesnesi saklanmaz: hatırlatıcı
 * uygulama kapalıyken de çalışır ve şehri yeniden aramak zorunda kalmamalıdır.
 *
 * @param id Benzersiz kimlik (UUID). Bildirim ve arka plan işleri bununla eşleşir.
 * @param subtitle "Ankara, Türkiye"; bildirime dokununca açılan tahmin ekranının alt başlığı.
 * @param targetDate Takip edilen gün, **şehrin** yerel tarihiyle.
 * @param lastSnapshot Son kontrolde görülen tahmin. İlk kontrolden önce null'dır;
 *   ilk kontrol yalnızca bunu doldurur, bildirim göndermez.
 */
data class WeatherReminder(
    val id: String,
    val cityName: String,
    val subtitle: String,
    val latitude: Double,
    val longitude: Double,
    val targetDate: LocalDate,
    val lastSnapshot: ForecastSnapshot? = null
)

/**
 * Bir günün tahmininden, değişimi anlamak için gereken kadarı.
 *
 * [DailyForecast]'ın tamamını saklamıyoruz: UV ya da rüzgâr değişince bildirim
 * göndermiyoruz, saklamaya da gerek yok. Az alan = daha küçük JSON, daha az kırılganlık.
 */
data class ForecastSnapshot(
    val weatherCode: Int,
    val maxTemperatureC: Double,
    val minTemperatureC: Double,
    val precipitationProbability: Int? = null
)

/**
 * Hatırlatıcı için seçilebilecek en ileri gün, bugünden itibaren.
 *
 * Servis en fazla 16 günlük tahmin verir: bugün + 15 gün. Daha ileri bir gün seçilirse
 * hatırlatıcı o gün tahmine girene kadar hiçbir şey yapamaz ve kullanıcı neden bildirim
 * gelmediğini anlamaz; bu yüzden seçime hiç izin vermiyoruz.
 */
const val REMINDER_MAX_DAYS_AHEAD = 15L

/**
 * Hatırlatıcı kurulabilecek tarihler: yarından [REMINDER_MAX_DAYS_AHEAD] gün sonrasına kadar (dahil).
 *
 * Bugün seçilemez: hatırlatıcının işi "o güne kadar" tahmini izlemektir; bugün için
 * izlenecek bir süre kalmamıştır, tahmin ekranı zaten önünde.
 *
 * @param cityToday Şehrin bugünü (bkz. todayAt). Telefon başka bir saat diliminde olabilir.
 */
fun reminderDateRange(cityToday: LocalDate): ClosedRange<LocalDate> = cityToday.plusDays(1)..cityToday.plusDays(REMINDER_MAX_DAYS_AHEAD)

/** Günlük tahminden karşılaştırma özetini çıkarır. */
fun DailyForecast.toSnapshot() = ForecastSnapshot(
    weatherCode = weatherCode,
    maxTemperatureC = maxTemperatureC,
    minTemperatureC = minTemperatureC,
    precipitationProbability = precipitationProbability
)
