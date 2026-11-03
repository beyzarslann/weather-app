package com.kampplus.hava.ui.forecast

import androidx.lifecycle.SavedStateHandle
import com.kampplus.hava.reminder.ReminderService
import com.kampplus.hava.testing.FakeReminderRepository
import com.kampplus.hava.testing.FakeReminderScheduler
import com.kampplus.hava.testing.FakeWeatherRepository
import com.kampplus.hava.testing.MainDispatcherRule
import com.kampplus.hava.testing.sampleForecast
import com.kampplus.hava.testing.sampleReminder
import com.kampplus.hava.ui.LoadState
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Tahmin ekranının durumlarını ve rota parametrelerini okuyuşunu doğrular.
 *
 * `SavedStateHandle` doğrudan oluşturulabildiği için navigasyonu ayağa kaldırmadan
 * test edebiliyoruz.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ForecastViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeWeatherRepository()
    private val reminderRepository = FakeReminderRepository()
    private val scheduler = FakeReminderScheduler()

    private fun createViewModel(
        name: String = "Ankara",
        subtitle: String = "Ankara, Türkiye",
        latitude: String = "39.91987",
        longitude: String = "32.85427"
    ) = ForecastViewModel(
        savedStateHandle = SavedStateHandle(
            mapOf(
                ForecastViewModel.ARG_NAME to name,
                ForecastViewModel.ARG_SUBTITLE to subtitle,
                ForecastViewModel.ARG_LATITUDE to latitude,
                ForecastViewModel.ARG_LONGITUDE to longitude
            )
        ),
        repository = repository,
        reminderService = ReminderService(reminderRepository, scheduler)
    )

    /** Hazır tahminin yalnızca anlık sıcaklığı değiştirilmiş kopyası. */
    private fun forecastWithTemperature(celsius: Double) = sampleForecast().let {
        it.copy(current = it.current.copy(temperatureC = celsius))
    }

    /** Ekrandaki anlık sıcaklık; durum Success değilse test burada patlar (bu da istenen). */
    private fun currentTemperature(viewModel: ForecastViewModel) = (viewModel.uiState.value as LoadState.Success).data.current.temperatureC

    @Test
    fun `rota parametreleri okunur`() {
        val viewModel = createViewModel()

        assertEquals("Ankara", viewModel.cityName)
        assertEquals("Ankara, Türkiye", viewModel.subtitle)
    }

    @Test
    fun `baslangicta Loading gosterilir`() = runTest {
        val viewModel = createViewModel()

        assertEquals(LoadState.Loading, viewModel.uiState.value)
    }

    @Test
    fun `tahmin gelince Success durumuna gecilir`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("Success bekleniyordu, gelen: $state", state is LoadState.Success)
        assertEquals(21.0, (state as LoadState.Success).data.current.temperatureC, 0.01)
    }

    @Test
    fun `baglanti hatasi Error durumuna cevrilir`() = runTest {
        repository.error = IOException("no network")

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(
            "İnternet bağlantını kontrol et.",
            (viewModel.uiState.value as LoadState.Error).message
        )
    }

    @Test
    fun `eksik current blogu Error durumuna cevrilir`() = runTest {
        // ForecastResponse.toForecast() bozuk yanıtta IllegalStateException fırlatır.
        repository.error = IllegalStateException("Forecast response has no current block")

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is LoadState.Error)
    }

    @Test
    fun `tekrar dene yeni istek baslatir`() = runTest {
        repository.error = IOException("no network")
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is LoadState.Error)

        repository.error = null
        viewModel.onRetry()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is LoadState.Success)
        assertEquals(2, repository.callCount)
    }

    @Test
    fun `yenileme sirasinda eski veri ekranda kalir ve sonra guncellenir`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Servis bu kez farklı bir sıcaklık dönecek ve biraz bekletecek.
        repository.result = forecastWithTemperature(25.0)
        repository.delayMs = 1_000
        viewModel.refresh()
        runCurrent()

        // Yenileme sürüyor: gösterge dönüyor ama ekran Loading'e düşmedi, eski veri duruyor.
        assertTrue(viewModel.isRefreshing.value)
        assertEquals(21.0, currentTemperature(viewModel), 0.01)

        advanceUntilIdle()

        assertFalse(viewModel.isRefreshing.value)
        assertEquals(25.0, currentTemperature(viewModel), 0.01)
        assertNull(viewModel.refreshMessage.value)
    }

    @Test
    fun `yenileme basarisiz olursa eski veri korunur ve mesaj gosterilir`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        repository.error = IOException("no network")
        viewModel.refresh()
        advanceUntilIdle()

        // Hata ekranı yok; eski tahmin yerinde.
        assertEquals(21.0, currentTemperature(viewModel), 0.01)
        assertFalse(viewModel.isRefreshing.value)
        assertEquals("Güncellenemedi. İnternet bağlantını kontrol et.", viewModel.refreshMessage.value)
    }

    @Test
    fun `gosterilen mesaj temizlenir`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        repository.error = IOException("no network")
        viewModel.refresh()
        advanceUntilIdle()

        viewModel.onRefreshMessageShown()

        // Ekran döndürülse bile aynı Snackbar ikinci kez çıkmaz.
        assertNull(viewModel.refreshMessage.value)
    }

    @Test
    fun `yenileme surerken tekrar cekmek yeni istek atmaz`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        repository.delayMs = 1_000
        viewModel.refresh()
        runCurrent()
        viewModel.refresh()
        advanceUntilIdle()

        // İlk yükleme + tek yenileme.
        assertEquals(2, repository.callCount)
    }

    @Test
    fun `hata ekranindayken yenileme normal yukleme yapar`() = runTest {
        repository.error = IOException("no network")
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Korunacak veri yok: Loading → Success akışı izlenir, Snackbar çıkmaz.
        repository.error = null
        viewModel.refresh()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is LoadState.Success)
        assertNull(viewModel.refreshMessage.value)
    }

    @Test
    fun `gecersiz koordinat uygulamayi cokertmez`() = runTest {
        val viewModel = createViewModel(latitude = "abc", longitude = "")
        advanceUntilIdle()

        // toDoubleOrNull() null dönünce 0.0 kullanılır; istek yine de yapılır.
        assertTrue(viewModel.uiState.value is LoadState.Success)
    }

    // --- Hatırlatıcı ---------------------------------------------------------------------

    @Test
    fun `hatirlatici ekrandaki sehir ve secilen gunle kaydedilir`() = runTest {
        val viewModel = createViewModel()

        viewModel.createReminder(LocalDate.of(2026, 11, 14))
        advanceUntilIdle()

        val saved = reminderRepository.items.single()
        assertEquals("Ankara", saved.cityName)
        assertEquals("Ankara, Türkiye", saved.subtitle)
        assertEquals(39.91987, saved.latitude, 1e-9)
        assertEquals(32.85427, saved.longitude, 1e-9)
        assertEquals(LocalDate.of(2026, 11, 14), saved.targetDate)
        // İlk özet arka plan işinin ilk kontrolünde alınır; kurulurken boştur.
        assertNull(saved.lastSnapshot)
        assertEquals(listOf("daily", "check-soon", "eve:${saved.id}"), scheduler.calls)
    }

    @Test
    fun `bu sehir icin hatirlatici varsa zil dolu`() = runTest {
        reminderRepository.add(sampleReminder().copy(latitude = 39.91987, longitude = 32.85427))
        val viewModel = createViewModel()

        // stateIn(WhileSubscribed): değer ancak biri dinlerken akar; ekranın yerine biz dinliyoruz.
        backgroundScope.launch { viewModel.hasReminder.collect {} }
        advanceUntilIdle()

        assertTrue(viewModel.hasReminder.value)
    }

    @Test
    fun `baska sehrin hatirlaticisi zili doldurmaz`() = runTest {
        reminderRepository.add(sampleReminder().copy(cityName = "Trabzon", latitude = 41.0, longitude = 39.72))
        val viewModel = createViewModel()

        backgroundScope.launch { viewModel.hasReminder.collect {} }
        advanceUntilIdle()

        assertFalse(viewModel.hasReminder.value)
    }
}
