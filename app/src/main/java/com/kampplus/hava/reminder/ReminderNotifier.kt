package com.kampplus.hava.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.kampplus.hava.R
import com.kampplus.hava.model.WeatherReminder
import com.kampplus.hava.ui.ForecastDeepLink

/**
 * Hatırlatıcı bildirimlerini gönderen taraf.
 *
 * Arayüz olarak tanımlı ki [ReminderChecker] testlerinde sahte bir gönderici verilip
 * "bildirim gönderildi mi, hangi metinle?" sorusu doğrulanabilsin.
 */
interface ReminderNotifier {
    /** Tahmin belirgin değişti. */
    fun notifyChange(reminder: WeatherReminder, message: String)

    /** Hedef günden önceki akşamın son özeti. */
    fun notifyEve(reminder: WeatherReminder, message: String)
}

/**
 * Gerçek uygulama: Android bildirimi gösterir.
 *
 * Android 13'ten (API 33) itibaren bildirimler çalışma anında izin ister
 * (POST_NOTIFICATIONS). İzin verilmemişse hiçbir şey göstermeyiz; çökmeyiz.
 * İzni istemek arayüzün işidir (bir sonraki adım).
 */
class AndroidReminderNotifier(context: Context) : ReminderNotifier {

    private val context = context.applicationContext

    override fun notifyChange(reminder: WeatherReminder, message: String) = show(
        reminder = reminder,
        notificationId = notificationId(reminder, KIND_CHANGE),
        title = context.getString(R.string.reminder_change_title, reminder.cityName),
        message = message
    )

    override fun notifyEve(reminder: WeatherReminder, message: String) = show(
        reminder = reminder,
        notificationId = notificationId(reminder, KIND_EVE),
        title = context.getString(R.string.reminder_eve_title, reminder.cityName),
        message = message
    )

    private fun show(reminder: WeatherReminder, notificationId: Int, title: String, message: String) {
        if (!canNotify()) {
            Log.i(TAG, "Bildirim izni yok; hatırlatıcı ${reminder.id} sessizce atlandı")
            return
        }
        ensureChannel()

        // Bildirime dokununca uygulama o şehrin tahmin ekranında açılır.
        // requestCode olarak bildirim kimliğini veriyoruz: aynı kodu paylaşan PendingIntent'ler
        // birbirinin extras'ını ezer ve iki şehrin bildirimi aynı ekranı açardı.
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            ForecastDeepLink.fromReminder(reminder).toIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_weather)
            .setContentTitle(title)
            .setContentText(message)
            // Uzun metin (kıyafet önerisi) tek satıra sığmaz; genişletilince tamamı görünsün.
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (e: SecurityException) {
            // canNotify() ile notify() arasında kullanıcı izni geri alabilir.
            Log.w(TAG, "Bildirim gönderilemedi: izin geri alınmış", e)
        }
    }

    /** İzin (API 33+) ve kullanıcının bildirim ayarı birlikte kontrol edilir. */
    private fun canNotify(): Boolean {
        val permissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return permissionGranted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /**
     * "Hava hatırlatıcıları" kanalı. Android 8'den (minSdk 26) itibaren her bildirim bir kanala ait olmalı.
     * Kanal oluşturmak idempotenttir: zaten varsa hiçbir şey olmaz, kullanıcının ayarları korunur.
     */
    private fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.reminder_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = context.getString(R.string.reminder_channel_description) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /**
     * Her hatırlatıcının iki bildirimi (değişim ve akşam özeti) ayrı kimlik alır;
     * akşam özeti, aynı gün gelmiş bir değişim bildirimini silmesin.
     */
    private fun notificationId(reminder: WeatherReminder, kind: Int) = reminder.id.hashCode() * 2 + kind

    private companion object {
        const val TAG = "ReminderNotifier"
        const val CHANNEL_ID = "weather_reminders"
        const val KIND_CHANGE = 0
        const val KIND_EVE = 1
    }
}
