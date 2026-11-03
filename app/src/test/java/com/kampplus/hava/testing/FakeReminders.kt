package com.kampplus.hava.testing

import com.kampplus.hava.data.ReminderRepository
import com.kampplus.hava.model.CurrentWeather
import com.kampplus.hava.model.DailyForecast
import com.kampplus.hava.model.Forecast
import com.kampplus.hava.model.WeatherReminder
import com.kampplus.hava.reminder.ReminderNotifier
import com.kampplus.hava.reminder.ReminderScheduler
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/*
 * Hatırlatıcı testlerinin sahteleri. Gerçekleri DataStore, WorkManager ve Android
 * bildirimleri gerektirir; sahteler yalnızca "ne istendi?" bilgisini tutar.
 */

/** Bellekte duran hatırlatıcı listesi. [items] ile testte doğrudan okunur. */
class FakeReminderRepository(initial: List<WeatherReminder> = emptyList()) : ReminderRepository {

    private val state = MutableStateFlow(initial)

    val items: List<WeatherReminder> get() = state.value

    override val reminders: Flow<List<WeatherReminder>> = state.map { list -> list.sortedBy { it.targetDate } }

    override suspend fun add(reminder: WeatherReminder) {
        state.value = state.value.filterNot { it.id == reminder.id } + reminder
    }

    override suspend fun update(reminder: WeatherReminder) {
        state.value = state.value.map { if (it.id == reminder.id) reminder else it }
    }

    override suspend fun remove(id: String) {
        state.value = state.value.filterNot { it.id == id }
    }

    override suspend fun removeExpired(today: LocalDate): List<WeatherReminder> {
        val (expired, active) = state.value.partition { it.targetDate.isBefore(today) }
        state.value = active
        return expired
    }
}

/** Gönderilen bildirimleri sırayla kaydeder. */
class FakeReminderNotifier : ReminderNotifier {
    data class Sent(val kind: String, val reminderId: String, val message: String)

    val sent = mutableListOf<Sent>()

    override fun notifyChange(reminder: WeatherReminder, message: String) {
        sent += Sent("change", reminder.id, message)
    }

    override fun notifyEve(reminder: WeatherReminder, message: String) {
        sent += Sent("eve", reminder.id, message)
    }
}

/** Planlama çağrılarını sırayla kaydeder: "daily", "cancel-daily", "check-soon", "eve:<id>", "cancel-eve:<id>". */
class FakeReminderScheduler : ReminderScheduler {
    val calls = mutableListOf<String>()

    override fun ensureDailyCheck() {
        calls += "daily"
    }

    override fun cancelDailyCheck() {
        calls += "cancel-daily"
    }

    override fun checkSoon() {
        calls += "check-soon"
    }

    override fun scheduleEve(reminder: WeatherReminder) {
        calls += "eve:${reminder.id}"
    }

    override fun cancelEve(reminderId: String) {
        calls += "cancel-eve:$reminderId"
    }
}

/** Ankara için hazır bir hatırlatıcı. */
fun sampleReminder(id: String = "r1", targetDate: LocalDate = LocalDate.of(2026, 11, 7)) = WeatherReminder(
    id = id,
    cityName = "Ankara",
    subtitle = "Ankara, Türkiye",
    latitude = 39.92,
    longitude = 32.85,
    targetDate = targetDate
)

/** Bir günlük tahmin satırı; yalnızca karşılaştırmada kullanılan alanlar. */
fun sampleDay(date: LocalDate, code: Int = 2, max: Double = 18.0, min: Double = 8.0, rain: Int? = 30) = DailyForecast(
    date = date,
    minTemperatureC = min,
    maxTemperatureC = max,
    weatherCode = code,
    precipitationProbability = rain
)

/** Şehrin "şu an"ı [observedOn] günü olan, verilen günleri içeren bir tahmin. */
fun forecastWith(observedOn: LocalDate, vararg days: DailyForecast) = Forecast(
    current = CurrentWeather(temperatureC = 12.0, weatherCode = 2, observedAt = LocalDateTime.of(observedOn, java.time.LocalTime.NOON)),
    hourly = emptyList(),
    daily = days.toList()
)
