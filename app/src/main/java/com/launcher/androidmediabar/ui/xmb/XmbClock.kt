package com.launcher.androidmediabar.ui.xmb

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Current local time, refreshed at the start of every minute. */
@Composable
fun rememberCurrentMinute(): State<LocalDateTime> = produceState(LocalDateTime.now()) {
    while (true) {
        val now = LocalDateTime.now()
        value = now
        val untilNextMinute = 60_000L - (now.second * 1_000L + now.nano / 1_000_000L)
        delay(untilNextMinute.coerceAtLeast(500L))
    }
}

@Composable
fun XmbClock(now: LocalDateTime, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val is24h = DateFormat.is24HourFormat(context)
    val formatter = remember(locale, is24h) {
        val skeleton = if (is24h) "EEEdMMMHHmm" else "EEEdMMMhmma"
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
    }
    Column(modifier, horizontalAlignment = Alignment.End) {
        BasicText(text = now.format(formatter), style = XmbTextStyles.Clock)
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .width(240.dp)
                .height(1.dp)
                .background(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.7f)))),
        )
    }
}
