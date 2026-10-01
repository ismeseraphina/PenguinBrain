package com.mhss.app.mybrain.presentation.main

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mhss.app.data.clock.ClockCard
import com.mhss.app.data.clock.PenguinClock
import com.mhss.app.database.MyBrainDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.sin

private val Purple = Color(0xFF6F4CAD)
private val Blue = Color(0xFF2965C9)
private val Red = Color(0xFFD53A2F)
private val Green = Color(0xFF1E9651)
private val Pink = Color(0xFFB83A6E)

private fun kindColor(kind: String) = when (kind) {
    "event-live", "event" -> Purple
    "event-next" -> Blue
    "task" -> Red
    "routine" -> Green
    else -> Pink
}

private fun kindLabel(kind: String) = when (kind) {
    "event-live" -> "Happening now"
    "event-next" -> "Coming up"
    "task" -> "Do now"
    "routine" -> "Routine"
    "event" -> "Event"
    else -> "Your timer"
}

private fun clockText(ms: Long): String {
    val s = (ms + 999) / 1000
    val h = s / 3600
    return if (h > 0) "%d:%02d:%02d".format(h, (s % 3600) / 60, s % 60) else "%02d:%02d".format(s / 60, s % 60)
}

@Composable
fun ClockDashboardWidget(modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    val database: MyBrainDatabase = koinInject()
    LaunchedEffect(Unit) { PenguinClock.load(context) }
    val timer by PenguinClock.timer.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var cards by remember { mutableStateOf(emptyList<ClockCard>()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            PenguinClock.tick(context, now)
            delay(250)
        }
    }
    val minute = now / 60_000
    LaunchedEffect(minute) {
        cards = withContext(Dispatchers.IO) {
            try { PenguinClock.cards(context, database, now) } catch (_: Exception) { emptyList() }
        }
    }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try { PenguinClock.scheduleNotifications(context, database) } catch (_: Exception) { }
        }
    }

    Column(modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val t = timer
        val tint = if (t == null) Pink else kindColor(t.kind)
        Surface(
            shape = RoundedCornerShape(30.dp),
            color = tint.copy(alpha = 0.12f),
            modifier = Modifier
                .fillMaxWidth()
                .border(2.dp, tint.copy(alpha = 0.35f), RoundedCornerShape(30.dp))
        ) {
            Column(
                Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (t == null) {
                    val cal = Calendar.getInstance().apply { timeInMillis = now }
                    val sec = (cal.get(Calendar.SECOND) * 1000 + cal.get(Calendar.MILLISECOND)) / 60_000f
                    ClockRing(progress = sec, color = Pink) {
                        Text(
                            "%02d:%02d".format(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE)),
                            fontSize = 60.sp, fontWeight = FontWeight.ExtraBold
                        )
                        Text("Clock", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        "Nothing running. Swipe the cards below and press START, or pick a length.",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf(25, 50, 90).forEach { m ->
                            OutlinedButton(
                                onClick = { PenguinClock.start(context, "Focus $m min", m) },
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.size(width = 84.dp, height = 64.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("$m", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                                    Text("min", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                } else {
                    val remaining = t.remainingAt(now)
                    val progress = (t.elapsedAt(now).toFloat() / t.duration).coerceIn(0f, 1f)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Chip(if (t.finished) "Finished" else if (t.running) kindLabel(t.kind) else "Paused", tint)
                        Text("${PenguinClock.dur(t.duration)} session", style = MaterialTheme.typography.bodySmall)
                    }
                    ClockRing(progress = progress, color = tint) {
                        Text(clockText(remaining), fontSize = 60.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                        Text(t.label, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                    }
                    LinearProgressIndicator(
                        progress = { progress },
                        color = tint,
                        trackColor = tint.copy(alpha = 0.15f),
                        strokeCap = StrokeCap.Round,
                        modifier = Modifier.fillMaxWidth().height(14.dp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (t.finished) {
                            BigButton("Done", tint) { PenguinClock.stop(context) }
                            TextButton(onClick = { PenguinClock.addMinutes(context, 5) }) { Text("+5 min", fontSize = 17.sp) }
                        } else {
                            TextButton(onClick = { PenguinClock.stop(context) }) { Text("Stop", fontSize = 17.sp) }
                            if (t.running) BigButton("Pause", tint) { PenguinClock.pause(context) }
                            else BigButton("Resume", tint) { PenguinClock.resume(context) }
                            TextButton(onClick = { PenguinClock.addMinutes(context, 5) }) { Text("+5 min", fontSize = 17.sp) }
                        }
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Start next", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(if (cards.isEmpty()) "Swipe for manual timer" else "${cards.size} ready · swipe", style = MaterialTheme.typography.bodySmall)
        }
        val pages = cards.size + 1
        val pager = rememberPagerState { pages }
        HorizontalPager(
            state = pager,
            contentPadding = PaddingValues(end = 36.dp),
            pageSpacing = 12.dp,
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            if (page < cards.size) SuggestionCard(cards[page], now) { title, m, kind -> PenguinClock.start(context, title, m, kind) }
            else ManualCard { title, m -> PenguinClock.start(context, title, m) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            repeat(pages) { i ->
                Box(
                    Modifier
                        .padding(3.dp)
                        .size(width = if (i == pager.currentPage) 22.dp else 8.dp, height = 8.dp)
                        .background(if (i == pager.currentPage) Pink else Color.Gray.copy(alpha = 0.35f), CircleShape)
                )
            }
        }
    }
}

@Composable
private fun ClockRing(progress: Float, color: Color, content: @Composable () -> Unit) {
    val animated by animateFloatAsState(progress, label = "ring")
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val tick = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
    val face = MaterialTheme.colorScheme.surface
    Box(Modifier.fillMaxWidth(0.82f).aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.065f
            val r = size.minDimension / 2 - stroke / 2
            drawCircle(face, radius = r)
            drawCircle(track, radius = r, style = Stroke(stroke))
            drawArc(
                color, startAngle = -90f, sweepAngle = 360f * animated, useCenter = false,
                topLeft = Offset(center.x - r, center.y - r), size = androidx.compose.ui.geometry.Size(r * 2, r * 2),
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
            for (i in 0 until 12) {
                val a = Math.toRadians(i * 30.0 - 90)
                val outer = r - stroke * 0.9f
                val inner = outer - if (i % 3 == 0) stroke * 0.8f else stroke * 0.45f
                drawLine(
                    tick,
                    Offset(center.x + (cos(a) * inner).toFloat(), center.y + (sin(a) * inner).toFloat()),
                    Offset(center.x + (cos(a) * outer).toFloat(), center.y + (sin(a) * outer).toFloat()),
                    strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(36.dp)) { content() }
    }
}

@Composable
private fun Chip(text: String, color: Color) {
    Text(
        text,
        color = Color.White,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        modifier = Modifier.background(color, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 5.dp)
    )
}

@Composable
private fun BigButton(text: String, color: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = color),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.heightIn(min = 56.dp).width(140.dp)
    ) { Text(text, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun Stepper(minutes: Int, onChange: (Int) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
    ) {
        TextButton(onClick = { onChange((minutes - 5).coerceAtLeast(1)) }) { Text("−", fontSize = 20.sp) }
        Text("$minutes min", fontWeight = FontWeight.Bold)
        TextButton(onClick = { onChange(minutes + 5) }) { Text("+", fontSize = 20.sp) }
    }
}

@Composable
private fun CardShell(color: Color, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = color.copy(alpha = 0.14f),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 230.dp)
            .border(2.dp, color.copy(alpha = 0.35f), RoundedCornerShape(26.dp))
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
    }
}

@Composable
private fun SuggestionCard(c: ClockCard, now: Long, onStart: (String, Int, String) -> Unit) {
    val color = kindColor(c.kind)
    var minutes by remember(c.key) { mutableIntStateOf(c.minutes) }
    CardShell(color) {
        Chip(kindLabel(c.kind), color)
        Text(c.title, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(c.detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (c.at > now && (c.kind == "event-next" || c.kind == "routine")) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("starts in ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(clockText(c.at - now), fontSize = 26.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (c.kind == "event-live" && c.end > c.at) {
            LinearProgressIndicator(
                progress = { ((now - c.at).toFloat() / (c.end - c.at)).coerceIn(0f, 1f) },
                color = color, trackColor = color.copy(alpha = 0.15f), strokeCap = StrokeCap.Round,
                modifier = Modifier.fillMaxWidth().height(8.dp)
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Stepper(minutes) { minutes = it }
            Button(
                onClick = { onStart(c.title, minutes, if (c.kind.startsWith("event")) "event" else c.kind) },
                colors = ButtonDefaults.buttonColors(containerColor = Pink),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.heightIn(min = 52.dp)
            ) { Text("START", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
        }
    }
}

@Composable
private fun ManualCard(onStart: (String, Int) -> Unit) {
    var minutes by remember { mutableIntStateOf(25) }
    CardShell(Pink) {
        Chip("Manual", Pink)
        Text("Any time", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(25, 50, 90).forEach { m ->
                OutlinedButton(
                    onClick = { minutes = m },
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(2.dp, if (minutes == m) Pink else MaterialTheme.colorScheme.outlineVariant)
                ) { Text("$m min", fontWeight = FontWeight.Bold) }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Stepper(minutes) { minutes = it }
            Button(
                onClick = { onStart("Focus $minutes min", minutes) },
                colors = ButtonDefaults.buttonColors(containerColor = Pink),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.heightIn(min = 52.dp)
            ) { Text("START", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
        }
    }
}
