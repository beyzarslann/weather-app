package com.kampplus.hava.model

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Bildirim metinlerinde gereken küçük Türkçe dil yardımcıları.
 *
 * Saf Kotlin'dir; Android kaynaklarına (strings.xml) bağlı değildir, çünkü metinler arka
 * plan işinde, kurallarla birlikte üretilir ve JVM testlerinde doğrulanır.
 */
internal object TurkishText {

    val LOCALE: Locale = Locale.forLanguageTag("tr")

    private val DAY_NAME: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE", LOCALE)
    private val DAY_AND_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM", LOCALE)

    /** Tarihin gün adı, büyük harfle: "Cumartesi". */
    fun dayName(date: LocalDate): String = date.format(DAY_NAME).replaceFirstChar { it.titlecase(LOCALE) }

    /** Gün adı, gün ve ay: "Cumartesi, 14 Kasım". Ekran başlığında ve hatırlatıcı metinlerinde kullanılır. */
    fun dayAndMonth(date: LocalDate): String = date.format(DAY_AND_MONTH).replaceFirstChar { it.titlecase(LOCALE) }

    /** Türkçe küçük harf: "Yağmurlu" → "yağmurlu". `lowercase()` tek başına "I"yı "i" yapardı. */
    fun lower(text: String): String = text.lowercase(LOCALE)

    /**
     * Özel isme bulunma eki: "Ankara'da", "İzmir'de", "Kars'ta", "Sinop'ta".
     *
     * İki ses kuralı:
     * - Ünlü uyumu: son ünlü kalınsa (a, ı, o, u) "-da", inceyse (e, i, ö, ü) "-de".
     * - Ünsüz benzeşmesi: kelime sert ünsüzle (f, s, t, k, ç, ş, h, p — "FıSTıKÇı ŞaHaP")
     *   bitiyorsa "d" yerine "t" gelir.
     *
     * Ünlüsü olmayan kısaltmalarda (ör. "NY") kalın ünlü varsayılır. Özel isim olduğu için
     * ek kesme işaretiyle ayrılır.
     */
    fun locative(name: String): String {
        val trimmed = name.trim()
        val lowered = lower(trimmed)
        val lastVowel = lowered.lastOrNull { it in VOWELS }
        val vowel = if (lastVowel != null && lastVowel in FRONT_VOWELS) 'e' else 'a'
        val lastLetter = lowered.lastOrNull()
        val consonant = if (lastLetter != null && lastLetter in HARD_CONSONANTS) 't' else 'd'
        return "$trimmed'$consonant$vowel"
    }

    private const val VOWELS = "aeıioöuü"
    private const val FRONT_VOWELS = "eiöü"
    private const val HARD_CONSONANTS = "fstkçşhp"
}
