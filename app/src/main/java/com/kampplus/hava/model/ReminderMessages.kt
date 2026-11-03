package com.kampplus.hava.model

import kotlin.math.roundToInt

/**
 * Hatırlatıcı bildirimlerinin metinleri.
 *
 * Değişim metnini [ForecastChangeDetector] üretir; burada yalnızca hedef günden önceki
 * akşam gönderilen son özet var. Saf Kotlin'dir; JVM testinde doğrulanır.
 */
object ReminderMessages {

    /**
     * "Yarın Ankara'da 14°, yağmurlu. Serin bir gün; ceketini al ve şemsiyeni unutma."
     *
     * Sıcaklık olarak günün en yükseği verilir: insanlar "yarın kaç derece?" diye
     * sorduğunda genellikle gündüzü kastederler. Ardından kıyafet önerisinin özeti eklenir.
     * Öneri yarın içindir; bugünün hissedilen sıcaklığı ona karıştırılmaz (current = null).
     */
    fun eveSummary(cityName: String, day: DailyForecast): String {
        val condition = TurkishText.lower(WeatherCondition.fromWeatherCode(day.weatherCode).label)
        val headline = "Yarın ${TurkishText.locative(cityName)} ${day.maxTemperatureC.roundToInt()}°, $condition."
        val outfit = OutfitAdvisor.advise(day, current = null).summary
        return "$headline $outfit"
    }
}
