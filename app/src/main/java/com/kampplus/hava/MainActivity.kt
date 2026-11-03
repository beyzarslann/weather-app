package com.kampplus.hava

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kampplus.hava.ui.ForecastDeepLink
import com.kampplus.hava.ui.HavaNavHost
import com.kampplus.hava.ui.theme.HavaTheme

/**
 * Uygulamanın tek Activity'si; kullanıcının girdiği kapı.
 *
 * Activity bir **pencereyi** yönetir. Pencerenin içine ne çizileceğini ise
 * Compose söyler: sahne Activity'dir, dekoru Compose kurar.
 */
class MainActivity : ComponentActivity() {

    /**
     * Ekran oluşturulurken Android bu fonksiyonu **kendisi** çağırır; biz çağırmayız.
     * Yaşam döngüsünün ilk adımıdır.
     *
     * Logcat'i açıp uygulamayı arka plana alıp geri dönerek `onCreate` / `onStart` /
     * `onResume` sırasını gözlemleyebilirsin.
     */
    /**
     * Bildirimden gelen "şu şehri aç" isteği. Compose state'i olduğu için değişince
     * [HavaNavHost] bunu görür ve tahmin ekranına gider; sonra null'a çekilir.
     */
    private var deepLink by mutableStateOf<ForecastDeepLink?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")

        // Yalnızca ilk açılışta okunur. Ekran döndürülünce Activity yeniden kurulur ama
        // Intent aynı kalır; tekrar okusaydık her döndürmede tahmin ekranına yeniden gidilirdi.
        if (savedInstanceState == null) deepLink = ForecastDeepLink.fromIntent(intent)

        // İçeriği durum ve navigasyon çubuklarının altına kadar yayar.
        enableEdgeToEdge()

        // Süslü parantezin içi Compose dünyasıdır.
        setContent {
            HavaTheme {
                HavaNavHost(deepLink = deepLink, onDeepLinkHandled = { deepLink = null })
            }
        }
    }

    /**
     * Uygulama zaten açıkken bildirime dokunulursa yeni Activity açılmaz (Manifest'te
     * singleTop); Android yeni Intent'i buraya getirir.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        ForecastDeepLink.fromIntent(intent)?.let { deepLink = it }
    }

    override fun onStart() {
        super.onStart()
        Log.d(TAG, "onStart")
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume")
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause")
    }

    override fun onStop() {
        super.onStop()
        Log.d(TAG, "onStop")
    }

    private companion object {
        /** Logcat'te filtrelemek için: arama kutusuna `Lifecycle` yaz. */
        const val TAG = "Lifecycle"
    }
}
