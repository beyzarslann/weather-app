# Hava

Hava — Kotlin ve Jetpack Compose ile geliştirilmiş Android hava durumu uygulaması.

Bu proje, KAMP+ Bilişim Kampı (T.C. Gençlik ve Spor Bakanlığı & Turkcell Geleceği Yazanlar) Android
programında geliştirilen [referans projenin](https://github.com/tc-yusufburakdemir/weather-app) üzerine
kendi özelliklerimi ekleyerek geliştirdiğim bir uygulamadır.

## Ekran görüntüleri

<table>
  <tr>
    <td align="center"><img src="docs/screenshots/arama.png" width="220" alt="Arama ekranı" /><br />Arama</td>
    <td align="center"><img src="docs/screenshots/tahmin.png" width="220" alt="Tahmin ekranı" /><br />Tahmin</td>
  </tr>
  <tr>
    <td align="center"><img src="docs/screenshots/yagmur.png" width="220" alt="Yağmurlu hava" /><br />Yağmurlu hava</td>
    <td align="center"><img src="docs/screenshots/hatirlatici.png" width="220" alt="Hava hatırlatıcısı" /><br />Hatırlatıcı</td>
  </tr>
</table>

## Özellikler

### Kamp projesinden gelen temel

- **Şehir arama:** Open-Meteo Geocoding API ile Türkçe şehir sonuçları.
- **Anlık, saatlik ve 7 günlük tahmin:** WMO hava kodları emoji ve Türkçe etikete çevrilir.
- **Dört UI durumu:** Yükleniyor (Shimmer), sonuç, boş sonuç ve "Tekrar dene" düğmeli hata; `sealed` bir `LoadState` ile hiçbiri atlanamaz.
- **Testler:** JSON eşlemesi, sütun→satır çevrimi, hava kodu eşlemesi ve iki ViewModel için JVM birim testleri.
- **CI:** Her pull request'te ve `main`'e push'ta GitHub Actions ktlint (Spotless) kontrolü, birim testleri ve debug derlemesi çalıştırır.
- **Release derlemesi:** R8 küçültme ve `keystore.properties` ile imzalama.
- **Checkpoint'ler:** `cp1`–`cp4` dalları projenin adım adım gelişimini gösterir ([docs/GIT-HISTORY.md](docs/GIT-HISTORY.md)).

### Benim eklediklerim

**Arama**

- **Canlı arama:** Yazmayı bıraktıktan 400 ms sonra otomatik arama yapılır (debounce); en az 2 harf gerekir ve süren eski istek iptal edilir.
- **Yeni arama ekranı:** Boş açılır ve "Hangi şehre bakalım?" karşılama görünümünü gösterir; kutu temizlenince süren arama iptal edilir.
- **Popüler şehirler:** İstanbul, Ankara, İzmir, Antalya, Bursa ve Trabzon çiplerine dokununca arama beklemeden yapılır.
- **Yenilenen üst kısım ve şehir kartları:** Türkçe tarihli büyük başlık, dolgulu arama kutusu ve yurt dışı sonuçları farklı renkte gösteren ülke rozetleri; alt başlıkta "Türkiye Cumhuriyeti" yerine "Türkiye" yazar.

**Görünüm**

- **Yeni renk paleti:** Mavi-gri tonlar; açık ve koyu temada `surfaceContainer` dahil bütün renk rolleri tanımlı.
- **Dinamik arka plan:** Tahmin ekranının arka planı hava durumuna göre yumuşak geçişli bir gradyandır.
- **Hava efektleri:** Güneş ışınları, süzülen bulutlar, eğik yağmur, salınan kar ve çift çakan şimşek Canvas ile çizilir; sistemde animasyonlar kapalıysa gösterilmez.

**Tahmin ekranı**

- **Yeni tahmin ekranı:** Ortalanmış büyük sıcaklık, bugünün en yüksek ve en düşük değeri, arka planı gösteren yarı saydam kartlar ve vurgulu "Şimdi" kartı.
- **Sıcaklık çubukları:** Her gün, haftalık ölçekte en düşükten en yükseğe uzanan, soğuktan sıcağa renklenen bir çubukla gösterilir.
- **Aşağı çekip yenileme:** Yenileme sırasında mevcut veri ekranda kalır; başarısız olursa eski veri korunur ve Snackbar ile bilgi verilir.
- **Kıyafet önerisi:** "Bugün ne giysem?" kartı; günün sıcaklığı, hissedilen sıcaklık, yağış, kar, rüzgâr ve UV indeksine bakan kural tabanlı bir motorla (`OutfitAdvisor`) öneri ve kısa bir özet üretir.
- **Saat dilimi düzeltmesi:** Saatlik liste, "Şimdi" kartı ve "Bugün" telefonun değil şehrin yerel saatine göre belirlenir.

**Hava hatırlatıcısı**

- **Hatırlatıcı kurma:** Tahmin ekranındaki zilden yarından itibaren 15 gün içinde bir tarih seçilir; Android 13 ve üstünde bildirim izni istenir.
- **Günlük kontrol:** WorkManager ile günde bir 16 günlük tahmin kontrol edilir; hava türü, en yüksek/en düşük sıcaklık (3°) veya yağış olasılığı (20 puan) değişirse bildirim gönderilir.
- **Akşam özeti:** Hedef günden önceki akşam 20:00'de "Yarın Ankara'da 14°, yağmurlu." biçiminde, kıyafet önerisini de içeren bir bildirim gelir.
- **Hatırlatıcılarım:** Kurulu hatırlatıcılar DataStore'da saklanır, arama ekranında listelenir ve silinebilir; bildirime ya da satıra dokununca o şehrin tahmini açılır.
- **Debug kısayolu:** Debug derlemesinde zile uzun basınca kontrol ve akşam özeti beklemeden çalışır.

## Kullanılan teknolojiler

Kotlin · Jetpack Compose · Material 3 · MVVM · StateFlow · Coroutines · Retrofit · Gson · Navigation Compose · WorkManager · DataStore · Open-Meteo API

## Kurulum ve çalıştırma

```bash
git clone https://github.com/beyzarslann/weather-app.git
```

Projeyi Android Studio'da açın, Gradle senkronizasyonu bittikten sonra bir emülatör ya da cihazda çalıştırın.
Uygulama Android 8.0 (API 26) ve üstünü destekler.

Komut satırından:

```bash
./gradlew assembleDebug        # debug APK
./gradlew testDebugUnitTest    # birim testleri
./gradlew spotlessCheck        # ktlint biçim kontrolü
```

Windows'ta `./gradlew` yerine `gradlew.bat` kullanın.

API anahtarı gerekmez: Open-Meteo ücretsizdir ve anahtar istemez.

## Teşekkür

KAMP+ Bilişim Kampı'ndaki eğitmenlerime, bu projeyi geliştirirken verdikleri destek ve rehberlik için teşekkür ederim.
