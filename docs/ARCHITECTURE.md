# Mimari

Tek `:app` modülü, üç katman: `model/` · `data/` · `ui/`.
Arka plan işleri (`reminder/`) aynı katmanları kullanan ikinci bir giriş noktasıdır.

```
UI (Composable)  →  ViewModel (StateFlow)  →  Repository (suspend)  →  Retrofit → Open-Meteo
                                                                    →  DataStore (hatırlatıcılar)
WorkManager      →  ReminderChecker        →  Repository            →  Bildirim
```

Kurallar:

- **UI internete gitmez.** Composable yalnızca aldığı state'i çizer ve olayları yukarı bildirir.
- **ViewModel çizim yapmaz.** Renk veya Composable bilmez; yalnızca state üretir.
- **Repository ekranı bilmez.** Veriyi getirir; nasıl gösterileceği onu ilgilendirmez.
- **Kurallar `model/` içindedir ve saf Kotlin'dir.** Kıyafet önerisi (`OutfitAdvisor`), tahmin değişimi
  (`ForecastChangeDetector`) ve "şu an / bugün" seçimi Android'e bağlı değildir; JVM testinde doğrulanır.

## Neden bu kadar sade?

Proje bilerek küçük tutuldu. Şu yapılar **yoktur**: bağımlılık enjeksiyonu (Hilt),
UseCase katmanı, `Result` sarmalayıcısı + hata eşleyici zinciri, `DataSource` ara katmanı,
domain/DTO/UI üçlü model ayrımı, favoriler ve Room.

Bunların hepsi gerçek projelerde yerinde tercihlerdir; ama üç katmanı anlamadan
eklendiklerinde kodu takip etmeyi zorlaştırırlar. Önce akışı öğren, katmanları
ihtiyaç doğdukça ekle.

Hatırlatıcılar az sayıda ve hep birlikte okunduğu için Room yerine DataStore Preferences'ta
tek bir JSON metni (Gson) olarak saklanır.

`LoadState<out T>` jenerik tutuldu: iki ekran aynı durumları paylaştığı için tip bir kez yazıldı.

## Veri akışı

```
Retrofit → Repository (suspend, withContext(IO)) → ViewModel (try/catch → LoadState)
         → Route (collectAsStateWithLifecycle) → Screen (stateless) → Composable
```

1. Kullanıcı arama kutusuna yazar → `onQueryChange` → `_query.value` güncellenir.
2. Yazmayı bırakınca 400 ms sonra (`debounce`, en az 2 harf) ya da Enter'a basınca `search()` çalışır;
   süren eski istek iptal edilir. Kutu temizlenirse arama iptal edilir ve ekran `Idle`'a döner.
3. `_uiState.value = LoadState.Loading` → ekranda Shimmer görünür.
4. `repository.search(query)` askıya alınır; ana thread serbest kalır.
5. Cevap gelir → liste boşsa `Empty`, doluysa `Success`; hata olursa `catch` → `Error`.
6. `collectAsStateWithLifecycle` yeni değeri okur, Compose ekranı yeniden çizer.

Tahmin ekranında aşağı çekip yenileme `uiState`'i `Loading`'e düşürmez: ayrı bir `isRefreshing`
akışı göstergeyi yönetir, eldeki veri ekranda kalır.

## Hata yönetimi

Hata **ViewModel'de** (arka planda `ReminderChecker`'da) yakalanır ve orada insan diline çevrilir.
Repository hatayı sarmalamaz, olduğu gibi fırlatır.

| Ne oldu | Kotlin'de | Kullanıcıya |
|---|---|---|
| İnternet yok, zaman aşımı | `IOException` | "İnternet bağlantını kontrol et." |
| Sunucu 4xx/5xx döndü | `retrofit2.HttpException` | "Sunucu şu an cevap vermiyor." |
| Beklenmeyen JSON | `JsonSyntaxException` | "Bir şeyler ters gitti. Tekrar dene." |
| Eksik `current` bloğu | `IllegalStateException` | "Bir şeyler ters gitti. Tekrar dene." |

- Yenileme başarısız olursa eski veri korunur, hata Snackbar'da "Güncellenemedi. …" olarak gösterilir.
- Arka plan işinde ağ ve sunucu hataları `Result.retry()` ile yeniden denenir (en fazla 3 kez);
  bozuk yanıt yeniden denenmez.

Teknik ayrıntı her zaman `Log.e(TAG, ..., e)` ile Logcat'e yazılır.

## Durumlar

`LoadState` `sealed` olduğu için `when` dallarından biri unutulursa derleme hatası alınır.
Her ekran dalların tamamını karşılar:

| Durum | Arama ekranı | Tahmin ekranı |
|---|---|---|
| `Idle` | Karşılama görünümü: hatırlatıcılar + popüler şehirler | Oluşmaz (`Loading` ile aynı çizilir) |
| `Loading` | `ShimmerList` | `CircularProgressIndicator` |
| `Success` | `LazyColumn` + `CityCard` | Başlık + kıyafet kartı + saatlik + günlük |
| `Empty` | "Şehir bulunamadı" + öneri | "Bu şehir için tahmin bulunamadı." |
| `Error` | Mesaj + "Tekrar dene" | Mesaj + "Tekrar dene" |

## Route / Screen ayrımı

Her ekran iki parçadır:

- **`XRoute`** — ViewModel'i edinir, `collectAsStateWithLifecycle` ile state'i toplar.
- **`XScreen`** — ViewModel tanımaz; aldığı değerleri çizer, olayları yukarı bildirir.

Bu ayrım sayesinde ekranlar `@Preview` içinde ve testte elle state verilerek çalıştırılabilir
(state hoisting — "değer aşağı, olay yukarı").

ViewModel'ler `viewModelFactory { initializer { … } }` ile kurulur (`CityViewModel.Factory`,
`ForecastViewModel.Factory`): hatırlatıcı servisi `Context` ister. Testlerde kurucu doğrudan,
sahte repository'lerle çağrılır.

## Navigasyon

İki rota: `city` (başlangıç) ve `forecast?name=..&subtitle=..&latitude=..&longitude=..`.

Parametreler yol parçası yerine sorgu parametresidir; çünkü `subtitle` boş olabilir ve boş
bir yol parçası rotayı eşleşmez hâle getirir. Değerler `Uri.encode` ile kaçışlanır.
`ForecastViewModel` parametreleri `SavedStateHandle` üzerinden okur.

Bildirime dokununca `ForecastDeepLink` intent extras'ı ile `MainActivity` açılır (`singleTop`;
uygulama açıksa `onNewIntent`) ve `HavaNavHost` şehir listesinin üstüne tahmin ekranını koyar.

## Zaman: şehrin saati

Servis bütün zamanları şehrin yerel saatinde gönderir (`timezone=auto`). "Şu an" ve "bugün"
telefonun saatinden (`LocalDateTime.now()`) değil, aynı yanıttaki `current.observedAt`'ten
alınır (`upcomingFrom`, `todayAt`, `currentHourOf`). Yalnızca kullanıcıya ait olanlar
(arama ekranı başlığındaki tarih, akşam bildiriminin saati) telefonun saatini kullanır.

## Hava hatırlatıcısı

```
ReminderBell (DatePicker) → ForecastViewModel.createReminder → ReminderService
    ├─ DataStoreReminderRepository.add
    └─ WorkManagerReminderScheduler: günlük iş (24 sa) + hemen bir kontrol + akşam 20:00 işi

ReminderWorker (günlük) → ReminderChecker.checkAll
    → 16 günlük tahmin → hedef gün → ForecastChangeDetector → değiştiyse bildirim + özeti güncelle
EveReminderWorker (önceki akşam) → ReminderChecker.sendEveSummary → "Yarın Ankara'da 14°, …"
```

- İlk kontrol yalnızca özeti kaydeder, bildirim göndermez.
- Özet yalnızca bildirim gönderilince güncellenir; her gün küçük kaymalar birikip eşiği aşınca yakalanır.
- Günü geçen hatırlatıcılar silinir; hatırlatıcı kalmazsa günlük iş iptal edilir.
- Worker'lar incedir: bağımlılıkları kurup işi `ReminderChecker`'a devreder. Kararlar arayüzler
  (`ReminderRepository`, `WeatherRepository`, `ReminderNotifier`) üzerinden verildiği için
  `ReminderCheckerTest` emülatörsüz çalışır.

## Genişletme noktaları

| Senaryo | Eklenir | Değişir | Dokunulmaz |
|---|---|---|---|
| Yeni hava durumu görünümü | `WeatherCondition`'a yeni sabit | `fromWeatherCode` içindeki `when` | Ekranlar, ViewModel'ler |
| Yeni anlık hava değişkeni | `CurrentResponse` + `CurrentWeather` alanı (varsayılanlı) | `ForecastApi.CURRENT_FIELDS`, ekran | Repository, ViewModel |
| Yeni günlük değişken | `DailyResponse` + `DailyForecast` alanı (nullable) | `ForecastApi.DAILY_FIELDS`, `toDailyForecasts` | Repository, ViewModel |
| Yeni kıyafet kuralı | `OutfitItem` sabiti, eşik sabiti | `OutfitAdvisor.advise` | Ekranlar, `OutfitCard` |
| Yeni "tahmin değişti" kuralı | `ChangeReason` sabiti, eşik sabiti | `ForecastChangeDetector.detect` | Worker'lar, bildirim |
| Farklı veri kaynağı | Yeni `CityRepository` / `WeatherRepository` implementasyonu | ViewModel `Factory`'si | Ekranlar, model |
| Sabit şehir listesine dönüş | `CityCatalog` sınıfı | `CityRepository` implementasyonu | Her şey |
