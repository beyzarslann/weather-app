package com.kampplus.hava.ui.forecast

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.kampplus.hava.R
import com.kampplus.hava.model.TurkishText
import com.kampplus.hava.model.reminderDateRange
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.launch

/**
 * Üst bardaki zil: dokununca tarih seçici açılır, seçilen gün için hatırlatıcı kurulur.
 *
 * Akış:
 * 1. Zile dokun → tarih seçici (yalnızca yarından itibaren 15 gün seçilebilir).
 * 2. Gün seçildi → Android 13+ ve bildirim izni yoksa izin istenir.
 * 3. İzin verilsin ya da verilmesin hatırlatıcı **kaydedilir**: kullanıcı izni sonradan
 *    açarsa takip kaldığı yerden sürer. İzin yoksa Snackbar ayarlara giden yolu gösterir.
 *
 * İzin isteği bir Activity sonucudur; bu yüzden ViewModel'de değil burada, ekranda yönetilir.
 *
 * @param hasReminder Bu şehir için kurulu hatırlatıcı varsa zil dolu çizilir.
 * @param cityToday Şehrin bugünü; seçilebilir tarihler buna göre hesaplanır.
 * @param onCreateReminder Seçilen gün için hatırlatıcıyı kaydeder.
 * @param onDebugRunNow Yalnızca debug derlemesinde dolu: zile uzun basınca işleri hemen çalıştırır.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReminderBell(
    hasReminder: Boolean,
    cityToday: LocalDate,
    snackbarHostState: SnackbarHostState,
    onCreateReminder: (LocalDate) -> Unit,
    onDebugRunNow: (() -> Unit)?
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // rememberSaveable: tarih seçici açıkken ekran döndürülürse kapanmasın.
    var showPicker by rememberSaveable { mutableStateOf(false) }
    // İzin penceresi açıkken seçilen gün burada bekler; LocalDate Serializable olduğu için saklanabilir.
    var pendingDate by rememberSaveable { mutableStateOf<LocalDate?>(null) }

    /** Hatırlatıcıyı kaydeder ve sonucu Snackbar ile bildirir. */
    fun save(date: LocalDate) {
        onCreateReminder(date)
        val allowed = notificationsAllowed(context)
        scope.launch {
            if (allowed) {
                snackbarHostState.showSnackbar(context.getString(R.string.reminder_created, TurkishText.dayAndMonth(date)))
            } else {
                val result = snackbarHostState.showSnackbar(
                    message = context.getString(R.string.reminder_permission_denied),
                    actionLabel = context.getString(R.string.reminder_open_settings),
                    duration = SnackbarDuration.Long
                )
                if (result == SnackbarResult.ActionPerformed) context.startActivity(notificationSettingsIntent(context))
            }
        }
    }

    // Sonuç ne olursa olsun kaydedilir; izin durumu save() içinde yeniden okunur.
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        pendingDate?.let(::save)
        pendingDate = null
    }

    fun onDatePicked(date: LocalDate) {
        showPicker = false
        if (shouldRequestPermission(context)) {
            pendingDate = date
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            save(date)
        }
    }

    val description = stringResource(if (hasReminder) R.string.reminder_action_active else R.string.reminder_action_add)
    // IconButton uzun basmayı desteklemez; aynı görünümü combinedClickable ile kuruyoruz.
    // minimumInteractiveComponentSize: dokunma alanı en az 48 dp olsun (erişilebilirlik).
    Box(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .size(40.dp)
            .clip(CircleShape)
            .combinedClickable(
                role = Role.Button,
                onClickLabel = description,
                onClick = { showPicker = true },
                onLongClick = onDebugRunNow?.let { run ->
                    {
                        run()
                        scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.reminder_debug_started)) }
                    }
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (hasReminder) Icons.Filled.Notifications else Icons.Outlined.Notifications,
            contentDescription = description
        )
    }

    if (showPicker) {
        ReminderDatePicker(
            range = remember(cityToday) { reminderDateRange(cityToday) },
            onDismiss = { showPicker = false },
            onConfirm = ::onDatePicked
        )
    }
}

/**
 * Material3 tarih seçici; aralığın dışındaki günler soluk ve seçilemez.
 *
 * DatePicker tarihleri "UTC gece yarısı milisaniyesi" olarak verir ve alır. Yerel saat
 * dilimiyle çevirseydik UTC'nin gerisindeki ülkelerde seçilen gün bir gün önceye kayardı;
 * bu yüzden iki yönde de UTC kullanıyoruz.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderDatePicker(range: ClosedRange<LocalDate>, onDismiss: () -> Unit, onConfirm: (LocalDate) -> Unit) {
    val years = range.start.year..range.endInclusive.year
    val state = rememberDatePickerState(
        initialDisplayedMonthMillis = range.start.toUtcMillis(),
        yearRange = years,
        selectableDates = remember(range) {
            object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis.toUtcDate() in range
                override fun isSelectableYear(year: Int) = year in years
            }
        }
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                // Gün seçilmeden "Kur" basılamaz.
                enabled = state.selectedDateMillis != null,
                onClick = { state.selectedDateMillis?.let { onConfirm(it.toUtcDate()) } }
            ) { Text(stringResource(R.string.reminder_picker_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.reminder_picker_cancel)) }
        }
    ) {
        DatePicker(
            state = state,
            title = {
                Text(
                    text = stringResource(R.string.reminder_picker_title),
                    modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp)
                )
            }
        )
    }
}

private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toUtcDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

/** Android 13+ ve izin henüz verilmemiş: çalışma anında istenmesi gerekir. */
private fun shouldRequestPermission(context: Context): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

/**
 * Bildirim gerçekten gösterilebilir mi? İzin (13+) ve kullanıcının uygulama bildirim
 * ayarı birlikte. Android 12 ve öncesinde izin yoktur ama kullanıcı bildirimleri
 * ayarlardan kapatmış olabilir; o zaman da aynı uyarıyı gösteririz.
 */
private fun notificationsAllowed(context: Context): Boolean =
    !shouldRequestPermission(context) && NotificationManagerCompat.from(context).areNotificationsEnabled()

/**
 * Uygulamanın bildirim ayarları sayfası. Genel "Uygulama bilgisi" sayfası yerine doğrudan
 * bildirim ayarına götürüyoruz; kullanıcı bir dokunuş daha az yapar. (minSdk 26 ile her cihazda var.)
 */
private fun notificationSettingsIntent(context: Context) = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
