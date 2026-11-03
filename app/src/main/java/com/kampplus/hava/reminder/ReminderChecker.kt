package com.kampplus.hava.reminder

import android.util.Log
import com.google.gson.JsonSyntaxException
import com.kampplus.hava.data.ForecastApi
import com.kampplus.hava.data.ReminderRepository
import com.kampplus.hava.data.WeatherRepository
import com.kampplus.hava.model.Forecast
import com.kampplus.hava.model.ForecastChangeDetector
import com.kampplus.hava.model.ReminderMessages
import com.kampplus.hava.model.WeatherReminder
import com.kampplus.hava.model.toSnapshot
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import retrofit2.HttpException

/**
 * Hatırlatıcı işlerinin asıl mantığı. Worker'lar yalnızca bunu kurup çağırır.
 *
 * Neden ayrı bir sınıf? `CoroutineWorker` Android'e bağlıdır ve JVM testinde kurulması
 * zahmetlidir. Kararların hepsi burada, arayüzler üzerinden verilir ([ReminderRepository],
 * [WeatherRepository], [ReminderNotifier]); testte üçü de sahtesiyle değiştirilir.
 *
 * @param today Telefonun bugünü. Testte sabit bir tarih verilir.
 */
class ReminderChecker(
    private val reminders: ReminderRepository,
    private val weather: WeatherRepository,
    private val notifier: ReminderNotifier,
    private val today: () -> LocalDate = { LocalDate.now() }
) {

    /** İşin WorkManager'a ne söyleyeceği. */
    enum class Outcome { SUCCESS, RETRY }

    /**
     * Günlük kontrolün sonucu.
     *
     * @param remaining Kontrolden sonra kalan hatırlatıcı sayısı. Sıfırsa günlük iş iptal edilir.
     */
    data class DailyResult(val outcome: Outcome, val remaining: Int)

    /**
     * Bütün hatırlatıcıları kontrol eder.
     *
     * Bir hatırlatıcıda ağ hatası olsa da diğerleri kontrol edilir; en sonda [Outcome.RETRY]
     * dönülür. Yeniden denemede zaten kontrol edilmiş olanlar tekrar bildirim göndermez,
     * çünkü özetleri güncellenmiştir ve yeni tahminle aynıdır.
     */
    suspend fun checkAll(): DailyResult {
        // Ağa çıkmadan, dünyanın hiçbir yerinde artık "bugün" olamayacak kadar eski kayıtları sil.
        // Saat dilimleri arasındaki fark bir günden fazla olabileceği için iki gün pay bırakıyoruz;
        // kesin karar, aşağıda şehrin kendi tarihine göre verilir.
        reminders.removeExpired(today().minusDays(OFFLINE_EXPIRY_MARGIN_DAYS))

        var needsRetry = false
        reminders.reminders.first().forEach { reminder ->
            if (check(reminder) == Check.NETWORK_ERROR) needsRetry = true
        }
        val remaining = reminders.reminders.first().size
        return DailyResult(outcome = if (needsRetry) Outcome.RETRY else Outcome.SUCCESS, remaining = remaining)
    }

    /**
     * Hedef günden önceki akşamın son özeti: "Yarın Ankara'da 14°, yağmurlu. ...".
     *
     * Hatırlatıcı bu arada silinmişse ya da hedef gün tahminde yoksa sessizce biter.
     */
    suspend fun sendEveSummary(reminderId: String): Outcome {
        val reminder = reminders.reminders.first().firstOrNull { it.id == reminderId } ?: return Outcome.SUCCESS
        val forecast = when (val fetched = fetch(reminder, ForecastApi.FORECAST_DAYS)) {
            is Fetch.Ok -> fetched.forecast
            Fetch.NetworkError -> return Outcome.RETRY
            Fetch.BadResponse -> return Outcome.SUCCESS
        }
        val day = forecast.daily.firstOrNull { it.date == reminder.targetDate } ?: return Outcome.SUCCESS
        notifier.notifyEve(reminder, ReminderMessages.eveSummary(reminder.cityName, day))
        return Outcome.SUCCESS
    }

    /** Tek bir hatırlatıcının kontrolü; sonucu yalnızca testlerde ve günlükte okunur. */
    internal enum class Check { FIRST_SEEN, CHANGED, UNCHANGED, NOT_IN_RANGE, EXPIRED, NETWORK_ERROR, BAD_RESPONSE }

    internal suspend fun check(reminder: WeatherReminder): Check {
        val forecast = when (val fetched = fetch(reminder, ForecastApi.MAX_FORECAST_DAYS)) {
            is Fetch.Ok -> fetched.forecast
            Fetch.NetworkError -> return Check.NETWORK_ERROR
            Fetch.BadResponse -> return Check.BAD_RESPONSE
        }

        // "Bugün" şehrin bugünüdür (bkz. todayAt): telefon başka bir saat diliminde olabilir.
        val cityToday = forecast.current.observedAt.toLocalDate()
        if (reminder.targetDate.isBefore(cityToday)) {
            reminders.remove(reminder.id)
            return Check.EXPIRED
        }

        // Hedef gün henüz 16 günlük pencerenin dışındaysa beklenir; ilk kontrol pencereye girince yapılır.
        val day = forecast.daily.firstOrNull { it.date == reminder.targetDate } ?: return Check.NOT_IN_RANGE
        val snapshot = day.toSnapshot()

        val previous = reminder.lastSnapshot
        if (previous == null) {
            // İlk bakış: kullanıcı bu tahmini zaten görüyor; yalnızca not alırız, bildirim yok.
            reminders.update(reminder.copy(lastSnapshot = snapshot))
            return Check.FIRST_SEEN
        }

        val change = ForecastChangeDetector.detect(reminder.targetDate, previous, snapshot) ?: return Check.UNCHANGED
        notifier.notifyChange(reminder, change.message)
        // Özet yalnızca bildirim gönderilince güncellenir. Böylece her gün 1°'lik küçük kaymalar
        // birikip eşiği aşınca yakalanır: karşılaştırma hep kullanıcıya en son söylenen tahminledir.
        reminders.update(reminder.copy(lastSnapshot = snapshot))
        return Check.CHANGED
    }

    private sealed interface Fetch {
        data class Ok(val forecast: Forecast) : Fetch
        data object NetworkError : Fetch
        data object BadResponse : Fetch
    }

    /**
     * Tahmini çeker ve hatayı türüne göre ayırır.
     *
     * Ağ hatası ve sunucu hatası geçicidir: yeniden denemeye değer. Bozuk yanıt ise
     * yeniden denemekle düzelmez; o hatırlatıcı bu tur atlanır.
     */
    private suspend fun fetch(reminder: WeatherReminder, days: Int): Fetch = try {
        Fetch.Ok(weather.getForecast(reminder.latitude, reminder.longitude, forecastDays = days))
    } catch (e: IOException) {
        Log.w(TAG, "Hatırlatıcı ${reminder.id}: bağlantı hatası", e)
        Fetch.NetworkError
    } catch (e: HttpException) {
        Log.w(TAG, "Hatırlatıcı ${reminder.id}: HTTP ${e.code()}", e)
        Fetch.NetworkError
    } catch (e: JsonSyntaxException) {
        Log.e(TAG, "Hatırlatıcı ${reminder.id}: yanıt çözümlenemedi", e)
        Fetch.BadResponse
    } catch (e: IllegalStateException) {
        Log.e(TAG, "Hatırlatıcı ${reminder.id}: eksik veri", e)
        Fetch.BadResponse
    }

    private companion object {
        const val TAG = "ReminderChecker"

        /** Ağa çıkmadan silme için telefonun bugününden kaç gün öncesine bakılır. */
        const val OFFLINE_EXPIRY_MARGIN_DAYS = 2L
    }
}
