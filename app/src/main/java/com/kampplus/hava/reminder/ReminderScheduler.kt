package com.kampplus.hava.reminder

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.kampplus.hava.model.WeatherReminder
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * Hatırlatıcı işlerini planlar ve iptal eder.
 *
 * Arayüz olarak tanımlı ki [ReminderService] testlerinde WorkManager kurmadan
 * "günlük iş planlandı mı, iptal edildi mi?" doğrulanabilsin.
 */
interface ReminderScheduler {
    /** Günlük kontrolü planlar. Zaten planlıysa dokunmaz. */
    fun ensureDailyCheck()

    /** Günlük kontrolü iptal eder (hatırlatıcı kalmadığında). */
    fun cancelDailyCheck()

    /** Yeni eklenen hatırlatıcının ilk özetinin hemen alınması için tek seferlik bir kontrol. */
    fun checkSoon()

    /** Hedef günden önceki akşam 20:00'ye son özet bildirimini planlar. */
    fun scheduleEve(reminder: WeatherReminder)

    /** Hatırlatıcı silinince akşam bildirimini iptal eder. */
    fun cancelEve(reminderId: String)

    /** Yalnızca debug: akşam özetini beklemeden, hemen gönderir. */
    fun sendEveNow(reminderId: String)
}

/**
 * Gerçek uygulama: WorkManager.
 *
 * WorkManager, uygulama kapalı olsa ve telefon yeniden başlasa bile işin çalışacağını
 * garanti eder. Pil tasarrufu için tam saatinde değil, sistemin uygun gördüğü bir
 * anda çalıştırır; günlük bir kontrol için bu yeterlidir.
 *
 * "Unique work": aynı adla iki kez planlama yapılırsa ikinci kopya oluşmaz. Böylece
 * her hatırlatıcı eklendiğinde günlük işin yeni bir kopyası birikmez.
 */
class WorkManagerReminderScheduler(context: Context) : ReminderScheduler {

    private val workManager = WorkManager.getInstance(context.applicationContext)

    /** İnternet yoksa iş bekletilir; bağlantı gelince çalışır. */
    private val needsNetwork = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    override fun ensureDailyCheck() {
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(DAILY_INTERVAL_HOURS, TimeUnit.HOURS)
            .setConstraints(needsNetwork)
            .build()
        // KEEP: zaten planlıysa sayaç sıfırlanmasın; yoksa her eklemede ilk çalışma ertelenirdi.
        workManager.enqueueUniquePeriodicWork(DAILY_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    override fun cancelDailyCheck() {
        workManager.cancelUniqueWork(DAILY_WORK_NAME)
    }

    override fun checkSoon() {
        val request = OneTimeWorkRequestBuilder<ReminderWorker>().setConstraints(needsNetwork).build()
        workManager.enqueueUniqueWork(CHECK_NOW_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    override fun scheduleEve(reminder: WeatherReminder) {
        val delay = eveNotificationDelay(reminder.targetDate, LocalDateTime.now()) ?: return
        val request = OneTimeWorkRequestBuilder<EveReminderWorker>()
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .setConstraints(needsNetwork)
            .setInputData(workDataOf(EveReminderWorker.KEY_REMINDER_ID to reminder.id))
            .build()
        // REPLACE: tarih değiştirilirse eski plan yenisiyle değişsin.
        workManager.enqueueUniqueWork(eveWorkName(reminder.id), ExistingWorkPolicy.REPLACE, request)
    }

    override fun cancelEve(reminderId: String) {
        workManager.cancelUniqueWork(eveWorkName(reminderId))
    }

    override fun sendEveNow(reminderId: String) {
        val request = OneTimeWorkRequestBuilder<EveReminderWorker>()
            .setConstraints(needsNetwork)
            .setInputData(workDataOf(EveReminderWorker.KEY_REMINDER_ID to reminderId))
            .build()
        // Ayrı bir ad: asıl akşam planını (eveWorkName) silmesin.
        workManager.enqueueUniqueWork("reminder-eve-debug-$reminderId", ExistingWorkPolicy.REPLACE, request)
    }

    private fun eveWorkName(reminderId: String) = "reminder-eve-$reminderId"

    private companion object {
        const val DAILY_WORK_NAME = "reminder-daily-check"
        const val CHECK_NOW_WORK_NAME = "reminder-check-now"
        const val DAILY_INTERVAL_HOURS = 24L
    }
}

/** Akşam özetinin gönderildiği saat (telefonun saatiyle; bildirimi kullanıcı okuyacak). */
val EVE_NOTIFICATION_TIME: LocalTime = LocalTime.of(20, 0)

/**
 * Akşam özetine ne kadar kaldığını hesaplar. Saf fonksiyondur; test edilebilmesi için
 * "şu an" parametre olarak verilir.
 *
 * - Hedef günden önceki akşam 20:00 henüz gelmediyse: o ana kadar geçecek süre.
 * - 20:00 geçti ama hedef gün henüz başlamadıysa (ör. kullanıcı yarın için akşam 21:00'de
 *   ekledi): hemen gönderilir (sıfır).
 * - Hedef gün başladıysa ya da geçtiyse: null, planlanacak bir şey yok.
 */
fun eveNotificationDelay(targetDate: LocalDate, now: LocalDateTime): Duration? {
    val eveAt = targetDate.minusDays(1).atTime(EVE_NOTIFICATION_TIME)
    return when {
        now.isBefore(eveAt) -> Duration.between(now, eveAt)
        now.isBefore(targetDate.atStartOfDay()) -> Duration.ZERO
        else -> null
    }
}
