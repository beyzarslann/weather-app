package com.kampplus.hava.ui.city

import com.kampplus.hava.reminder.ReminderService
import com.kampplus.hava.testing.FakeCityRepository
import com.kampplus.hava.testing.FakeReminderRepository
import com.kampplus.hava.testing.FakeReminderScheduler
import com.kampplus.hava.testing.MainDispatcherRule
import com.kampplus.hava.testing.sampleCity
import com.kampplus.hava.testing.sampleReminder
import com.kampplus.hava.ui.LoadState
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

/**
 * Şehir listesi ekranının durumlarını doğrular.
 *
 * Temel kural: ekran boş kutu ve Idle ile açılır. Her istek Loading ile başlar
 * ve Success / Empty / Error'dan biriyle biter; kutu temizlenince Idle'a dönülür.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CityViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeCityRepository()
    private val reminderRepository = FakeReminderRepository()
    private val scheduler = FakeReminderScheduler()

    /** Testlerde "bugün" sabit: 2 Kasım 2026. */
    private val today = LocalDate.of(2026, 11, 2)

    private fun createViewModel() = CityViewModel(
        repository = repository,
        reminderService = ReminderService(reminderRepository, scheduler),
        today = { today }
    )

    /** Kullanıcının bir şehir yazıp Enter'a basmasını taklit eder. */
    private fun TestScope.searchFor(viewModel: CityViewModel, query: String = "Ankara") {
        viewModel.onQueryChange(query)
        viewModel.search()
        advanceUntilIdle()
    }

    @Test
    fun `baslangicta kutu bos ve Idle gosterilir`() = runTest {
        val viewModel = createViewModel()

        assertEquals("", viewModel.query.value)
        assertEquals(LoadState.Idle, viewModel.uiState.value)
    }

    @Test
    fun `acilista otomatik arama yapilmaz`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Varsayılan şehir kaldırıldı: kullanıcı bir şey istemeden servise gidilmez.
        assertEquals(0, repository.callCount)
        assertEquals(LoadState.Idle, viewModel.uiState.value)
    }

    @Test
    fun `veri gelince Success durumuna gecilir`() = runTest {
        repository.result = listOf(sampleCity(), sampleCity(id = 2L, name = "İzmir"))
        val viewModel = createViewModel()

        searchFor(viewModel)

        val state = viewModel.uiState.value
        assertTrue("Success bekleniyordu, gelen: $state", state is LoadState.Success)
        assertEquals(2, (state as LoadState.Success).data.size)
    }

    @Test
    fun `bos liste Empty durumuna cevrilir`() = runTest {
        // Cevap geldi ama içi boş: bu bir hata değildir.
        repository.result = emptyList()
        val viewModel = createViewModel()

        searchFor(viewModel, "xyz")

        assertEquals(LoadState.Empty, viewModel.uiState.value)
    }

    @Test
    fun `baglanti hatasi anlasilir mesaja cevrilir`() = runTest {
        repository.error = IOException("no network")
        val viewModel = createViewModel()

        searchFor(viewModel)

        val state = viewModel.uiState.value
        assertTrue("Error bekleniyordu, gelen: $state", state is LoadState.Error)
        // Kullanıcıya teknik mesaj değil, insan dilinde bir cümle gösterilir.
        assertEquals("İnternet bağlantını kontrol et.", (state as LoadState.Error).message)
    }

    @Test
    fun `sunucu hatasi farkli mesaj gosterir`() = runTest {
        repository.error = HttpException(
            Response.error<Unit>(500, "".toResponseBody("application/json".toMediaType()))
        )
        val viewModel = createViewModel()

        searchFor(viewModel)

        assertEquals(
            "Sunucu şu an cevap vermiyor.",
            (viewModel.uiState.value as LoadState.Error).message
        )
    }

    @Test
    fun `yazmayi birakinca 400 ms sonra otomatik arar`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onQueryChange("Rize")
        advanceTimeBy(300)
        runCurrent()
        // Süre dolmadı, istek yok.
        assertEquals(0, repository.callCount)

        advanceUntilIdle()
        assertEquals(1, repository.callCount)
        assertEquals("Rize", repository.lastQuery)
    }

    @Test
    fun `hizli yazilan harfler tek istege doner`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        listOf("R", "Ri", "Riz", "Rize").forEach {
            viewModel.onQueryChange(it)
            advanceTimeBy(100)
        }
        advanceUntilIdle()

        assertEquals(1, repository.callCount)
        assertEquals("Rize", repository.lastQuery)
    }

    @Test
    fun `tek harfle otomatik arama yapilmaz`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onQueryChange("R")
        advanceUntilIdle()

        assertEquals(0, repository.callCount)
        // Tek harf yazılınca da karşılama görünümü kalır, "bulunamadı" çıkmaz.
        assertEquals(LoadState.Idle, viewModel.uiState.value)
    }

    @Test
    fun `enter sonrasi debounce ayni kelimeyi tekrar aramaz`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onQueryChange("Rize")
        viewModel.search() // Kullanıcı Enter'a bastı.
        advanceUntilIdle()

        assertEquals(1, repository.callCount)
    }

    @Test
    fun `search guncel metinle istek atar`() = runTest {
        val viewModel = createViewModel()

        searchFor(viewModel, "Rize")

        assertEquals("Rize", repository.lastQuery)
    }

    @Test
    fun `bos kutuyla search istek atmaz`() = runTest {
        val viewModel = createViewModel()

        viewModel.search()
        advanceUntilIdle()

        assertEquals(0, repository.callCount)
        assertEquals(LoadState.Idle, viewModel.uiState.value)
    }

    @Test
    fun `kutu temizlenince Idle durumuna donulur`() = runTest {
        repository.result = listOf(sampleCity())
        val viewModel = createViewModel()
        searchFor(viewModel)
        assertTrue(viewModel.uiState.value is LoadState.Success)

        viewModel.onQueryChange("")

        // Debounce beklenmeden, anında karşılama görünümüne dönülür.
        assertEquals(LoadState.Idle, viewModel.uiState.value)
    }

    @Test
    fun `kutu temizlenince suren arama iptal edilir`() = runTest {
        repository.result = listOf(sampleCity())
        repository.delayMs = 1_000
        val viewModel = createViewModel()

        viewModel.onQueryChange("Ankara")
        viewModel.search()
        runCurrent()
        assertEquals(LoadState.Loading, viewModel.uiState.value)

        viewModel.onQueryChange("")
        advanceUntilIdle()

        // Geç gelen cevap karşılama görünümünün üstüne yazılmamalı.
        assertEquals(LoadState.Idle, viewModel.uiState.value)
    }

    @Test
    fun `temizledikten sonra ayni sehir yeniden aranabilir`() = runTest {
        val viewModel = createViewModel()
        searchFor(viewModel, "Rize")

        viewModel.onQueryChange("")
        searchFor(viewModel, "Rize")

        assertEquals(2, repository.callCount)
    }

    @Test
    fun `cipe dokununca kutu dolar ve hemen aranir`() = runTest {
        repository.result = listOf(sampleCity())
        val viewModel = createViewModel()

        viewModel.onSuggestionClick("Trabzon")
        runCurrent()

        // Debounce süresi beklenmeden istek atılmış olmalı.
        assertEquals("Trabzon", viewModel.query.value)
        assertEquals(1, repository.callCount)
        assertEquals("Trabzon", repository.lastQuery)

        // Debounce sonradan aynı kelimeyi ikinci kez aramamalı.
        advanceUntilIdle()
        assertEquals(1, repository.callCount)
    }

    @Test
    fun `tekrar dene yeni bir istek baslatir ve basarili olabilir`() = runTest {
        repository.error = IOException("no network")
        val viewModel = createViewModel()
        searchFor(viewModel)
        assertTrue(viewModel.uiState.value is LoadState.Error)

        // Bağlantı geri geldi.
        repository.error = null
        repository.result = listOf(sampleCity())
        viewModel.search()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is LoadState.Success)
        assertEquals(2, repository.callCount)
    }

    // --- Hatırlatıcılarım ----------------------------------------------------------------

    @Test
    fun `hatirlaticilar listelenir ve gunu gecmis olanlar gizlenir`() = runTest {
        reminderRepository.add(sampleReminder(id = "past", targetDate = today.minusDays(1)))
        reminderRepository.add(sampleReminder(id = "today", targetDate = today))
        reminderRepository.add(sampleReminder(id = "future", targetDate = today.plusDays(5)))
        val viewModel = createViewModel()

        backgroundScope.launch { viewModel.reminders.collect {} }
        advanceUntilIdle()

        assertEquals(listOf("today", "future"), viewModel.reminders.value.map { it.id })
    }

    @Test
    fun `hatirlatici silinince isleri de iptal edilir`() = runTest {
        reminderRepository.add(sampleReminder(id = "a"))
        val viewModel = createViewModel()

        viewModel.removeReminder("a")
        advanceUntilIdle()

        assertTrue(reminderRepository.items.isEmpty())
        // Son hatırlatıcıydı: akşam planı ve günlük kontrol birlikte durur.
        assertEquals(listOf("cancel-eve:a", "cancel-daily"), scheduler.calls)
    }
}
