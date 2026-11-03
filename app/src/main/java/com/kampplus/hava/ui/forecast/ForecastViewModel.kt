package com.kampplus.hava.ui.forecast

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonSyntaxException
import com.kampplus.hava.data.OpenMeteoWeatherRepository
import com.kampplus.hava.data.WeatherRepository
import com.kampplus.hava.model.Forecast
import com.kampplus.hava.ui.LoadState
import java.io.IOException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

/**
 * Tahmin ekranının beyni. [com.kampplus.hava.ui.city.CityViewModel] ile aynı desen.
 *
 * Fark: hangi şehrin tahmini isteneceğini navigasyondan öğrenir.
 * `SavedStateHandle` rota parametrelerini taşır ve süreç
 * öldürülüp geri dönüldüğünde de bu değerler korunur.
 */
class ForecastViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: WeatherRepository
) : ViewModel() {

    /**
     * Compose'un `viewModel()` yardımcısı, tam olarak `(SavedStateHandle)` imzalı bir
     * kurucu arar. Varsayılan parametre kullanmak bu imzayı üretmediği için ikincil
     * kurucuyu elle yazıyoruz.
     */
    constructor(savedStateHandle: SavedStateHandle) : this(savedStateHandle, OpenMeteoWeatherRepository())

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

    init {
        load()
    }

    /** Hata ekranındaki "Tekrar dene" düğmesi buraya bağlanır. */
    fun onRetry() = load()

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

        /** Rota parametrelerinin adları; [com.kampplus.hava.ui.HavaNavHost] ile ortak. */
        const val ARG_NAME = "name"
        const val ARG_SUBTITLE = "subtitle"
        const val ARG_LATITUDE = "latitude"
        const val ARG_LONGITUDE = "longitude"
    }
}
