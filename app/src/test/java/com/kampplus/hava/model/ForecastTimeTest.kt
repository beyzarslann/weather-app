package com.kampplus.hava.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "Şu an" ve "bugün"ün telefonun saatinden değil, şehrin saatinden (observedAt) belirlendiğini doğrular.
 *
 * Senaryo: telefon İstanbul'da (UTC+3), bakılan şehir Tokyo (UTC+9). Servis Tokyo'nun yerel
 * saatini gönderir. İstanbul'da 21:45 iken Tokyo'da ertesi gün 03:45'tir; liste 03:00'ten
 * başlamalı, "bugün" de Tokyo'nun tarihi olmalıdır.
 */
class ForecastTimeTest {

    /** Tokyo'da gözlem zamanı: 2 Kasım 03:45 (İstanbul'da 1 Kasım 21:45). */
    private val tokyoObservedAt = LocalDateTime.of(2026, 11, 2, 3, 45)

    /** Tokyo saatiyle 1 Kasım 00:00'dan başlayan 36 saatlik liste (servisin gönderdiği gibi). */
    private val tokyoHours = List(36) {
        HourlyForecast(
            time = LocalDateTime.of(2026, 11, 1, 0, 0).plusHours(it.toLong()),
            temperatureC = 10.0 + it,
            weatherCode = 0
        )
    }

    private fun day(date: LocalDate) = DailyForecast(date = date, minTemperatureC = 5.0, maxTemperatureC = 15.0, weatherCode = 0)

    @Test
    fun `saatlik liste sehrin su anki saatinden baslar`() {
        val upcoming = tokyoHours.upcomingFrom(tokyoObservedAt, limit = 24)

        // 03:45 → saat başına yuvarlanır; "Şimdi" 03:00'tır, telefonun 21:00'i değil.
        assertEquals(LocalDateTime.of(2026, 11, 2, 3, 0), upcoming.first().time)
        assertTrue("Geçmiş saat listede olmamalı", upcoming.none { it.time.isBefore(currentHourOf(tokyoObservedAt)) })
    }

    @Test
    fun `liste limit kadar saat icerir`() {
        assertEquals(24, tokyoHours.upcomingFrom(LocalDateTime.of(2026, 11, 1, 0, 0), limit = 24).size)
        // Gün sonuna yakın gözlemde elde kalan saat sayısı kadar döner, hata vermez.
        assertEquals(1, tokyoHours.upcomingFrom(LocalDateTime.of(2026, 11, 2, 11, 30), limit = 24).size)
    }

    @Test
    fun `sonuc telefonun saat diliminden etkilenmez`() {
        // Kod LocalDateTime.now() kullansaydı, varsayılan saat dilimi değişince sonuç da değişirdi.
        val original = TimeZone.getDefault()
        try {
            val results = listOf("Europe/Istanbul", "America/Los_Angeles", "Asia/Tokyo").map { zone ->
                TimeZone.setDefault(TimeZone.getTimeZone(zone))
                tokyoHours.upcomingFrom(tokyoObservedAt, limit = 24).first().time
            }
            assertEquals(listOf(LocalDateTime.of(2026, 11, 2, 3, 0)), results.distinct())
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test
    fun `bugun sehrin tarihine gore secilir`() {
        val days = listOf(day(LocalDate.of(2026, 11, 1)), day(LocalDate.of(2026, 11, 2)), day(LocalDate.of(2026, 11, 3)))

        // İstanbul'da hâlâ 1 Kasım ama Tokyo'da 2 Kasım: "bugün" 2 Kasım olmalı.
        assertEquals(LocalDate.of(2026, 11, 2), days.todayAt(tokyoObservedAt)?.date)
    }

    @Test
    fun `eslesen tarih yoksa ilk gune dusulur`() {
        val days = listOf(day(LocalDate.of(2026, 11, 5)), day(LocalDate.of(2026, 11, 6)))

        assertEquals(LocalDate.of(2026, 11, 5), days.todayAt(tokyoObservedAt)?.date)
    }

    @Test
    fun `gunluk liste bossa bugun yoktur`() {
        assertEquals(null, emptyList<DailyForecast>().todayAt(tokyoObservedAt))
    }
}
