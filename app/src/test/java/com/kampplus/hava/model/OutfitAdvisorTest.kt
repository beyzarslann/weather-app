package com.kampplus.hava.model

import com.kampplus.hava.model.OutfitItem.BEANIE
import com.kampplus.hava.model.OutfitItem.COAT
import com.kampplus.hava.model.OutfitItem.GLOVES
import com.kampplus.hava.model.OutfitItem.HAT
import com.kampplus.hava.model.OutfitItem.HEAVY_COAT
import com.kampplus.hava.model.OutfitItem.JACKET
import com.kampplus.hava.model.OutfitItem.LIGHT_JACKET
import com.kampplus.hava.model.OutfitItem.LIGHT_TROUSERS
import com.kampplus.hava.model.OutfitItem.LONG_SLEEVES
import com.kampplus.hava.model.OutfitItem.SCARF
import com.kampplus.hava.model.OutfitItem.SHORTS
import com.kampplus.hava.model.OutfitItem.SUNGLASSES
import com.kampplus.hava.model.OutfitItem.SUNSCREEN
import com.kampplus.hava.model.OutfitItem.SWEATER
import com.kampplus.hava.model.OutfitItem.T_SHIRT
import com.kampplus.hava.model.OutfitItem.UMBRELLA
import com.kampplus.hava.model.OutfitItem.WATERPROOF_BOOTS
import com.kampplus.hava.model.OutfitItem.WINDBREAKER
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "Bugün ne giysem?" kurallarını doğrular.
 *
 * Her testte yalnızca incelenen alan değiştirilir; diğerleri hiçbir kuralı tetiklemeyecek
 * nötr değerlerdedir (yağış yok, rüzgâr yok, UV düşük, gün içi fark yok, açık hava).
 * Böylece bir test kırıldığında suçlu kural hemen belli olur.
 */
class OutfitAdvisorTest {

    private fun day(
        min: Double,
        max: Double = min,
        rainChance: Int? = null,
        weatherCode: Int = 0,
        maxWindKmh: Double? = null,
        uv: Double? = null
    ) = DailyForecast(
        date = LocalDate.of(2026, 11, 1),
        minTemperatureC = min,
        maxTemperatureC = max,
        weatherCode = weatherCode,
        precipitationProbability = rainChance,
        uvIndexMax = uv,
        maxWindSpeedKmh = maxWindKmh
    )

    /** Hissedilen verilmezse fark yok sayılır; anlık sıcaklığın kendisi kurallarda kullanılmaz. */
    private fun current(temperature: Double = 15.0, apparent: Double? = null) = CurrentWeather(
        temperatureC = temperature,
        weatherCode = 0,
        observedAt = LocalDateTime.of(2026, 11, 1, 9, 0),
        apparentTemperatureC = apparent
    )

    private fun advise(today: DailyForecast, now: CurrentWeather = current()) = OutfitAdvisor.advise(today, now)

    /** Gün boyu sabit [temperature] derece olan nötr bir günün parçaları. */
    private fun itemsAt(temperature: Double) = advise(day(temperature)).items

    // --- Sıcaklık bantları ve sınırları ----------------------------------------------------

    @Test
    fun `0 derece ve alti dondurucu bant`() {
        val freezing = listOf(HEAVY_COAT, BEANIE, SCARF, GLOVES)
        assertEquals(freezing, itemsAt(-10.0))
        assertEquals(freezing, itemsAt(0.0))
    }

    @Test
    fun `1 ile 8 derece arasi soguk bant`() {
        assertEquals(listOf(COAT, SCARF), itemsAt(1.0))
        assertEquals(listOf(COAT, SCARF), itemsAt(8.0))
    }

    @Test
    fun `9 ile 14 derece arasi serin bant`() {
        assertEquals(listOf(JACKET, SWEATER), itemsAt(9.0))
        assertEquals(listOf(JACKET, SWEATER), itemsAt(14.0))
    }

    @Test
    fun `15 ile 20 derece arasi ilik bant`() {
        assertEquals(listOf(LIGHT_JACKET, LONG_SLEEVES), itemsAt(15.0))
        assertEquals(listOf(LIGHT_JACKET, LONG_SLEEVES), itemsAt(20.0))
    }

    @Test
    fun `21 ile 26 derece arasi sicak bant`() {
        assertEquals(listOf(T_SHIRT, LIGHT_TROUSERS), itemsAt(21.0))
        assertEquals(listOf(T_SHIRT, LIGHT_TROUSERS), itemsAt(26.0))
    }

    @Test
    fun `27 derece ve ustu cok sicak bant`() {
        assertEquals(listOf(T_SHIRT, SHORTS, HAT), itemsAt(27.0))
        assertEquals(listOf(T_SHIRT, SHORTS, HAT), itemsAt(40.0))
    }

    @Test
    fun `ondalikli sicaklik en yakin tam sayiya yuvarlanir`() {
        // 8,4 → 8 (soğuk), 8,5 → 9 (serin): iki bant arasında "boşluğa düşen" değer olmaz.
        assertEquals(listOf(COAT, SCARF), itemsAt(8.4))
        assertEquals(listOf(JACKET, SWEATER), itemsAt(8.5))
    }

    @Test
    fun `temel sicaklik gunun en dusuk ve en yuksek ortalamasidir`() {
        // Anlık sıcaklık 30° olsa da gün 4–8° arası: ortalama 6°, soğuk bant.
        val advice = advise(day(min = 4.0, max = 8.0), current(temperature = 30.0))

        assertEquals(listOf(COAT, SCARF), advice.items)
    }

    // --- Hissedilen sıcaklık ------------------------------------------------------------

    @Test
    fun `hissedilen daha soguksa fark kadar asagi kaydirilir`() {
        // Ortalama 16° (ılık) ama rüzgâr yüzünden 4° daha soğuk hissediliyor → 12° (serin).
        val advice = advise(day(16.0), current(temperature = 16.0, apparent = 12.0))

        assertEquals(listOf(JACKET, SWEATER), advice.items)
        assertTrue(advice.summary, advice.summary.contains("hissedilen daha soğuk"))
    }

    @Test
    fun `hissedilen daha sicaksa yukari kaydirilmaz`() {
        val advice = advise(day(16.0), current(temperature = 16.0, apparent = 22.0))

        assertEquals(listOf(LIGHT_JACKET, LONG_SLEEVES), advice.items)
    }

    @Test
    fun `kucuk hissedilen farki cumlede belirtilmez`() {
        // 2° fark bandı değiştirebilir ama cümlede ayrıca anılacak kadar belirgin değil.
        val advice = advise(day(16.0), current(temperature = 16.0, apparent = 14.0))

        assertFalse(advice.summary, advice.summary.contains("hissedilen"))
    }

    // --- Yağış ---------------------------------------------------------------------------

    @Test
    fun `yagis olasiligi yuzde 50 ve ustunde semsiye eklenir`() {
        val advice = advise(day(15.0, rainChance = 50))

        assertTrue(UMBRELLA in advice.items)
        assertTrue(advice.summary, advice.summary.contains("şemsiyeni unutma"))
    }

    @Test
    fun `yagis olasiligi yuzde 30 ile 49 arasinda yalnizca not dusulur`() {
        listOf(30, 49).forEach { chance ->
            val advice = advise(day(15.0, rainChance = chance))

            assertFalse("%$chance için şemsiye listede olmamalı", UMBRELLA in advice.items)
            assertTrue(advice.summary, advice.summary.contains("yanına şemsiye al"))
        }
    }

    @Test
    fun `yagis olasiligi yuzde 30 altinda semsiyeden soz edilmez`() {
        val advice = advise(day(15.0, rainChance = 29))

        assertFalse(UMBRELLA in advice.items)
        assertFalse(advice.summary, advice.summary.contains("şemsiye"))
    }

    // --- Kar ---------------------------------------------------------------------------

    @Test
    fun `kar kodlarinda su gecirmez bot eklenir`() {
        // 71 = hafif kar, 86 = yoğun kar sağanağı.
        listOf(71, 86).forEach { code ->
            assertTrue("Kod $code için bot bekleniyordu", WATERPROOF_BOOTS in advise(day(-2.0, weatherCode = code)).items)
        }
    }

    @Test
    fun `yagmur kodunda bot eklenmez`() {
        assertFalse(WATERPROOF_BOOTS in advise(day(5.0, weatherCode = 61)).items)
    }

    // --- Rüzgâr -------------------------------------------------------------------------

    @Test
    fun `gunluk en yuksek ruzgar 30 km sa ve ustunde ruzgarlik eklenir`() {
        assertTrue(WINDBREAKER in advise(day(15.0, maxWindKmh = 30.0)).items)
        assertFalse(WINDBREAKER in advise(day(15.0, maxWindKmh = 29.9)).items)
    }

    // --- Gün içi fark ---------------------------------------------------------------------

    @Test
    fun `gun ici fark 10 derece ve ustunde katmanli giyin notu`() {
        val layered = advise(day(min = 10.0, max = 20.0))
        val steady = advise(day(min = 10.0, max = 19.9))

        assertTrue(layered.summary, layered.summary.contains("katmanlı giyin"))
        assertFalse(steady.summary, steady.summary.contains("katmanlı"))
    }

    // --- UV -----------------------------------------------------------------------------

    @Test
    fun `uv 6 ve ustunde gunes gozlugu ve krem eklenir`() {
        val high = advise(day(25.0, uv = 6.0)).items
        val moderate = advise(day(25.0, uv = 5.9)).items

        assertTrue(SUNGLASSES in high && SUNSCREEN in high)
        assertFalse(SUNGLASSES in moderate || SUNSCREEN in moderate)
    }

    // --- Eksik veri -----------------------------------------------------------------------

    @Test
    fun `servis yagis uv ve ruzgar gondermezse yalnizca bant parcalari onerilir`() {
        val advice = advise(day(15.0, rainChance = null, maxWindKmh = null, uv = null))

        assertEquals(listOf(LIGHT_JACKET, LONG_SLEEVES), advice.items)
    }

    // --- Özet cümlesi ----------------------------------------------------------------------

    @Test
    fun `degisken ve yagmurlu gunun ozeti`() {
        // Sabah 9° (serin), öğleden sonra 19° (ılık), %60 yağmur.
        val advice = advise(day(min = 9.0, max = 19.0, rainChance = 60, weatherCode = 61))

        assertEquals("Gün serin başlayıp ılınıyor; katmanlı giyin ve şemsiyeni unutma.", advice.summary)
    }

    @Test
    fun `sabit bir gunun ozeti bant tavsiyesini kullanir`() {
        assertEquals("Dondurucu bir gün; sıkı giyin.", advise(day(-3.0)).summary)
        assertEquals("Serin bir gün; ceketini al.", advise(day(12.0)).summary)
    }

    @Test
    fun `ucten fazla not virgul ve ve ile baglanir`() {
        // Sıcak, güneşli ve rüzgârlı: bant tavsiyesi + rüzgârlık + krem.
        val advice = advise(day(24.0, maxWindKmh = 35.0, uv = 8.0))

        assertEquals("Sıcak bir gün; hafif giyin, rüzgârlığını al ve güneş kremi sür.", advice.summary)
    }

    @Test
    fun `parcalar tekrar etmez`() {
        val advice = advise(day(min = -5.0, max = 6.0, rainChance = 80, weatherCode = 75, maxWindKmh = 40.0, uv = 7.0))

        assertEquals(advice.items.distinct(), advice.items)
    }
}
