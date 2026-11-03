package com.kampplus.hava.ui.city

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kampplus.hava.R
import com.kampplus.hava.ui.theme.HavaTheme

/**
 * Arama kutusu boşken gösterilen karşılama görünümü.
 *
 * Boş bir ekran kullanıcıya ne yapacağını söylemez. Burada hem ne beklendiğini
 * anlatıyoruz ("Hangi şehre bakalım?") hem de tek dokunuşla arama yapılabilen
 * popüler şehirler sunuyoruz.
 *
 * @param onSuggestionClick Bir çipe dokunulunca şehrin adıyla çağrılır.
 *   Kutuyu doldurup aramayı başlatmak ViewModel'in işidir.
 */
@Composable
fun WelcomeView(onSuggestionClick: (String) -> Unit, modifier: Modifier = Modifier) {
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

@Preview(showBackground = true)
@Composable
private fun WelcomeViewPreview() {
    HavaTheme {
        WelcomeView(onSuggestionClick = {})
    }
}
