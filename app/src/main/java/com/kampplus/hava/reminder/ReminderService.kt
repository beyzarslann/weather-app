package com.kampplus.hava.reminder

import android.content.Context
import com.kampplus.hava.data.DataStoreReminderRepository
import com.kampplus.hava.data.ReminderRepository
import com.kampplus.hava.model.WeatherReminder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Arayüzün hatırlatıcılarla konuştuğu tek kapı (bir sonraki adımda kullanılacak).
 *
 * Kayıt ile planlamayı bir arada tutar: bir hatırlatıcı eklenince hem saklanır hem de
 * işleri planlanır; silinince işleri de iptal edilir. Ekran bu iki adımı ayrı ayrı
 * hatırlamak zorunda kalmaz, biri unutulup "silinmiş hatırlatıcıdan bildirim" gelmez.
 */
class ReminderService(
    private val repository: ReminderRepository,
    private val scheduler: ReminderScheduler
) {

    /** Uygulamada kullanılan kurucu: DataStore + WorkManager. */
    constructor(context: Context) : this(DataStoreReminderRepository(context), WorkManagerReminderScheduler(context))

    val reminders: Flow<List<WeatherReminder>> = repository.reminders

    /**
     * Hatırlatıcıyı kaydeder ve işlerini planlar.
     *
     * Günlük iş zaten planlıysa dokunulmaz; yine de yeni hatırlatıcının ilk özeti bir
     * gün beklemesin diye tek seferlik bir kontrol de istenir. Böylece kullanıcının
     * eklerken gördüğü tahmin, sonraki değişimlerin karşılaştırma noktası olur.
     */
    suspend fun add(reminder: WeatherReminder) {
        repository.add(reminder)
        scheduler.ensureDailyCheck()
        scheduler.checkSoon()
        scheduler.scheduleEve(reminder)
    }

    /** Hatırlatıcıyı siler; son hatırlatıcıysa günlük kontrol de durur. */
    suspend fun remove(id: String) {
        repository.remove(id)
        scheduler.cancelEve(id)
        if (repository.reminders.first().isEmpty()) scheduler.cancelDailyCheck()
    }
}
