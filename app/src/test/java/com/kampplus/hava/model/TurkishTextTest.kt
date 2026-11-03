package com.kampplus.hava.model

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/** Bildirim metinlerindeki Türkçe dil kurallarını doğrular. */
class TurkishTextTest {

    @Test
    fun `bulunma eki unlu uyumuna gore secilir`() {
        assertEquals("Ankara'da", TurkishText.locative("Ankara"))
        assertEquals("İstanbul'da", TurkishText.locative("İstanbul"))
        assertEquals("İzmir'de", TurkishText.locative("İzmir"))
        assertEquals("Rize'de", TurkishText.locative("Rize"))
        assertEquals("Ürgüp'te", TurkishText.locative("Ürgüp"))
    }

    @Test
    fun `sert unsuzle biten isimde d yerine t gelir`() {
        assertEquals("Kars'ta", TurkishText.locative("Kars"))
        assertEquals("Sinop'ta", TurkishText.locative("Sinop"))
        assertEquals("Tokat'ta", TurkishText.locative("Tokat"))
        assertEquals("Bayburt'ta", TurkishText.locative("Bayburt"))
    }

    @Test
    fun `buyuk I harfi kalin unlu sayilir`() {
        // Locale'siz lowercase "I"yı "i" (ince) yapardı ve "ISPARTA'de" gibi hatalı ek üretirdi.
        assertEquals("ISPARTA'da", TurkishText.locative("ISPARTA"))
    }

    @Test
    fun `gun adi buyuk harfle baslar`() {
        assertEquals("Cumartesi", TurkishText.dayName(LocalDate.of(2026, 11, 7)))
        assertEquals("Pazartesi", TurkishText.dayName(LocalDate.of(2026, 11, 2)))
    }

    @Test
    fun `gun ve ay birlikte yazilir`() {
        assertEquals("Cumartesi, 14 Kasım", TurkishText.dayAndMonth(LocalDate.of(2026, 11, 14)))
        assertEquals("Pazar, 1 Kasım", TurkishText.dayAndMonth(LocalDate.of(2026, 11, 1)))
    }
}
