package com.kampplus.hava.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kampplus.hava.ui.city.CityCardDefaults
import com.kampplus.hava.ui.theme.HavaTheme

/**
 * Yükleniyor durumunun görünümü.
 *
 * Shimmer, içerik gelmeden önce içeriğin **iskeletini** gösterir ve üzerinden
 * kayan bir ışıltı geçirir. Dönen bir çark yerine bunu tercih ederiz; çünkü
 * kullanıcı ne geleceğini önceden görür ve bekleme daha kısa hissedilir.
 *
 * Shimmer, Compose'a hazır gelmez; aşağıdaki animasyonu kendimiz yazıyoruz.
 */
@Composable
fun ShimmerList(modifier: Modifier = Modifier, itemCount: Int = 6) {
    val brush = shimmerBrush()
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Gerçek kartlarla aynı ölçülerde boş kartlar çizilir. Ölçüler [CityCardDefaults]'tan
        // okunur; kart değişirse iskelet de kendiliğinden uyar ve yükleme bitince ekran zıplamaz.
        repeat(itemCount) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = CityCardDefaults.shape,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier.padding(CityCardDefaults.contentPadding),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CityCardDefaults.spacing)
                ) {
                    Box(
                        modifier = Modifier
                            .size(CityCardDefaults.badgeSize)
                            .background(brush, CityCardDefaults.badgeShape)
                    )
                    // Gerçek kartta şehir adı + alt yazı iki satırı yaklaşık 44.dp tutar;
                    // aynı yüksekliği verip çizgileri dikeyde eşit aralıkla dağıtıyoruz.
                    Column(
                        modifier = Modifier.height(TEXT_BLOCK_HEIGHT),
                        verticalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.55f)
                                .height(14.dp)
                                .background(brush, RoundedCornerShape(4.dp))
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.35f)
                                .height(10.dp)
                                .background(brush, RoundedCornerShape(4.dp))
                        )
                    }
                }
            }
        }
    }
}

/**
 * Soldan sağa kayan ışıltı efekti.
 *
 * `rememberInfiniteTransition` + `infiniteRepeatable`: bitmeyen bir animasyon kurar.
 * Değişen değer, gradient'in yatay konumudur; her karede biraz sağa kayar.
 */
@Composable
private fun shimmerBrush(): Brush {
    // Taban kart zemininden biraz koyu, ışıltı ise ondan açık. surfaceBright her iki
    // temada da surfaceContainerHigh'tan aydınlık olduğu için koyu temada da "parlama" gibi durur.
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    val highlight = MaterialTheme.colorScheme.surfaceBright
    val translate by rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue = 0f,
        targetValue = SHIMMER_DISTANCE,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )
    return Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(translate - SHIMMER_WIDTH, 0f),
        end = Offset(translate, 0f)
    )
}

private val TEXT_BLOCK_HEIGHT = 44.dp
private const val SHIMMER_DISTANCE = 1_500f
private const val SHIMMER_WIDTH = 400f

@Preview(showBackground = true)
@Composable
private fun ShimmerListPreview() {
    HavaTheme {
        ShimmerList()
    }
}
