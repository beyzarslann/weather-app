# 🌤️ Hava Durumu Uygulaması

Kotlin ile geliştirilmiş basit bir Android hava durumu uygulaması. Şehir arayıp anlık hava durumunu ve tahminleri görebilirsiniz.

## Özellikler

- Şehir arama
- Anlık hava durumu
- Günlük / saatlik tahmin

## Kurulum

```bash
git clone https://github.com/beyzarslann/weather-app.git
```

Projeyi Android Studio'da açın, Gradle senkronizasyonu bittikten sonra çalıştırın.

### Eklediklerim

- **Canlı arama:** Yazmayı bıraktıktan kısa süre sonra otomatik arama yapılır (debounce). En az 2 harf gerekir ve eski istekler iptal edilir.
- **Daha isabetli sonuçlar:** Arama sonuçları yalnızca yerleşim yerleriyle filtrelenir ve nüfusa göre sıralanır.
- **Dinamik arka plan:** Tahmin ekranının arka planı hava durumuna göre değişen animasyonlu bir gradyandır.
- **Aşağı çekip yenileme:** Yenileme sırasında mevcut veri ekranda kalmaya devam eder.

<p align="center">
  <img src="https://github.com/user-attachments/assets/02d01d7e-2d34-4d38-b7c4-2edfb9b1e6d8" width="250" />
  <img src="https://github.com/user-attachments/assets/5d1a3dd9-e491-42a8-bae4-4e20f79b5a82" width="250" />
</p>
