package com.kampplus.hava.model

import kotlin.math.roundToInt

/**
 * "Bugün ne giysem?" önerisinin tek bir parçası: bir emoji ve Türkçe bir etiket.
 *
 * [WeatherCondition] ile aynı fikir: ekran bir enum değeri alır, nasıl görüneceğini
 * enum'un kendisi bilir.
 */
enum class OutfitItem(val emoji: String, val label: String) {
    // Sıcaklık bantlarından gelen parçalar, soğuktan sıcağa.
    HEAVY_COAT("🧥", "Kalın mont"),
    BEANIE("🧶", "Bere"),
    SCARF("🧣", "Atkı"),
    GLOVES("🧤", "Eldiven"),
    COAT("🧥", "Mont"),
    JACKET("🧥", "Ceket"),
    SWEATER("🧶", "Kazak"),
    LIGHT_JACKET("🧥", "Hafif ceket veya hırka"),
    LONG_SLEEVES("👔", "Uzun kollu"),
    T_SHIRT("👕", "Tişört"),
    LIGHT_TROUSERS("👖", "İnce pantolon"),
    SHORTS("🩳", "Şort"),
    HAT("🧢", "Şapka"),

    // Ek kurallardan gelen parçalar.
    UMBRELLA("☂️", "Şemsiye"),
    WATERPROOF_BOOTS("🥾", "Su geçirmez bot"),
    WINDBREAKER("🌬️", "Rüzgârlık"),
    SUNGLASSES("🕶️", "Güneş gözlüğü"),
    SUNSCREEN("🧴", "Güneş kremi")
}

/**
 * Önerinin sonucu.
 *
 * @param items Önerilen parçalar; önce sıcaklığa göre giysiler, ardından ek kurallarınkiler.
 * @param summary Kararları özetleyen kısa bir Türkçe cümle.
 */
data class OutfitAdvice(val items: List<OutfitItem>, val summary: String)

/**
 * Kural tabanlı kıyafet önerisi. Yapay zekâ yok; her karar aşağıdaki eşiklerden birine dayanır.
 *
 * Saf Kotlin'dir: Android'e, Compose'a ya da ağa bağımlılığı yoktur. Bu sayede JVM
 * üzerinde, emülatör açmadan, her kural tek tek test edilebilir (bkz. OutfitAdvisorTest).
 *
 * Öneri **günün geneline** göre verilir, o anki sıcaklığa göre değil: sabah 9°'de
 * çıkıp öğleden sonra 20°'yi gören biri, yalnızca şu ana bakan bir öneriyle ya
 * üşür ya da terler.
 *
 * Adımlar:
 * 1. Temel sıcaklık = (en düşük + en yüksek) / 2.
 * 2. Hissedilen sıcaklık gerçeğinden düşükse (rüzgâr, nem), aradaki fark kadar aşağı kaydırılır.
 *    Tersi (hissedilen daha sıcak) yok sayılır: fazladan bir katman taşımak, üşümekten iyidir.
 * 3. Sonuç en yakın tam sayıya yuvarlanır ve bir sıcaklık bandına düşer; bant temel giysileri verir.
 * 4. Yağış, kar, rüzgâr, gün içi sıcaklık farkı ve UV ek parçalar ve notlar ekler.
 */
object OutfitAdvisor {

    /**
     * Bugün için öneri üretir.
     *
     * @param today Bugünün günlük tahmini (en düşük/en yüksek, yağış, UV, rüzgâr).
     * @param current Anlık durum; yalnızca hissedilen sıcaklık farkı için kullanılır.
     */
    fun advise(today: DailyForecast, current: CurrentWeather): OutfitAdvice {
        val effective = effectiveTemperature(today, current)
        val band = TemperatureBand.of(effective)

        val items = band.items.toMutableList()
        val notes = mutableListOf<String>()

        // Gün içinde sıcaklık çok değişiyorsa tek kalın parça yerine çıkarılabilir katmanlar.
        val spread = today.maxTemperatureC - today.minTemperatureC
        notes += if (spread >= LAYERING_SPREAD_C) "katmanlı giyin" else band.advice

        val rainChance = today.precipitationProbability ?: 0
        when {
            rainChance >= RAIN_LIKELY_PERCENT -> {
                items += OutfitItem.UMBRELLA
                notes += "şemsiyeni unutma"
            }
            // Orta olasılık: şemsiyeyi listeye koymayız ama hatırlatırız.
            rainChance >= RAIN_POSSIBLE_PERCENT -> notes += "yanına şemsiye al"
        }

        if (WeatherCondition.fromWeatherCode(today.weatherCode) in SNOW_CONDITIONS) {
            items += OutfitItem.WATERPROOF_BOOTS
            notes += "su geçirmez bot giy"
        }

        if ((today.maxWindSpeedKmh ?: 0.0) >= WINDY_KMH) {
            items += OutfitItem.WINDBREAKER
            notes += "rüzgârlığını al"
        }

        if ((today.uvIndexMax ?: 0.0) >= HIGH_UV_INDEX) {
            items += OutfitItem.SUNGLASSES
            items += OutfitItem.SUNSCREEN
            notes += "güneş kremi sür"
        }

        val summary = "${opening(today, effective, current)}; ${joinTurkish(notes)}."
        return OutfitAdvice(items = items.distinct(), summary = summary)
    }

    /**
     * Kararların dayandığı sıcaklık: günün ortalaması, hissedilen daha soğuksa o kadar aşağı.
     * Bantlar tam sayı olarak tanımlı olduğu için sonuç yuvarlanır (8,4° → 8, 8,5° → 9).
     */
    internal fun effectiveTemperature(today: DailyForecast, current: CurrentWeather): Int {
        val average = (today.minTemperatureC + today.maxTemperatureC) / 2
        return (average - feelsColderBy(current)).roundToInt()
    }

    /** Hissedilen sıcaklık gerçeğinden kaç derece düşük? Daha sıcak ya da bilinmiyorsa 0. */
    private fun feelsColderBy(current: CurrentWeather): Double {
        val apparent = current.apparentTemperatureC ?: return 0.0
        return (current.temperatureC - apparent).coerceAtLeast(0.0)
    }

    /**
     * Cümlenin ilk yarısı: günün karakteri.
     *
     * Gün içi fark büyükse sabahtan öğleye değişimi anlatırız ("Gün serin başlayıp ılınıyor").
     * Değilse günü tek kelimeyle niteleriz ("Serin bir gün").
     */
    private fun opening(today: DailyForecast, effective: Int, current: CurrentWeather): String {
        val spread = today.maxTemperatureC - today.minTemperatureC
        val morning = TemperatureBand.of(today.minTemperatureC.roundToInt())
        val afternoon = TemperatureBand.of(today.maxTemperatureC.roundToInt())
        val base = if (spread >= LAYERING_SPREAD_C && morning != afternoon) {
            "Gün ${morning.adjective} başlayıp ${afternoon.warmingVerb}"
        } else {
            "${TemperatureBand.of(effective).adjective.replaceFirstChar(Char::uppercaseChar)} bir gün"
        }
        // Rüzgâr/nem yüzünden belirgin biçimde daha soğuk hissediliyorsa bunu da söyleriz.
        return if (feelsColderBy(current) >= NOTICEABLY_COLDER_C) "$base, hissedilen daha soğuk" else base
    }

    /** ["a", "b", "c"] → "a, b ve c". Türkçede son iki öğe "ve" ile bağlanır. */
    private fun joinTurkish(parts: List<String>): String = when (parts.size) {
        0 -> ""
        1 -> parts.first()
        else -> parts.dropLast(1).joinToString(", ") + " ve " + parts.last()
    }

    // -----------------------------------------------------------------------------------
    // Eşikler. Hepsi tek yerde; bir kuralı değiştirmek için yalnızca buraya bakmak yeter.
    // -----------------------------------------------------------------------------------

    /** Yağış olasılığı bu yüzde ve üstündeyse şemsiye listeye girer. */
    const val RAIN_LIKELY_PERCENT = 50

    /** Bu yüzde ile [RAIN_LIKELY_PERCENT] arasında şemsiye yalnızca not olarak hatırlatılır. */
    const val RAIN_POSSIBLE_PERCENT = 30

    /** Günün en yüksek rüzgârı bu hız (km/sa) ve üstündeyse rüzgârlık önerilir. Beaufort 5: "sert esinti". */
    const val WINDY_KMH = 30.0

    /** En yüksek ile en düşük arasındaki fark bu kadar (°C) ve üstündeyse katmanlı giyim önerilir. */
    const val LAYERING_SPREAD_C = 10.0

    /** UV indeksi bu değer ve üstündeyse güneş gözlüğü ve kremi önerilir. DSÖ ölçeğinde 6–7 "yüksek"tir. */
    const val HIGH_UV_INDEX = 6.0

    /** Hissedilen sıcaklık en az bu kadar düşükse cümlede ayrıca belirtilir. */
    private const val NOTICEABLY_COLDER_C = 3.0

    /** Su geçirmez bot gerektiren durumlar. */
    private val SNOW_CONDITIONS = setOf(WeatherCondition.SNOW, WeatherCondition.SNOW_SHOWERS)
}

/**
 * Sıcaklık bantları. Her bant bir üst sınırla (dahil) tanımlanır; bir sıcaklık,
 * üst sınırı kendisinden büyük ya da eşit olan **ilk** banda düşer.
 *
 * @param upperC Bandın en yüksek sıcaklığı (°C, dahil). Son bandın üst sınırı yoktur.
 * @param items Bu bandın temel giysileri.
 * @param adjective Cümlede günü niteleyen sıfat ("serin bir gün").
 * @param warmingVerb Gün öğleden sonra bu banda ısınıyorsa kullanılan fiil ("ılınıyor").
 *   En soğuk bant hiçbir zaman "varılan" bant olamaz; onunki yalnızca eksiksiz olsun diye vardır.
 * @param advice Katmanlı giyim notu yoksa cümleye eklenen bant tavsiyesi.
 */
internal enum class TemperatureBand(
    val upperC: Int,
    val items: List<OutfitItem>,
    val adjective: String,
    val warmingVerb: String,
    val advice: String
) {
    /** ≤ 0° */
    FREEZING(
        upperC = 0,
        items = listOf(OutfitItem.HEAVY_COAT, OutfitItem.BEANIE, OutfitItem.SCARF, OutfitItem.GLOVES),
        adjective = "dondurucu",
        warmingVerb = "biraz ısınıyor",
        advice = "sıkı giyin"
    ),

    /** 1–8° */
    COLD(
        upperC = 8,
        items = listOf(OutfitItem.COAT, OutfitItem.SCARF),
        adjective = "soğuk",
        warmingVerb = "biraz ısınıyor",
        advice = "montunu al"
    ),

    /** 9–14° */
    COOL(
        upperC = 14,
        items = listOf(OutfitItem.JACKET, OutfitItem.SWEATER),
        adjective = "serin",
        // "serinliyor" demiyoruz: o fiil soğumayı anlatır, burada ise hava ısınıyor.
        warmingVerb = "biraz ısınıyor",
        advice = "ceketini al"
    ),

    /** 15–20° */
    MILD(
        upperC = 20,
        items = listOf(OutfitItem.LIGHT_JACKET, OutfitItem.LONG_SLEEVES),
        adjective = "ılık",
        warmingVerb = "ılınıyor",
        advice = "yanına ince bir hırka al"
    ),

    /** 21–26° */
    WARM(
        upperC = 26,
        items = listOf(OutfitItem.T_SHIRT, OutfitItem.LIGHT_TROUSERS),
        adjective = "sıcak",
        warmingVerb = "ısınıyor",
        advice = "hafif giyin"
    ),

    /** ≥ 27° */
    HOT(
        upperC = Int.MAX_VALUE,
        items = listOf(OutfitItem.T_SHIRT, OutfitItem.SHORTS, OutfitItem.HAT),
        adjective = "çok sıcak",
        warmingVerb = "iyice ısınıyor",
        advice = "ince giyin ve bol su iç"
    );

    companion object {
        /** Sıcaklığın düştüğü bant. Son bandın sınırı sonsuz olduğu için her zaman bir sonuç vardır. */
        fun of(temperatureC: Int): TemperatureBand = entries.first { temperatureC <= it.upperC }
    }
}
