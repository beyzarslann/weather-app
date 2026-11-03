package com.kampplus.hava.ui.forecast

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kampplus.hava.R
import com.kampplus.hava.model.CurrentWeather
import com.kampplus.hava.model.DailyForecast
import com.kampplus.hava.model.OutfitAdvice
import com.kampplus.hava.model.OutfitAdvisor
import com.kampplus.hava.model.OutfitItem
import com.kampplus.hava.ui.theme.HavaTheme
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * "Bugün ne giysem?" kartı: başlık, özet cümlesi ve önerilen parçalar.
 *
 * Kart yalnızca çizer; hangi parçaların önerileceğine [OutfitAdvisor] karar verir.
 * Böylece kurallar ekranı açmadan test edilebilir, kart da @Preview'da elle
 * verilmiş bir [OutfitAdvice] ile denenebilir.
 *
 * Zemini diğer kartlarla aynı yarı saydam renktir ([translucentCardColors]); arkadaki
 * hava gradient'i ve efektler kartın içinden de hissedilir.
 */
@Composable
fun OutfitCard(advice: OutfitAdvice, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth(), colors = translucentCardColors()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = stringResource(R.string.outfit_title), style = MaterialTheme.typography.titleMedium)
            Text(
                text = advice.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // FlowRow: parçalar yan yana dizilir, satır dolunca alt satıra kayar.
            // Kışın 6–7 parça olabilir; Row kullansaydık sondakiler kesilirdi.
            FlowRow(
                modifier = Modifier.padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                advice.items.forEach { OutfitChip(item = it) }
            }
        }
    }
}

/**
 * Tek bir parça: emoji + etiket, hap biçiminde.
 *
 * Material'in Chip bileşenlerini kullanmıyoruz: onlar dokunulabilir öğelerdir ve
 * ekran okuyucu "düğme" diye okur. Bu çipler yalnızca bilgi verir.
 */
@Composable
private fun OutfitChip(item: OutfitItem) {
    val description = stringResource(R.string.outfit_item_description, item.label)
    Surface(
        shape = RoundedCornerShape(percent = 50),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = CHIP_ALPHA),
        // Emoji ekran okuyucuda "yağmur şemsiyesi" gibi ayrıca okunmasın; tek, sade bir açıklama yeter.
        modifier = Modifier.clearAndSetSemantics { contentDescription = description }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(text = item.emoji, style = MaterialTheme.typography.bodyMedium)
            Text(text = item.label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Çipin zemini kartın yarı saydam zemininden biraz daha belirgin olsun diye. */
private const val CHIP_ALPHA = 0.7f

// ---------------------------------------------------------------------------------------
// Önizlemeler: kurallar gerçek OutfitAdvisor'dan geçer; böylece önizleme de bir tür test olur.
// ---------------------------------------------------------------------------------------

/** Önizleme için bir gün + anlık durum; yalnızca verilen alanlar değişir. */
private fun previewAdvice(
    min: Double,
    max: Double,
    weatherCode: Int,
    rainChance: Int? = null,
    uv: Double? = null,
    maxWindKmh: Double? = null,
    feelsColderBy: Double = 0.0
): OutfitAdvice {
    val today = DailyForecast(
        date = LocalDate.of(2026, 11, 1),
        minTemperatureC = min,
        maxTemperatureC = max,
        weatherCode = weatherCode,
        precipitationProbability = rainChance,
        uvIndexMax = uv,
        maxWindSpeedKmh = maxWindKmh
    )
    val now = (min + max) / 2
    val current = CurrentWeather(
        temperatureC = now,
        weatherCode = weatherCode,
        observedAt = LocalDateTime.of(2026, 11, 1, 9, 0),
        apparentTemperatureC = now - feelsColderBy
    )
    return OutfitAdvisor.advise(today, current)
}

/** Kartı gerçek ekrandaki gibi renkli bir zeminde gösterir; yarı saydamlık görünsün diye. */
@Composable
private fun OutfitCardPreviewFrame(top: Color, bottom: Color, advice: OutfitAdvice) {
    HavaTheme {
        Box(
            modifier = Modifier
                .background(Brush.verticalGradient(listOf(top, bottom)))
                .padding(16.dp)
        ) {
            OutfitCard(advice = advice)
        }
    }
}

@Preview(name = "Soğuk ve yağmurlu", widthDp = 360)
@Composable
private fun OutfitCardColdRainyPreview() {
    OutfitCardPreviewFrame(
        top = Color(0xFFAFC0CF),
        bottom = Color(0xFFE6ECF1),
        advice = previewAdvice(min = 3.0, max = 9.0, weatherCode = 63, rainChance = 80, maxWindKmh = 34.0, feelsColderBy = 3.0)
    )
}

@Preview(name = "Ilık", widthDp = 360)
@Composable
private fun OutfitCardMildPreview() {
    OutfitCardPreviewFrame(
        top = Color(0xFFCFE3F5),
        bottom = Color(0xFFF4F8FB),
        advice = previewAdvice(min = 9.0, max = 21.0, weatherCode = 2, rainChance = 35)
    )
}

@Preview(name = "Sıcak ve güneşli", widthDp = 360)
@Composable
private fun OutfitCardHotSunnyPreview() {
    OutfitCardPreviewFrame(
        top = Color(0xFFFFE29F),
        bottom = Color(0xFFFFF6E5),
        advice = previewAdvice(min = 24.0, max = 33.0, weatherCode = 0, uv = 9.0)
    )
}
