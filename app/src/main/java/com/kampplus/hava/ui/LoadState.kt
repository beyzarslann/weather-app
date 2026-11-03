package com.kampplus.hava.ui

/**
 * Bir ağ isteğinin bitebileceği dört durum ve isteğin hiç başlamadığı bekleme hâli.
 *
 * Ekran her an bu durumlardan **tam olarak birindedir**:
 * - [Idle]    : Henüz istek yapılmadı (ör. arama kutusu boş). Ekran karşılama görünümü çizer.
 * - [Loading] : Veri bekleniyor. Kullanıcı bir şeylerin olduğunu görmeli (Shimmer / gösterge).
 * - [Success] : Veri geldi, içinde kayıt var.
 * - [Empty]   : Cevap geldi ama içi boş. En çok unutulan durum budur.
 * - [Error]   : Bir şey ters gitti. Anlaşılır mesaj + "Tekrar dene".
 *
 * `sealed` = durum listesi kapalıdır. Ekranı çizerken `when` içinde bir dalı
 * unutursan Kotlin derleme hatası verir; durum atlamak imkânsız hâle gelir.
 *
 * Tip jeneriktir: `out T` sayesinde hem şehir listesi hem de tahmin ekranı aynı
 * yapıyı paylaşır; dört durumu her ekran için ayrı ayrı yazmak gerekmez.
 */
sealed interface LoadState<out T> {

    /**
     * Kullanıcı henüz bir şey istemedi. Yalnızca şehir aramasında kullanılır;
     * tahmin ekranı açılır açılmaz istek attığı için bu duruma hiç düşmez.
     */
    data object Idle : LoadState<Nothing>

    data object Loading : LoadState<Nothing>

    data class Success<T>(val data: T) : LoadState<T>

    data object Empty : LoadState<Nothing>

    /** [message] kullanıcıya gösterilecek, insan dilinde yazılmış metindir. */
    data class Error(val message: String) : LoadState<Nothing>
}
