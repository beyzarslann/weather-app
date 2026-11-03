package com.kampplus.hava.ui.forecast

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.gson.JsonSyntaxException
import com.kampplus.hava.data.OpenMeteoWeatherRepository
import com.kampplus.hava.data.WeatherRepository
import com.kampplus.hava.model.Forecast
import com.kampplus.hava.model.WeatherReminder
import com.kampplus.hava.reminder.ReminderService
import com.kampplus.hava.ui.LoadState
import java.io.IOException
import java.time.LocalDate
import java.util.UUID
import kotlin.math.abs
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import retrofit2.HttpException

/**
 * Tahmin ekranının beyni. [com.kampplus.hava.ui.city.CityViewModel] ile aynı desen.
 *
 * Fark: hangi şehrin tahmini isteneceğini navigasyondan öğrenir.
 * `SavedStateHandle` rota parametrelerini taşır ve süreç
 * öldürülüp geri dönüldüğünde de bu değerler korunur.
 *
 * Ekrandan [Factory] ile kurulur: hatırlatıcı servisi Context'e ihtiyaç duyar ve
 * `viewModel()`'in varsayılan kurucu araması bunu veremez. Testlerde kurucu doğrudan,
 * sahte repository ve servisle çağrılır.
 */
class ForecastViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: WeatherRepository,
    private val reminderService: ReminderService
) : ViewModel() {

    /** Başlıkta gösterilecek şehir adı; rotadan gelir. */
    val cityName: String = savedStateHandle.get<String>(ARG_NAME).orEmpty()

    /** Başlığın altındaki "Ankara, Türkiye" satırı. */
    val subtitle: String = savedStateHandle.get<String>(ARG_SUBTITLE).orEmpty()

    private val latitude: Double = savedStateHandle.get<String>(ARG_LATITUDE)?.toDoubleOrNull() ?: 0.0
    private val longitude: Double = savedStateHandle.get<String>(ARG_LONGITUDE)?.toDoubleOrNull() ?: 0.0

    private val _uiState = MutableStateFlow<LoadState<Forecast>>(LoadState.Loading)
    val uiState: StateFlow<LoadState<Forecast>> = _uiState.asStateFlow()

    /**
     * Aşağı çekip yenileme sürüyor mu? Ekranın üstündeki dönen göstergeyi yönetir.
     *
     * [uiState]'ten ayrı tutulur: yenileme sırasında eldeki veri ekranda kalmalı,
     * ekran Loading'e düşüp boşalmamalı.
     */
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    /**
     * Yenileme başarısız olunca gösterilecek kısa mesaj (Snackbar). Gösterilecek bir şey yoksa null.
     *
     * Tek seferlik bir olay gibi görünse de state olarak tutuyoruz: ekran döndürülse bile
     * mesaj kaybolmaz ya da iki kez gösterilmez. Ekran mesajı gösterdikten sonra
     * [onRefreshMessageShown] ile "tamam, gösterdim" der ve mesaj temizlenir.
     */
    private val _refreshMessage = MutableStateFlow<String?>(null)
    val refreshMessage: StateFlow<String?> = _refreshMessage.asStateFlow()

    /** Süren istek (ilk yükleme ya da yenileme). Üst üste çekmelerde ikinci istek atılmaz. */
    private var fetchJob: Job? = null

    /**
     * Bu şehir için kurulu bir hatırlatıcı var mı? Varsa üst bardaki zil dolu çizilir.
     *
     * `stateIn(WhileSubscribed(5_000))`: ekran dinlediği sürece DataStore izlenir; ekran
     * döndürülürken (birkaç yüz ms dinleyicisiz kalır) akış kapanıp yeniden açılmasın diye
     * 5 saniye beklenir.
     */
    val hasReminder: StateFlow<Boolean> = reminderService.reminders
        .map { list -> list.any(::isThisCity) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

    init {
        load()
    }

    /** Hata ekranındaki "Tekrar dene" düğmesi buraya bağlanır. */
    fun onRetry() = load()

    /**
     * Seçilen gün için hatırlatıcı kurar. Kayıt ve arka plan işlerinin planlanması
     * [ReminderService]'in işidir; burada yalnızca ekrandaki şehirden kayıt oluşturulur.
     */
    fun createReminder(date: LocalDate) {
        val reminder = WeatherReminder(
            id = UUID.randomUUID().toString(),
            cityName = cityName,
            subtitle = subtitle,
            latitude = latitude,
            longitude = longitude,
            targetDate = date
        )
        viewModelScope.launch { reminderService.add(reminder) }
    }

    /**
     * Yalnızca debug derlemesinde: hatırlatıcı kontrolünü ve bu şehrin akşam özetlerini
     * hemen çalıştırır; 24 saat ya da akşam 20:00 beklemeden bildirim denenebilir.
     */
    fun runRemindersNowForDebug() {
        viewModelScope.launch {
            val ids = reminderService.reminders.first().filter(::isThisCity).map { it.id }
            reminderService.runNowForDebug(eveReminderIds = ids)
        }
    }

    /**
     * Hatırlatıcı bu ekrandaki şehre mi ait? Koordinatlar karşılaştırılır; ad değil
     * (iki farklı "Ankara" olabilir). Küçük bir tolerans, ondalık yuvarlama farklarını yutar.
     */
    private fun isThisCity(reminder: WeatherReminder) =
        abs(reminder.latitude - latitude) < COORDINATE_TOLERANCE && abs(reminder.longitude - longitude) < COORDINATE_TOLERANCE

    /**
     * Aşağı çekip yenileme.
     *
     * Ekranda veri varsa onu koruyarak arka planda yeni veri istenir; yalnızca üstteki
     * gösterge döner. Başarısız olursa eski veri olduğu gibi kalır ve [refreshMessage]
     * dolar. Ekranda veri yoksa (hata durumu) korunacak bir şey olmadığı için normal
     * yükleme yapılır.
     */
    fun refresh() {
        if (fetchJob?.isActive == true) return
        if (_uiState.value !is LoadState.Success) {
            load()
            return
        }
        fetchJob = viewModelScope.launch {
            _isRefreshing.value = true
            // finally: istek iptal edilse bile gösterge sonsuza kadar dönmesin.
            try {
                when (val result = fetch()) {
                    is LoadState.Success -> _uiState.value = result
                    // Eski tahmin ekranda kalır; kullanıcıya yalnızca kısa bir uyarı gösterilir.
                    is LoadState.Error -> _refreshMessage.value = "Güncellenemedi. ${result.message}"
                    else -> Unit
                }
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    /** Ekran Snackbar'ı gösterdi; aynı mesaj bir daha gösterilmesin. */
    fun onRefreshMessageShown() {
        _refreshMessage.value = null
    }

    private fun load() {
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            _uiState.value = LoadState.Loading
            _uiState.value = fetch()
        }
    }

    /**
     * Tahmini ister ve sonucu Success ya da Error'a çevirir.
     *
     * İlk yükleme ve yenileme aynı hata eşlemesini paylaşsın diye ayrı bir fonksiyon:
     * fark, sonucun ekrana nasıl yansıtıldığındadır, nasıl üretildiğinde değil.
     */
    private suspend fun fetch(): LoadState<Forecast> = try {
        LoadState.Success(repository.getForecast(latitude, longitude))
    } catch (e: IOException) {
        Log.e(TAG, "Tahmin alınamadı: bağlantı hatası", e)
        LoadState.Error("İnternet bağlantını kontrol et.")
    } catch (e: HttpException) {
        Log.e(TAG, "Tahmin alınamadı: HTTP ${e.code()}", e)
        LoadState.Error("Sunucu şu an cevap vermiyor.")
    } catch (e: JsonSyntaxException) {
        Log.e(TAG, "Tahmin alınamadı: yanıt çözümlenemedi", e)
        LoadState.Error("Bir şeyler ters gitti. Tekrar dene.")
    } catch (e: IllegalStateException) {
        // ForecastResponse.toForecast(), "current" bloğu yoksa bunu fırlatır.
        Log.e(TAG, "Tahmin alınamadı: eksik veri", e)
        LoadState.Error("Bir şeyler ters gitti. Tekrar dene.")
    }

    companion object {
        private const val TAG = "ForecastViewModel"
        private const val STOP_TIMEOUT_MS = 5_000L

        /** Yaklaşık 10 metre; aynı şehrin iki kaydını ayırt etmeye yetecek kadar dar. */
        private const val COORDINATE_TOLERANCE = 1e-4

        /**
         * Uygulamanın kullandığı kurulum. `createSavedStateHandle()` rota parametrelerini,
         * `APPLICATION_KEY` ise hatırlatıcı servisinin ihtiyaç duyduğu Context'i verir.
         */
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
                ForecastViewModel(
                    savedStateHandle = createSavedStateHandle(),
                    repository = OpenMeteoWeatherRepository(),
                    reminderService = ReminderService(application)
                )
            }
        }

        /** Rota parametrelerinin adları; [com.kampplus.hava.ui.HavaNavHost] ile ortak. */
        const val ARG_NAME = "name"
        const val ARG_SUBTITLE = "subtitle"
        const val ARG_LATITUDE = "latitude"
        const val ARG_LONGITUDE = "longitude"
    }
}
