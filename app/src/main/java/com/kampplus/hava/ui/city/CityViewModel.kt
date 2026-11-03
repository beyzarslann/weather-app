package com.kampplus.hava.ui.city

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.gson.JsonSyntaxException
import com.kampplus.hava.data.CityRepository
import com.kampplus.hava.data.OpenMeteoCityRepository
import com.kampplus.hava.model.City
import com.kampplus.hava.model.WeatherReminder
import com.kampplus.hava.reminder.ReminderService
import com.kampplus.hava.ui.LoadState
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import retrofit2.HttpException

/**
 * Şehir listesi ekranının beyni.
 *
 * Görev dağılımı:
 * - Ekran (Composable) yalnızca çizer.
 * - ViewModel state'i tutar ve kararları verir.
 * - Repository veriyi getirir.
 *
 * ViewModel ekran döndüğünde (Activity yeniden kurulurken) yaşamaya devam eder;
 * bu yüzden arama metni ve sonuçlar kaybolmaz.
 *
 * Ekran boş bir arama kutusuyla açılır ve [LoadState.Idle] durumunda bekler;
 * kullanıcı yazana ya da popüler şehirlerden birini seçene kadar istek atılmaz.
 * Bu sırada karşılama görünümünde kurulu hatırlatıcılar listelenir.
 *
 * Ekrandan [Factory] ile kurulur (hatırlatıcı servisi Context ister). Testlerde kurucu
 * doğrudan, sahte repository ve servisle çağrılır.
 *
 * @param repository Şehir araması. Testlerde sahte bir repository verilir.
 * @param reminderService Kurulu hatırlatıcılar ve silme.
 * @param today Telefonun bugünü; geçmiş hatırlatıcıları listede göstermemek için.
 */
class CityViewModel(
    private val repository: CityRepository,
    private val reminderService: ReminderService,
    private val today: () -> LocalDate = { LocalDate.now() }
) : ViewModel() {

    // State ViewModel'de MutableStateFlow ile tutulur.
    // Değiştirilebilir alan private'tır; dışarıya yalnızca okunabilir hâli açılır.
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _uiState = MutableStateFlow<LoadState<List<City>>>(LoadState.Idle)
    val uiState: StateFlow<LoadState<List<City>>> = _uiState.asStateFlow()

    /** Süren arama; yenisi başlarken iptal edilir. */
    private var searchJob: Job? = null

    /** En son aranan metin; aynı kelimeyi iki kez aramamak için. */
    private var lastSearchedQuery: String? = null

    /**
     * Karşılama görünümündeki "Hatırlatıcılarım" listesi.
     *
     * Günü geçmiş kayıtlar gösterilmez. Onları asıl silen arka plan işidir; ama iş en
     * fazla günde bir çalıştığı için kullanıcı geçmiş bir tarihi bir gün daha görmesin.
     */
    val reminders: StateFlow<List<WeatherReminder>> = reminderService.reminders
        .map { list -> list.filterNot { it.targetDate.isBefore(today()) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    init {
        observeQuery()
    }

    /** "Hatırlatıcılarım" satırındaki sil ikonu. İşlerin iptali servisin içindedir. */
    fun removeReminder(id: String) {
        viewModelScope.launch { reminderService.remove(id) }
    }

    /**
     * Canlı arama: kullanıcı yazmayı bıraktıktan [DEBOUNCE_MS] sonra aramayı tetikler.
     *
     * `debounce`: her yeni harf sayacı sıfırlar. "Ankara" hızlıca yazılırsa
     * 6 istek değil, yalnızca 1 istek atılır.
     *
     * Açılıştaki boş metin [MIN_QUERY_LENGTH] filtresine takılır; bu yüzden
     * ekran açılır açılmaz istek atılmaz.
     */
    @OptIn(FlowPreview::class)
    private fun observeQuery() {
        viewModelScope.launch {
            _query
                .map { it.trim() }
                .debounce(DEBOUNCE_MS)
                .filter { it.length >= MIN_QUERY_LENGTH }
                .collect { query ->
                    // Enter ile ya da çipe dokunarak az önce aranan kelime tekrar aranmaz.
                    if (query != lastSearchedQuery) search()
                }
        }
    }

    /**
     * Kullanıcı arama kutusuna her harf yazdığında çağrılır.
     *
     * Kutu boşalınca beklemeden karşılama görünümüne dönülür: debounce'u
     * beklemek, temizle düğmesine basıldıktan sonra eski sonuçların bir an
     * daha görünmesine yol açardı.
     */
    fun onQueryChange(newQuery: String) {
        _query.value = newQuery
        if (newQuery.isBlank()) resetToIdle()
    }

    /**
     * Popüler şehir çipine dokunulduğunda çağrılır: kutuyu doldurur ve
     * debounce beklemeden hemen arar.
     */
    fun onSuggestionClick(cityName: String) {
        _query.value = cityName
        search()
    }

    /**
     * Aramayı çalıştırır ve sonucu dört durumdan birine çevirir.
     *
     * `viewModelScope.launch`: yeni bir coroutine başlatır. Ekran kapanırsa bu kapsam
     * işi otomatik iptal eder, yarıda kalan istek sızıntı yapmaz.
     */
    fun search() {
        // Boş kutuyla Enter'a basılırsa servise gitmeye gerek yok.
        if (_query.value.isBlank()) {
            resetToIdle()
            return
        }
        // Önceki istek hâlâ sürüyorsa iptal: geç gelen eski cevap
        // yeni sonucun üstüne yazmasın.
        searchJob?.cancel()
        lastSearchedQuery = _query.value.trim()
        searchJob = viewModelScope.launch {
            _uiState.value = LoadState.Loading
            _uiState.value = try {
                val cities = repository.search(_query.value)
                // Cevap geldi ama içi boş: bu bir hata değil, "Empty" durumudur.
                if (cities.isEmpty()) LoadState.Empty else LoadState.Success(cities)
            } catch (e: IOException) {
                // İnternet yok, DNS çözülemedi veya zaman aşımı.
                Log.e(TAG, "Arama başarısız: bağlantı hatası", e)
                LoadState.Error("İnternet bağlantını kontrol et.")
            } catch (e: HttpException) {
                // Sunucu yanıt verdi ama 4xx/5xx döndü.
                Log.e(TAG, "Arama başarısız: HTTP ${e.code()}", e)
                LoadState.Error("Sunucu şu an cevap vermiyor.")
            } catch (e: JsonSyntaxException) {
                // Beklenmeyen JSON: model ile yanıt uyuşmuyor.
                Log.e(TAG, "Arama başarısız: yanıt çözümlenemedi", e)
                LoadState.Error("Bir şeyler ters gitti. Tekrar dene.")
            }
            // Kullanıcıya sade mesaj, Logcat'e teknik ayrıntı.
        }
    }

    /**
     * Süren aramayı iptal eder ve karşılama görünümüne döner.
     *
     * [lastSearchedQuery] da unutulur; kullanıcı aynı şehri yeniden yazarsa
     * arama "zaten arandı" diye atlanmasın.
     */
    private fun resetToIdle() {
        searchJob?.cancel()
        searchJob = null
        lastSearchedQuery = null
        _uiState.value = LoadState.Idle
    }

    companion object {
        private const val TAG = "CityViewModel"
        private const val STOP_TIMEOUT_MS = 5_000L

        /** Uygulamanın kullandığı kurulum: gerçek arama servisi + Context'li hatırlatıcı servisi. */
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
                CityViewModel(repository = OpenMeteoCityRepository(), reminderService = ReminderService(application))
            }
        }

        /** Son harften sonra aramadan önce beklenen süre. */
        private const val DEBOUNCE_MS = 400L

        /** Bundan kısa metinlerle otomatik arama yapılmaz. */
        private const val MIN_QUERY_LENGTH = 2
    }
}
