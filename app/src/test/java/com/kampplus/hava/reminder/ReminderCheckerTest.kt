package com.kampplus.hava.reminder

import com.kampplus.hava.data.ForecastApi
import com.kampplus.hava.model.toSnapshot
import com.kampplus.hava.testing.FakeReminderNotifier
import com.kampplus.hava.testing.FakeReminderRepository
import com.kampplus.hava.testing.FakeWeatherRepository
import com.kampplus.hava.testing.forecastWith
import com.kampplus.hava.testing.sampleDay
import com.kampplus.hava.testing.sampleReminder
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

/**
 * Hatırlatıcı işinin kararlarını doğrular: ne zaman bildirim gönderilir, ne zaman
 * yalnızca not alınır, hata ve süresi geçmiş kayıtlarda ne olur.
 */
class ReminderCheckerTest {

    /** Telefonun ve şehrin bugünü: 2 Kasım. Hedef gün: 7 Kasım (Cumartesi). */
    private val today = LocalDate.of(2026, 11, 2)
    private val target = LocalDate.of(2026, 11, 7)

    private val reminders = FakeReminderRepository(listOf(sampleReminder(targetDate = target)))
    private val weather = FakeWeatherRepository()
    private val notifier = FakeReminderNotifier()
    private val checker = ReminderChecker(reminders, weather, notifier, today = { today })

    /** Servisin hedef gün için döneceği tahmini ayarlar. */
    private fun serviceReturns(code: Int = 2, max: Double = 18.0, min: Double = 8.0, rain: Int? = 30) {
        weather.result = forecastWith(today, sampleDay(today), sampleDay(target, code, max, min, rain))
    }

    @Test
    fun `ilk kontrol ozeti kaydeder ama bildirim gondermez`() = runTest {
        serviceReturns()

        val result = checker.checkAll()

        assertEquals(ReminderChecker.Outcome.SUCCESS, result.outcome)
        assertTrue("İlk kontrolde bildirim olmamalı", notifier.sent.isEmpty())
        assertEquals(sampleDay(target).toSnapshot(), reminders.items.single().lastSnapshot)
    }

    @Test
    fun `hatirlatici 16 gunluk tahminle kontrol edilir`() = runTest {
        serviceReturns()

        checker.checkAll()

        assertEquals(ForecastApi.MAX_FORECAST_DAYS, weather.lastForecastDays)
    }

    @Test
    fun `belirgin degisimde bildirim gonderilir ve ozet guncellenir`() = runTest {
        serviceReturns()
        checker.checkAll()

        serviceReturns(code = 61, max = 14.0)
        checker.checkAll()

        val sent = notifier.sent.single()
        assertEquals("change", sent.kind)
        assertEquals("Cumartesi için tahmin değişti: artık yağmurlu, en yüksek 14° (önce 18°)", sent.message)
        assertEquals(14.0, reminders.items.single().lastSnapshot?.maxTemperatureC ?: 0.0, 0.01)
    }

    @Test
    fun `kucuk degisimde bildirim yok ve ozet ilk haliyle kalir`() = runTest {
        serviceReturns(max = 18.0)
        checker.checkAll()

        // Her gün 1° kayıyor: tek tek eşiğin altında ama üç günde 3° birikir.
        serviceReturns(max = 19.0)
        checker.checkAll()
        serviceReturns(max = 20.0)
        checker.checkAll()
        assertTrue(notifier.sent.isEmpty())
        assertEquals(18.0, reminders.items.single().lastSnapshot?.maxTemperatureC ?: 0.0, 0.01)

        serviceReturns(max = 21.0)
        checker.checkAll()
        // Karşılaştırma hep kullanıcıya en son söylenenle yapıldığı için birikmiş fark yakalanır.
        assertEquals("Cumartesi için tahmin değişti: en yüksek 21° (önce 18°)", notifier.sent.single().message)
    }

    @Test
    fun `ayni degisim ikinci kez bildirilmez`() = runTest {
        serviceReturns()
        checker.checkAll()
        serviceReturns(code = 61)
        checker.checkAll()

        checker.checkAll()

        assertEquals(1, notifier.sent.size)
    }

    @Test
    fun `ag hatasinda retry doner ve ozet degismez`() = runTest {
        weather.error = IOException("no network")

        val result = checker.checkAll()

        assertEquals(ReminderChecker.Outcome.RETRY, result.outcome)
        assertNull(reminders.items.single().lastSnapshot)
    }

    @Test
    fun `sunucu hatasinda da retry doner`() = runTest {
        weather.error = HttpException(Response.error<Unit>(503, "".toResponseBody("application/json".toMediaType())))

        assertEquals(ReminderChecker.Outcome.RETRY, checker.checkAll().outcome)
    }

    @Test
    fun `bozuk yanitta retry yapilmaz`() = runTest {
        // Yeniden denemek bozuk yanıtı düzeltmez; ertesi günü bekleriz.
        weather.error = IllegalStateException("no current block")

        assertEquals(ReminderChecker.Outcome.SUCCESS, checker.checkAll().outcome)
    }

    @Test
    fun `hedef gun sehrin bugununden onceyse hatirlatici silinir`() = runTest {
        // Şehirde artık 8 Kasım: 7 Kasım geçti.
        weather.result = forecastWith(target.plusDays(1), sampleDay(target.plusDays(1)))

        val result = checker.checkAll()

        assertTrue(reminders.items.isEmpty())
        assertEquals(0, result.remaining)
    }

    @Test
    fun `hedef gunun kendisinde hatirlatici silinmez`() = runTest {
        weather.result = forecastWith(target, sampleDay(target))

        checker.checkAll()

        assertEquals(1, reminders.items.size)
    }

    @Test
    fun `cok eski hatirlaticilar aga cikmadan silinir`() = runTest {
        reminders.add(sampleReminder(id = "old", targetDate = today.minusDays(3)))
        weather.error = IOException("no network")

        checker.checkAll()

        assertEquals(listOf("r1"), reminders.items.map { it.id })
    }

    @Test
    fun `hedef gun 16 gunluk pencerenin disindaysa beklenir`() = runTest {
        // Servis yalnızca bugünü döndü; hedef gün listede yok.
        weather.result = forecastWith(today, sampleDay(today))

        checker.checkAll()

        assertNull(reminders.items.single().lastSnapshot)
        assertEquals(1, reminders.items.size)
    }

    // --- Akşam özeti ---------------------------------------------------------------------

    @Test
    fun `aksam ozeti sehir ve kiyafet onerisiyle gonderilir`() = runTest {
        serviceReturns(code = 61, max = 14.0, min = 9.0, rain = 80)

        val outcome = checker.sendEveSummary("r1")

        assertEquals(ReminderChecker.Outcome.SUCCESS, outcome)
        val sent = notifier.sent.single()
        assertEquals("eve", sent.kind)
        assertTrue(sent.message, sent.message.startsWith("Yarın Ankara'da 14°, yağmurlu. "))
        assertTrue(sent.message, sent.message.contains("şemsiyeni unutma"))
    }

    @Test
    fun `silinmis hatirlatici icin aksam ozeti gonderilmez`() = runTest {
        serviceReturns()

        assertEquals(ReminderChecker.Outcome.SUCCESS, checker.sendEveSummary("yok"))
        assertTrue(notifier.sent.isEmpty())
    }

    @Test
    fun `aksam ozetinde ag hatasi retry doner`() = runTest {
        weather.error = IOException("no network")

        assertEquals(ReminderChecker.Outcome.RETRY, checker.sendEveSummary("r1"))
    }
}
