package com.kampplus.hava.reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kampplus.hava.data.DataStoreReminderRepository
import com.kampplus.hava.data.OpenMeteoWeatherRepository

/*
 * WorkManager'ın çalıştırdığı iki iş.
 *
 * İkisi de bilerek incedir: gerçek bağımlılıkları kurar, işi [ReminderChecker]'a devreder
 * ve sonucu WorkManager'ın diline (success / retry) çevirir. Kararların testleri
 * ReminderCheckerTest'tedir.
 *
 * WorkManager işleri (Context, WorkerParameters) imzalı kurucuyla kendisi oluşturur;
 * bu yüzden bağımlılıklar kurucu parametresi olarak değil, doWork içinde kurulur.
 */

/** Ortak kurulum: gerçek repository'ler ve bildirim göndericiyle bir [ReminderChecker]. */
private fun Context.reminderChecker() = ReminderChecker(
    reminders = DataStoreReminderRepository(this),
    weather = OpenMeteoWeatherRepository(),
    notifier = AndroidReminderNotifier(this)
)

/**
 * Bir işin kaç kez yeniden deneneceği. Sonrasında bugünlük vazgeçilir, ertesi günkü
 * çalışma bekler. Sınırsız denemek, ağı olmayan bir telefonda pili boşuna tüketirdi.
 */
private const val MAX_ATTEMPTS = 3

/**
 * Günlük kontrol: her hatırlatıcı için 16 günlük tahmini çeker, değişim varsa bildirir.
 * Hatırlatıcı kalmadıysa kendi günlük planını iptal eder.
 */
class ReminderWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val result = applicationContext.reminderChecker().checkAll()
        if (result.remaining == 0) WorkManagerReminderScheduler(applicationContext).cancelDailyCheck()
        return when {
            result.outcome == ReminderChecker.Outcome.SUCCESS -> Result.success()
            runAttemptCount + 1 >= MAX_ATTEMPTS -> Result.success()
            else -> Result.retry()
        }
    }
}

/**
 * Hedef günden önceki akşamın son özeti. Hangi hatırlatıcı için çalıştığını
 * [KEY_REMINDER_ID] ile girdi verisinden okur.
 */
class EveReminderWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_REMINDER_ID) ?: return Result.failure()
        val outcome = applicationContext.reminderChecker().sendEveSummary(id)
        return when {
            outcome == ReminderChecker.Outcome.SUCCESS -> Result.success()
            runAttemptCount + 1 >= MAX_ATTEMPTS -> Result.success()
            else -> Result.retry()
        }
    }

    companion object {
        const val KEY_REMINDER_ID = "reminder_id"
    }
}
