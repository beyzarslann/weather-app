package com.kampplus.hava.ui.city

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kampplus.hava.R
import com.kampplus.hava.model.TurkishText
import com.kampplus.hava.model.WeatherReminder
import com.kampplus.hava.ui.theme.HavaTheme
import java.time.LocalDate

/**
 * Arama kutusu boşken gösterilen karşılama görünümü.
 *
 * Boş bir ekran kullanıcıya ne yapacağını söylemez. Burada hem ne beklendiğini
 * anlatıyoruz ("Hangi şehre bakalım?") hem de tek dokunuşla arama yapılabilen
 * popüler şehirler sunuyoruz.
 *
 * Kurulu hatırlatıcı varsa popüler şehirlerin üstünde "Hatırlatıcılarım" bölümü çıkar;
 * yoksa bölüm hiç çizilmez (boş bir başlık kullanıcıyı şaşırtırdı).
 *
 * @param onSuggestionClick Bir çipe dokunulunca şehrin adıyla çağrılır.
 *   Kutuyu doldurup aramayı başlatmak ViewModel'in işidir.
 * @param onReminderClick Hatırlatıcı satırına dokunulunca; o şehrin tahmini açılır.
 * @param onReminderDelete Satırdaki sil ikonu.
 */
@Composable
fun WelcomeView(
    onSuggestionClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    reminders: List<WeatherReminder> = emptyList(),
    onReminderClick: (WeatherReminder) -> Unit = {},
    onReminderDelete: (WeatherReminder) -> Unit = {}
) {
    val cities = stringArrayResource(R.array.popular_cities)
    Column(
        modifier = modifier
            .fillMaxSize()
            // Küçük ekranda ya da klavye açıkken içerik sığmayabilir.
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Yuvarlak zemin ikonu öne çıkarır; düz bir ikon boşlukta kaybolurdu.
        Surface(
            modifier = Modifier.size(88.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    // Dekoratif ikon; anlamı başlık zaten veriyor.
                    contentDescription = null,
                    modifier = Modifier.size(40.dp)
                )
            }
        }
        Text(
            text = stringResource(R.string.welcome_title),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 20.dp)
        )
        Text(
            text = stringResource(R.string.welcome_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
        if (reminders.isNotEmpty()) {
            Text(
                text = stringResource(R.string.reminders_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp, bottom = 8.dp)
            )
            // Az sayıda satır olduğu ve sayfa zaten kaydırılabildiği için LazyColumn yerine Column.
            // (Kaydırılabilir bir sütunun içine LazyColumn koymak Compose'da hata verir.)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                reminders.forEach { reminder ->
                    ReminderRow(
                        reminder = reminder,
                        onClick = { onReminderClick(reminder) },
                        onDelete = { onReminderDelete(reminder) }
                    )
                }
            }
        }
        Text(
            text = stringResource(R.string.popular_cities_title),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 32.dp, bottom = 8.dp)
        )
        // FlowRow: çipler yan yana dizilir, satır dolunca alt satıra kayar.
        // Row kullansaydık dar ekranda son çipler taşıp kesilirdi.
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            cities.forEach { city ->
                AssistChip(
                    onClick = { onSuggestionClick(city) },
                    label = { Text(city) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.LocationOn,
                            contentDescription = null,
                            modifier = Modifier.size(AssistChipDefaults.IconSize)
                        )
                    }
                )
            }
        }
    }
}

/**
 * Tek bir hatırlatıcı: zil · şehir ve tarih · sil.
 *
 * Kart şehir kartıyla aynı dili konuşur (beyaz zemin, ince kenarlık, [CityCardDefaults.shape]);
 * kullanıcı ikisinin de "bir şehre gider" olduğunu bir bakışta anlar.
 */
@Composable
private fun ReminderRow(reminder: WeatherReminder, onClick: () -> Unit, onDelete: () -> Unit) {
    val date = TurkishText.dayAndMonth(reminder.targetDate)
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = CityCardDefaults.shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(start = CityCardDefaults.contentPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CityCardDefaults.spacing)
        ) {
            Icon(
                imageVector = Icons.Filled.Notifications,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = CityCardDefaults.contentPadding)
            ) {
                Text(
                    text = reminder.cityName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = date,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Ayrı bir dokunma hedefi: karta değil sil ikonuna basınca yalnızca silinir.
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.reminder_delete, reminder.cityName, date),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun WelcomeViewWithRemindersPreview() {
    HavaTheme {
        WelcomeView(
            onSuggestionClick = {},
            reminders = listOf(
                WeatherReminder("1", "Ankara", "Ankara, Türkiye", 39.92, 32.85, LocalDate.of(2026, 11, 14)),
                WeatherReminder("2", "Trabzon", "Trabzon, Türkiye", 41.0, 39.72, LocalDate.of(2026, 11, 16))
            )
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun WelcomeViewPreview() {
    HavaTheme {
        WelcomeView(onSuggestionClick = {})
    }
}
