package com.kampplus.hava.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.kampplus.hava.model.ForecastSnapshot
import com.kampplus.hava.testing.sampleReminder
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Gerçek DataStore'u geçici bir dosyayla çalıştırarak saklamayı uçtan uca doğrular.
 *
 * DataStore'un çekirdeği saf JVM'dir; Context yerine dosya yolunu doğrudan verince
 * emülatörsüz test edilebilir. Böylece Gson + LocalDate adaptörünün gerçekten
 * yazıp geri okuyabildiği de sınanır.
 */
class ReminderRepositoryTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val repository by lazy {
        DataStoreReminderRepository(
            PreferenceDataStoreFactory.create(scope = scope) { folder.newFile("reminders.preferences_pb").also { it.delete() } }
        )
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `eklenen hatirlatici tarihi ve ozetiyle geri okunur`() = runTest {
        val reminder = sampleReminder(id = "a").copy(
            lastSnapshot = ForecastSnapshot(
                weatherCode = 61,
                maxTemperatureC = 14.0,
                minTemperatureC = 6.0,
                precipitationProbability = null
            )
        )

        repository.add(reminder)

        assertEquals(listOf(reminder), repository.reminders.first())
    }

    @Test
    fun `liste hedef tarihe gore siralidir`() = runTest {
        repository.add(sampleReminder(id = "late", targetDate = LocalDate.of(2026, 11, 20)))
        repository.add(sampleReminder(id = "soon", targetDate = LocalDate.of(2026, 11, 5)))

        assertEquals(listOf("soon", "late"), repository.reminders.first().map { it.id })
    }

    @Test
    fun `guncelleme yalnizca eslesen kaydi degistirir`() = runTest {
        repository.add(sampleReminder(id = "a"))
        repository.add(sampleReminder(id = "b"))
        val snapshot = ForecastSnapshot(weatherCode = 0, maxTemperatureC = 20.0, minTemperatureC = 10.0)

        repository.update(sampleReminder(id = "b").copy(lastSnapshot = snapshot))

        val byId = repository.reminders.first().associateBy { it.id }
        assertEquals(null, byId.getValue("a").lastSnapshot)
        assertEquals(snapshot, byId.getValue("b").lastSnapshot)
    }

    @Test
    fun `silme ve suresi gecenleri temizleme`() = runTest {
        repository.add(sampleReminder(id = "past", targetDate = LocalDate.of(2026, 11, 1)))
        repository.add(sampleReminder(id = "today", targetDate = LocalDate.of(2026, 11, 2)))
        repository.add(sampleReminder(id = "future", targetDate = LocalDate.of(2026, 11, 9)))

        val removed = repository.removeExpired(today = LocalDate.of(2026, 11, 2))
        repository.remove("future")

        assertEquals(listOf("past"), removed.map { it.id })
        assertEquals(listOf("today"), repository.reminders.first().map { it.id })
    }

    @Test
    fun `bozuk json bos liste olarak okunur`() {
        assertTrue(ReminderJson.decode("{bu json değil").isEmpty())
        assertTrue(ReminderJson.decode("""[{"id":"a","targetDate":"7 Kasım"}]""").isEmpty())
        assertTrue(ReminderJson.decode(null).isEmpty())
    }
}
