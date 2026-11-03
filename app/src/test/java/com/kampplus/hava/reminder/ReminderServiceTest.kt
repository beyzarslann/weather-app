package com.kampplus.hava.reminder

import com.kampplus.hava.testing.FakeReminderRepository
import com.kampplus.hava.testing.FakeReminderScheduler
import com.kampplus.hava.testing.sampleReminder
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Kayıt ile planlamanın birlikte yürüdüğünü ve akşam özetinin zamanlamasını doğrular. */
class ReminderServiceTest {

    private val repository = FakeReminderRepository()
    private val scheduler = FakeReminderScheduler()
    private val service = ReminderService(repository, scheduler)

    @Test
    fun `ekleme kaydeder ve isleri planlar`() = runTest {
        service.add(sampleReminder(id = "a"))

        assertEquals(listOf("a"), repository.items.map { it.id })
        assertEquals(listOf("daily", "check-soon", "eve:a"), scheduler.calls)
    }

    @Test
    fun `son hatirlatici silinince gunluk is iptal edilir`() = runTest {
        service.add(sampleReminder(id = "a"))
        service.add(sampleReminder(id = "b"))
        scheduler.calls.clear()

        service.remove("a")
        // Hâlâ bir hatırlatıcı var: günlük iş sürmeli.
        assertEquals(listOf("cancel-eve:a"), scheduler.calls)

        service.remove("b")
        assertEquals(listOf("cancel-eve:a", "cancel-eve:b", "cancel-daily"), scheduler.calls)
    }

    // --- Akşam özetinin zamanı ------------------------------------------------------------

    private val target = LocalDate.of(2026, 11, 7)

    @Test
    fun `aksam ozeti hedef gunden onceki aksam 20de`() {
        val now = LocalDateTime.of(2026, 11, 2, 9, 0)

        // 2 Kasım 09:00 → 6 Kasım 20:00 = 4 gün 11 saat.
        assertEquals(Duration.ofDays(4).plusHours(11), eveNotificationDelay(target, now))
    }

    @Test
    fun `aksam 20yi gecmisse ama hedef gun gelmediyse hemen gonderilir`() {
        assertEquals(Duration.ZERO, eveNotificationDelay(target, LocalDateTime.of(2026, 11, 6, 20, 0)))
        assertEquals(Duration.ZERO, eveNotificationDelay(target, LocalDateTime.of(2026, 11, 6, 23, 59)))
    }

    @Test
    fun `hedef gun basladiysa aksam ozeti planlanmaz`() {
        assertNull(eveNotificationDelay(target, LocalDateTime.of(2026, 11, 7, 0, 0)))
        assertNull(eveNotificationDelay(target, LocalDateTime.of(2026, 11, 8, 9, 0)))
    }
}
