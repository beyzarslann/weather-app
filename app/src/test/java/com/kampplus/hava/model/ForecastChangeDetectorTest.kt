package com.kampplus.hava.model

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tahmin değişimi kurallarını sınır değerleriyle doğrular.
 *
 * Her testte yalnızca incelenen alan değişir; diğer alanlar [base] ile aynı kalır.
 * Böylece bir test kırılırsa hangi kuralın bozulduğu hemen anlaşılır.
 */
class ForecastChangeDetectorTest {

    /** 7 Kasım 2026 bir cumartesidir. */
    private val saturday = LocalDate.of(2026, 11, 7)

    /** Parçalı bulutlu, 18°/8°, %30 yağış. */
    private val base = ForecastSnapshot(weatherCode = 2, maxTemperatureC = 18.0, minTemperatureC = 8.0, precipitationProbability = 30)

    private fun detect(new: ForecastSnapshot) = ForecastChangeDetector.detect(saturday, base, new)

    @Test
    fun `hicbir sey degismezse null doner`() {
        assertNull(detect(base))
    }

    // --- Hava türü ----------------------------------------------------------------------

    @Test
    fun `hava turu degisirse bildirilir`() {
        val change = detect(base.copy(weatherCode = 61))

        assertEquals(setOf(ChangeReason.CONDITION), change?.reasons)
        assertEquals("Cumartesi için tahmin değişti: artık yağmurlu", change?.message)
    }

    @Test
    fun `ayni turun farkli kodlari degisim sayilmaz`() {
        // 61 ve 65 ikisi de "Yağmurlu": kod değişti ama kullanıcının göreceği tür aynı.
        val old = base.copy(weatherCode = 61)
        assertNull(ForecastChangeDetector.detect(saturday, old, old.copy(weatherCode = 65)))
    }

    @Test
    fun `bilinmeyen koda gecis degisim sayilmaz`() {
        assertNull(detect(base.copy(weatherCode = 999)))
    }

    // --- Sıcaklık ----------------------------------------------------------------------

    @Test
    fun `en yuksek 3 derece degisirse bildirilir 2 derece degisirse bildirilmez`() {
        assertEquals(setOf(ChangeReason.MAX_TEMPERATURE), detect(base.copy(maxTemperatureC = 15.0))?.reasons)
        assertEquals(setOf(ChangeReason.MAX_TEMPERATURE), detect(base.copy(maxTemperatureC = 21.0))?.reasons)
        assertNull(detect(base.copy(maxTemperatureC = 16.0)))
        assertNull(detect(base.copy(maxTemperatureC = 20.0)))
    }

    @Test
    fun `en dusuk 3 derece degisirse bildirilir 2 derece degisirse bildirilmez`() {
        assertEquals(setOf(ChangeReason.MIN_TEMPERATURE), detect(base.copy(minTemperatureC = 5.0))?.reasons)
        assertEquals(setOf(ChangeReason.MIN_TEMPERATURE), detect(base.copy(minTemperatureC = 11.0))?.reasons)
        assertNull(detect(base.copy(minTemperatureC = 6.0)))
    }

    @Test
    fun `sicaklik ekranda gorunen tam sayilarla karsilastirilir`() {
        // 18,0 → 15,4: ham fark 2,6° ama ekranda 18° → 15°; kullanıcı 3° fark görür.
        assertNotNull(detect(base.copy(maxTemperatureC = 15.4)))
        // 18,0 → 15,6: ekranda 16°; görünen fark 2°, bildirilmez.
        assertNull(detect(base.copy(maxTemperatureC = 15.6)))
    }

    @Test
    fun `sicaklik mesaji yeni ve eski degeri icerir`() {
        assertEquals(
            "Cumartesi için tahmin değişti: en yüksek 14° (önce 18°)",
            detect(base.copy(maxTemperatureC = 14.0))?.message
        )
    }

    // --- Yağış ---------------------------------------------------------------------------

    @Test
    fun `yagis olasiligi 20 puan degisirse bildirilir 19 puan bildirilmez`() {
        assertEquals(setOf(ChangeReason.PRECIPITATION), detect(base.copy(precipitationProbability = 50))?.reasons)
        assertEquals(setOf(ChangeReason.PRECIPITATION), detect(base.copy(precipitationProbability = 10))?.reasons)
        assertNull(detect(base.copy(precipitationProbability = 49)))
        assertNull(detect(base.copy(precipitationProbability = 11)))
    }

    @Test
    fun `yagis bilgisi bir tarafta yoksa karsilastirilmaz`() {
        // Servis bir gün yağış göndermedi diye "%30'dan %0'a düştü" denmemeli.
        assertNull(detect(base.copy(precipitationProbability = null)))
        assertNull(
            ForecastChangeDetector.detect(saturday, base.copy(precipitationProbability = null), base.copy(precipitationProbability = 90))
        )
    }

    // --- Birleşik mesaj -------------------------------------------------------------------

    @Test
    fun `birden cok degisim tek mesajda virgulle birlesir`() {
        val change = detect(base.copy(weatherCode = 61, maxTemperatureC = 14.0, precipitationProbability = 80))

        assertEquals(setOf(ChangeReason.CONDITION, ChangeReason.MAX_TEMPERATURE, ChangeReason.PRECIPITATION), change?.reasons)
        assertEquals(
            "Cumartesi için tahmin değişti: artık yağmurlu, en yüksek 14° (önce 18°), yağış olasılığı %80 (önce %30)",
            change?.message
        )
    }
}
