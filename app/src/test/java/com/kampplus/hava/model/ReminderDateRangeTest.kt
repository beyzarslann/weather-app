package com.kampplus.hava.model

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tarih seçicide hangi günlerin seçilebileceğini doğrular (16 günlük tahmin sınırı). */
class ReminderDateRangeTest {

    private val today = LocalDate.of(2026, 11, 2)
    private val range = reminderDateRange(today)

    @Test
    fun `aralik yarindan baslar ve bugunden 15 gun sonra biter`() {
        assertEquals(LocalDate.of(2026, 11, 3), range.start)
        assertEquals(LocalDate.of(2026, 11, 17), range.endInclusive)
    }

    @Test
    fun `bugun ve gecmis secilemez`() {
        assertFalse(today in range)
        assertFalse(today.minusDays(1) in range)
    }

    @Test
    fun `16 gunluk tahminin disi secilemez`() {
        assertTrue(today.plusDays(15) in range)
        assertFalse(today.plusDays(16) in range)
    }

    @Test
    fun `ay ve yil sinirini gecen aralik dogru hesaplanir`() {
        val newYear = reminderDateRange(LocalDate.of(2026, 12, 25))

        assertEquals(LocalDate.of(2026, 12, 26), newYear.start)
        assertEquals(LocalDate.of(2027, 1, 9), newYear.endInclusive)
    }
}
