package com.kampplus.hava.model

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/** Hedef günden önceki akşam gönderilen özet metnini doğrular. */
class ReminderMessagesTest {

    private fun day(code: Int, max: Double, min: Double, rain: Int? = null) = DailyForecast(
        date = LocalDate.of(2026, 11, 7),
        minTemperatureC = min,
        maxTemperatureC = max,
        weatherCode = code,
        precipitationProbability = rain
    )

    @Test
    fun `ozet sehir sicaklik durum ve kiyafet onerisini icerir`() {
        // 9–15°: ortalama 12° (serin), %80 yağmur.
        val message = ReminderMessages.eveSummary("Ankara", day(code = 61, max = 14.6, min = 9.0, rain = 80))

        assertEquals("Yarın Ankara'da 15°, yağmurlu. Serin bir gün; ceketini al ve şemsiyeni unutma.", message)
    }

    @Test
    fun `sehir adinin ekine unlu uyumu uygulanir`() {
        val message = ReminderMessages.eveSummary("İzmir", day(code = 0, max = 24.0, min = 24.0))

        assertEquals("Yarın İzmir'de 24°, açık. Sıcak bir gün; hafif giyin.", message)
    }
}
