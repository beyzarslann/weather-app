package com.kampplus.hava.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Uygulamanın renk paleti. MaterialTheme sayesinde renkler tek yerden yönetilir.
 *
 * Renkler burada **isimlendirilir**, [Theme] dosyasında açık/koyu temaya dağıtılır.
 * Bileşenler hiçbir zaman buradaki sabitleri doğrudan kullanmaz; `MaterialTheme.colorScheme`
 * üzerinden okur. Böylece tek satır değişince bütün uygulama yeni kıyafetini giyer.
 *
 * İsimlerdeki sayı tonu belirtir: 0 siyah, 100 beyaz; sayı büyüdükçe renk açılır.
 *
 * Palet mavi-gri: gökyüzünü çağrıştıran sakin bir mavi (Blue) ana vurgu,
 * onun soluk hâli (BlueGrey) ikincil vurgu, sıcak bir kum rengi (Sand) ise
 * üçüncül vurgu. Nötr tonlar hafif mavimsidir ki zemin "soğuk" ve temiz dursun.
 */

// Ana vurgu: butonlar, odak kenarlığı, "Şimdi" kartı, TR rozeti.
internal val Blue10 = Color(0xFF001C3A)
internal val Blue20 = Color(0xFF00315D)
internal val Blue30 = Color(0xFF0B4884)
internal val Blue40 = Color(0xFF185FA5)
internal val Blue80 = Color(0xFFA4C9FF)
internal val Blue90 = Color(0xFFD4E3FF)

// İkincil vurgu: daha az dikkat çekmesi gereken öğeler.
internal val BlueGrey10 = Color(0xFF111C2B)
internal val BlueGrey20 = Color(0xFF263141)
internal val BlueGrey30 = Color(0xFF3C4758)
internal val BlueGrey40 = Color(0xFF545F71)
internal val BlueGrey80 = Color(0xFFBCC7DB)
internal val BlueGrey90 = Color(0xFFD8E3F8)

// Üçüncül vurgu: yabancı ülke rozeti gibi "farklı" olanı ayırmak için.
internal val Sand10 = Color(0xFF2A1800)
internal val Sand20 = Color(0xFF462A00)
internal val Sand30 = Color(0xFF643F00)
internal val Sand40 = Color(0xFF845400)
internal val Sand80 = Color(0xFFFFB955)
internal val Sand90 = Color(0xFFFFDDB5)

// Nötr tonlar: zeminler, kartlar ve yazılar.
// Ara tonlar (4, 6, 12, 17, 22, 24, 87, 92, 94, 96, 98) surfaceContainer rolleri içindir.
internal val Neutral4 = Color(0xFF0C0E12)
internal val Neutral6 = Color(0xFF111418)
internal val Neutral10 = Color(0xFF191C20)
internal val Neutral12 = Color(0xFF1D2024)
internal val Neutral17 = Color(0xFF282A2F)
internal val Neutral20 = Color(0xFF2E3135)
internal val Neutral22 = Color(0xFF33353A)
internal val Neutral24 = Color(0xFF37393E)
internal val Neutral87 = Color(0xFFD8DADE)
internal val Neutral90 = Color(0xFFE0E3E7)
internal val Neutral92 = Color(0xFFE5E8EC)
internal val Neutral94 = Color(0xFFEBEEF2)
internal val Neutral95 = Color(0xFFEFF1F4)
internal val Neutral96 = Color(0xFFF1F4F7)
internal val Neutral98 = Color(0xFFF7F9FB)
internal val Neutral100 = Color(0xFFFFFFFF)

// Nötr-varyant tonlar: ikincil yazılar, kenarlıklar, ayırıcı çizgiler.
internal val NeutralVariant30 = Color(0xFF43474E)
internal val NeutralVariant50 = Color(0xFF73777F)
internal val NeutralVariant60 = Color(0xFF8D9199)
internal val NeutralVariant80 = Color(0xFFC3C6CF)
internal val NeutralVariant90 = Color(0xFFDFE2EB)

// Hata renkleri.
internal val Red10 = Color(0xFF410002)
internal val Red20 = Color(0xFF690005)
internal val Red30 = Color(0xFF93000A)
internal val Red40 = Color(0xFFBA1A1A)
internal val Red80 = Color(0xFFFFB4AB)
internal val Red90 = Color(0xFFFFDAD6)
