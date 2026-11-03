package com.kampplus.hava.ui

import android.content.Context
import android.content.Intent
import com.kampplus.hava.MainActivity
import com.kampplus.hava.model.WeatherReminder

/**
 * Uygulamayı doğrudan bir şehrin tahmin ekranında açma isteği.
 *
 * Bildirime dokununca [MainActivity] bu bilgiyle açılır; [HavaNavHost] da şehir listesinin
 * üstüne tahmin ekranını koyar. Geri tuşu kullanıcıyı şehir listesine götürür.
 *
 * Neden intent extras? Bildirim uygulama kapalıyken de gelir; açılırken elimizde yalnızca
 * Intent vardır. Şehri yeniden aramak yerine gereken dört değeri doğrudan taşıyoruz.
 */
data class ForecastDeepLink(
    val name: String,
    val subtitle: String,
    val latitude: Double,
    val longitude: Double
) {

    /**
     * Uygulamayı bu şehirde açacak Intent.
     *
     * - NEW_TASK: bildirim bir Activity dışından (sistemden) başlatılır.
     * - CLEAR_TOP + singleTop (Manifest): uygulama zaten açıksa yeni bir kopya açılmaz;
     *   mevcut Activity'nin onNewIntent'i çağrılır.
     */
    fun toIntent(context: Context): Intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra(EXTRA_NAME, name)
        putExtra(EXTRA_SUBTITLE, subtitle)
        putExtra(EXTRA_LATITUDE, latitude)
        putExtra(EXTRA_LONGITUDE, longitude)
    }

    companion object {
        private const val EXTRA_NAME = "com.kampplus.hava.extra.CITY_NAME"
        private const val EXTRA_SUBTITLE = "com.kampplus.hava.extra.CITY_SUBTITLE"
        private const val EXTRA_LATITUDE = "com.kampplus.hava.extra.LATITUDE"
        private const val EXTRA_LONGITUDE = "com.kampplus.hava.extra.LONGITUDE"

        fun fromReminder(reminder: WeatherReminder) = ForecastDeepLink(
            name = reminder.cityName,
            subtitle = reminder.subtitle,
            latitude = reminder.latitude,
            longitude = reminder.longitude
        )

        /**
         * Intent bir tahmin isteği taşıyorsa onu, yoksa null döner.
         * Ana ekran simgesinden normal açılışta extras yoktur; o zaman null.
         */
        fun fromIntent(intent: Intent?): ForecastDeepLink? {
            val extras = intent?.extras ?: return null
            val name = extras.getString(EXTRA_NAME) ?: return null
            if (!extras.containsKey(EXTRA_LATITUDE) || !extras.containsKey(EXTRA_LONGITUDE)) return null
            return ForecastDeepLink(
                name = name,
                subtitle = extras.getString(EXTRA_SUBTITLE).orEmpty(),
                latitude = extras.getDouble(EXTRA_LATITUDE),
                longitude = extras.getDouble(EXTRA_LONGITUDE)
            )
        }
    }
}
