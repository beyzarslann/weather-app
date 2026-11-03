package com.kampplus.hava.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParseException
import com.google.gson.TypeAdapter
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import com.kampplus.hava.model.WeatherReminder
import java.time.LocalDate
import java.time.format.DateTimeParseException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Hava hatırlatıcılarının saklandığı yer.
 *
 * [WeatherRepository] ile aynı desen: ekran ve arka plan işi yalnızca bu arayüzü tanır;
 * verinin DataStore'da mı, veritabanında mı durduğunu bilmez. Testlerde sahte bir
 * uygulama verilebilir.
 */
interface ReminderRepository {

    /** Bütün hatırlatıcılar, hedef tarihe göre sıralı. Liste her değiştiğinde yeni değer yayılır. */
    val reminders: Flow<List<WeatherReminder>>

    /** Yeni hatırlatıcı ekler. Aynı kimlikte bir kayıt varsa onun yerine geçer. */
    suspend fun add(reminder: WeatherReminder)

    /** Kimliği eşleşen kaydı değiştirir (ör. son tahmin özetini günceller). Kayıt yoksa bir şey yapmaz. */
    suspend fun update(reminder: WeatherReminder)

    /** Kimliği verilen kaydı siler. */
    suspend fun remove(id: String)

    /**
     * Hedef tarihi [today]'den önce olan kayıtları siler ve silinenleri döndürür.
     * Hedef günün kendisi silinmez; o gün de takip edilir.
     */
    suspend fun removeExpired(today: LocalDate): List<WeatherReminder>
}

/**
 * Gerçek uygulama: hatırlatıcıları DataStore'da tek bir JSON metni olarak tutar.
 *
 * Neden tek bir JSON? Hatırlatıcı sayısı küçüktür (birkaç tane) ve hep birlikte okunur.
 * Bunun için Room veritabanı kurmak fazla olurdu; DataStore Preferences + Gson yeter.
 *
 * DataStore, SharedPreferences'ın yerini alan, `suspend` ve `Flow` ile çalışan bir
 * saklama aracıdır. `edit { }` bloğu atomiktir: iki iş aynı anda yazmaya kalkarsa
 * biri diğerini bekler, kayıt kaybolmaz.
 *
 * @param dataStore Testlerde geçici bir dosyaya yazan DataStore verilir.
 */
class DataStoreReminderRepository(
    private val dataStore: DataStore<Preferences>
) : ReminderRepository {

    /** Uygulamada kullanılan kurucu; tek DataStore örneğini [Context] üzerinden alır. */
    constructor(context: Context) : this(context.applicationContext.reminderDataStore)

    override val reminders: Flow<List<WeatherReminder>> = dataStore.data.map { prefs ->
        ReminderJson.decode(prefs[KEY_REMINDERS]).sortedBy { it.targetDate }
    }

    override suspend fun add(reminder: WeatherReminder) = modify { list ->
        list.filterNot { it.id == reminder.id } + reminder
    }

    override suspend fun update(reminder: WeatherReminder) = modify { list ->
        list.map { if (it.id == reminder.id) reminder else it }
    }

    override suspend fun remove(id: String) = modify { list -> list.filterNot { it.id == id } }

    override suspend fun removeExpired(today: LocalDate): List<WeatherReminder> {
        var removed = emptyList<WeatherReminder>()
        modify { list ->
            val (expired, active) = list.partition { it.targetDate.isBefore(today) }
            removed = expired
            active
        }
        return removed
    }

    /** Listeyi okur, [transform] ile değiştirir ve geri yazar; hepsi tek atomik işlemde. */
    private suspend fun modify(transform: (List<WeatherReminder>) -> List<WeatherReminder>) {
        dataStore.edit { prefs ->
            prefs[KEY_REMINDERS] = ReminderJson.encode(transform(ReminderJson.decode(prefs[KEY_REMINDERS])))
        }
    }

    private companion object {
        val KEY_REMINDERS = stringPreferencesKey("reminders_json")
    }
}

/**
 * Uygulama genelinde tek DataStore örneği.
 *
 * `preferencesDataStore` delegesi aynı dosya için tek örnek garanti eder. Aynı dosyaya
 * iki DataStore açmak DataStore'un en bilinen hatasıdır ve çalışma anında çöker.
 */
private val Context.reminderDataStore: DataStore<Preferences> by preferencesDataStore(name = "reminders")

/**
 * Hatırlatıcı listesi ↔ JSON metni.
 *
 * Gson, `java.time.LocalDate`'i kendiliğinden doğru saklayamaz (iç alanlarına yansıma ile
 * erişmeye çalışır; yeni Java/Android sürümlerinde bu engellenir). Bu yüzden tarihi
 * "2026-11-07" biçiminde metin olarak yazan küçük bir adaptör kaydediyoruz.
 */
internal object ReminderJson {

    private val gson: Gson = GsonBuilder()
        .registerTypeAdapter(LocalDate::class.java, LocalDateAdapter().nullSafe())
        .create()

    private val listType = object : TypeToken<List<WeatherReminder>>() {}.type

    fun encode(reminders: List<WeatherReminder>): String = gson.toJson(reminders, listType)

    /**
     * Metni listeye çevirir. Metin yoksa ya da bozuksa boş liste döner: bozuk bir kayıt
     * yüzünden uygulama ve arka plan işi çökmemeli.
     */
    fun decode(json: String?): List<WeatherReminder> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            gson.fromJson<List<WeatherReminder>>(json, listType).orEmpty()
        } catch (e: JsonParseException) {
            Log.e(TAG, "Hatırlatıcılar okunamadı; boş liste kullanılıyor", e)
            emptyList()
        } catch (e: DateTimeParseException) {
            // Tarih adaptörü bozuk bir tarihte bunu fırlatır; Gson onu sarmalamaz.
            Log.e(TAG, "Hatırlatıcı tarihi okunamadı; boş liste kullanılıyor", e)
            emptyList()
        }
    }

    private class LocalDateAdapter : TypeAdapter<LocalDate>() {
        override fun write(out: JsonWriter, value: LocalDate) {
            out.value(value.toString())
        }

        override fun read(input: JsonReader): LocalDate = LocalDate.parse(input.nextString())
    }

    private const val TAG = "ReminderJson"
}
