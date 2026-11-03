package com.kampplus.hava.model

import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

/** Tahminin hangi yönden değiştiği. Bir değişimin birden çok nedeni olabilir. */
enum class ChangeReason { CONDITION, MAX_TEMPERATURE, MIN_TEMPERATURE, PRECIPITATION }

/**
 * Belirgin bir tahmin değişimi.
 *
 * @param reasons Hangi kuralların tetiklendiği; testlerde ve ileride ayar ekranında işe yarar.
 * @param message Bildirimde gösterilecek kısa Türkçe metin.
 */
data class ForecastChange(val reasons: Set<ChangeReason>, val message: String)

/**
 * İki tahmin özetini karşılaştırıp "kullanıcıya haber vermeye değer mi?" sorusunu yanıtlar.
 *
 * Saf Kotlin'dir: Android'e bağımlı değildir, her kural JVM testinde sınır değerleriyle
 * doğrulanır (bkz. ForecastChangeDetectorTest).
 *
 * Neden eşik var? Tahmin her gün biraz oynar; 18°'nin 19° olması için bildirim göndermek
 * kullanıcıyı yorar ve bildirimleri kapattırır. Yalnızca planı değiştirebilecek
 * farklar bildirilir.
 *
 * Sıcaklıklar karşılaştırılmadan önce **tam sayıya yuvarlanır**: kullanıcı ekranda ve
 * bildirimde tam sayı görür. Ham değerle karşılaştırsaydık 17,6° → 15,4° (2,2° fark)
 * bildirilmez ama 18° → 15° görünürdü; ya da tersi, mesaj "18° (önce 15°)" der ama
 * eşik tutmazdı. Yuvarlanmış değerle karar ve mesaj hep tutarlıdır.
 */
object ForecastChangeDetector {

    /**
     * Sıcaklık (en yüksek veya en düşük) bu kadar derece ya da daha fazla değişirse bildirilir.
     * 3° çoğu insan için giysi kararını değiştiren farktır (ör. ceketten monta geçiş).
     */
    const val TEMPERATURE_THRESHOLD_C = 3

    /** Yağış olasılığı bu kadar yüzde puan ya da daha fazla değişirse bildirilir (ör. %30 → %50). */
    const val PRECIPITATION_THRESHOLD_POINTS = 20

    /**
     * Değişim varsa [ForecastChange], yoksa null döner.
     *
     * Kurallar:
     * - Hava türü ([WeatherCondition]) değişirse. Taraflardan biri [WeatherCondition.UNKNOWN]
     *   ise sayılmaz: servis tanımadığımız bir kod gönderdi diye "tahmin değişti" demek yanıltıcı olur.
     * - En yüksek veya en düşük sıcaklık [TEMPERATURE_THRESHOLD_C] ya da daha fazla değişirse.
     * - Yağış olasılığı [PRECIPITATION_THRESHOLD_POINTS] ya da daha fazla değişirse. İki taraf da
     *   biliniyorsa karşılaştırılır; servis bir gün göndermediyse bu "%0'a düştü" sayılmaz.
     *
     * @param date Hedef gün; mesajdaki gün adı için ("Cumartesi için...").
     */
    fun detect(date: LocalDate, old: ForecastSnapshot, new: ForecastSnapshot): ForecastChange? {
        val reasons = linkedSetOf<ChangeReason>()
        val parts = mutableListOf<String>()

        val oldCondition = WeatherCondition.fromWeatherCode(old.weatherCode)
        val newCondition = WeatherCondition.fromWeatherCode(new.weatherCode)
        val bothKnown = oldCondition != WeatherCondition.UNKNOWN && newCondition != WeatherCondition.UNKNOWN
        if (bothKnown && oldCondition != newCondition) {
            reasons += ChangeReason.CONDITION
            parts += "artık ${TurkishText.lower(newCondition.label)}"
        }

        val oldMax = old.maxTemperatureC.roundToInt()
        val newMax = new.maxTemperatureC.roundToInt()
        if (abs(newMax - oldMax) >= TEMPERATURE_THRESHOLD_C) {
            reasons += ChangeReason.MAX_TEMPERATURE
            parts += "en yüksek $newMax° (önce $oldMax°)"
        }

        val oldMin = old.minTemperatureC.roundToInt()
        val newMin = new.minTemperatureC.roundToInt()
        if (abs(newMin - oldMin) >= TEMPERATURE_THRESHOLD_C) {
            reasons += ChangeReason.MIN_TEMPERATURE
            parts += "en düşük $newMin° (önce $oldMin°)"
        }

        val oldRain = old.precipitationProbability
        val newRain = new.precipitationProbability
        if (oldRain != null && newRain != null && abs(newRain - oldRain) >= PRECIPITATION_THRESHOLD_POINTS) {
            reasons += ChangeReason.PRECIPITATION
            parts += "yağış olasılığı %$newRain (önce %$oldRain)"
        }

        if (reasons.isEmpty()) return null
        val message = "${TurkishText.dayName(date)} için tahmin değişti: ${parts.joinToString(", ")}"
        return ForecastChange(reasons = reasons, message = message)
    }
}
