package com.kampplus.hava.ui.city

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kampplus.hava.model.City
import com.kampplus.hava.ui.theme.HavaTheme

/**
 * Listedeki tek bir şehir satırı.
 *
 * Yapısı: `Card` → `Row` → rozet + `Spacer` + `Column(weight(1f))` + sağ ok.
 *
 * `Modifier.weight(1f)`: ortadaki sütun kalan boşluğun tamamını kaplar, böylece
 * sağ oku sağ kenara iter. Uzun şehir adları (Afyonkarahisar) rozeti ezmez.
 *
 * @param onClick Karta dokunulduğunda çağrılır. Bileşen ne olacağını bilmez,
 *   yalnızca haber verir — olay yukarı, veri aşağı (state hoisting).
 */
@Composable
fun CityCard(city: City, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = CityCardDefaults.shape,
        // Beyaz kart + ince kenarlık: gölge yerine çizgiyle ayrışan, sakin bir görünüm.
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(CityCardDefaults.contentPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CityCardDefaults.spacing)
        ) {
            CountryBadge(code = city.countryCode)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = city.name,
                    style = MaterialTheme.typography.titleMedium,
                    // Tek satıra sığmayan ad "..." ile kısaltılır, satır kaymaz.
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = city.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                // Ok yalnızca görseldir; "dokunulabilir" bilgisini Card zaten verir.
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Ülke kodu rozeti ("TR").
 *
 * Veri API'den geldiği için elimizde plaka numarası yok; rozete ülke kodunu
 * koyuyoruz. Kod gelmezse "?" gösterilir.
 *
 * Renk ülkeye göre değişir: TR sakin mavi (primaryContainer), diğer ülkeler
 * kum rengi (tertiaryContainer). "Ankara" araması ABD'deki bir Ankara'yı da
 * döndürebilir; farklı renk, yabancı sonucu bir bakışta ayırt ettirir.
 */
@Composable
private fun CountryBadge(code: String?, modifier: Modifier = Modifier) {
    val isTurkey = code == "TR"
    Surface(
        modifier = modifier.size(CityCardDefaults.badgeSize),
        shape = CityCardDefaults.badgeShape,
        color = if (isTurkey) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = if (isTurkey) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onTertiaryContainer
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = code ?: "?",
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

/**
 * Kartın ölçüleri tek yerde. [com.kampplus.hava.ui.component.ShimmerList] de bunları
 * kullanır; böylece yükleniyor iskeleti gerçek kartla birebir aynı boyutta kalır.
 */
object CityCardDefaults {
    val shape = RoundedCornerShape(14.dp)
    val badgeSize = 40.dp
    val badgeShape = RoundedCornerShape(10.dp)
    val contentPadding = 12.dp
    val spacing = 12.dp
}

@Preview(showBackground = true)
@Composable
private fun CityCardPreview() {
    HavaTheme {
        CityCard(
            city = City(
                id = 323786,
                name = "Ankara",
                latitude = 39.91987,
                longitude = 32.85427,
                countryCode = "TR",
                country = "Türkiye",
                admin1 = "Ankara"
            ),
            onClick = {}
        )
    }
}
