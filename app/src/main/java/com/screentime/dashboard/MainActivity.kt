// ============================================================================
// FILE: app/src/main/java/com/screentime/dashboard/MainActivity.kt
// Screen-Time Dashboard — Android port (Jetpack Compose)
// Visual + functional 1:1 port of the Next.js dashboard page.
// ============================================================================
package com.screentime.dashboard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.min
import kotlin.random.Random

// ---------------------------------------------------------------------------
// THEME — matches the original CSS tokens
// ---------------------------------------------------------------------------
object Theme {
    val Bg      = Color(0xFF0B0B0F)   // page background
    val Panel   = Color(0x13FFFFFF)   // panel: white/[0.03]
    val Line    = Color(0x1FFFFFFF)   // border-line
    val Paper   = Color(0xFFF5F4F0)   // text-paper
    val Fog     = Color(0xFF8B8B96)   // text-fog
    val Ember   = Color(0xFFFF6B4A)   // ember (orange)
    val Accent  = Color(0xFFB7FF5C)   // lime accent (ring / blocking)
    val Violet  = Color(0xFFB08CFF)
    val Cyan    = Color(0xFF6FE3FF)
    val Lime    = Color(0xFFB7FF5C)
}

// ---------------------------------------------------------------------------
// DATA MODELS — mirror @/lib/data
// ---------------------------------------------------------------------------
data class UsageEvent(
    val date: String,             // yyyy-MM-dd
    val appId: Int,
    val minutes: Int,
    val opens: Int,
    val notificationsBlocked: Int,
)

data class BlockedApp(val id: Int, val name: String, val color: Color)
data class AppGroup(val id: Int, val name: String, val color: Color, val enabled: Boolean, val apps: List<BlockedApp>)

data class SessionLite(
    val id: Int, val groupId: Int, val label: String,
    val startedAt: Long, val endsAt: Long, val status: String,
    val strictMode: Boolean,
)

data class ScheduleRule(
    val id: Int, val name: String, val groupId: Int,
    val startMinutes: Int, val endMinutes: Int,
    val days: List<Int>, val enabled: Boolean, val strict: Boolean,
)

// ---------------------------------------------------------------------------
// DEMO REPOSITORY — deterministic sample data shaped exactly like
// getGroupsBundle(), getSessions(), getSchedules(), getUsageSince(400).
// Swap with Room/UsageStatsManager later without touching the UI.
// ---------------------------------------------------------------------------
object DemoData {

    val groups = listOf(
        AppGroup(1, "Social", Theme.Ember, true, listOf(
            BlockedApp(101, "Instagram", Color(0xFFE1306C)),
            BlockedApp(102, "TikTok", Color(0xFF69C9D0)),
            BlockedApp(103, "X", Color(0xFF1DA1F2)),
        )),
        AppGroup(2, "Games", Theme.Violet, true, listOf(
            BlockedApp(104, "Candy Crush", Color(0xFFE44A8C)),
            BlockedApp(105, "Chess", Color(0xFF82B366)),
        )),
        AppGroup(3, "Video", Theme.Cyan, false, listOf(
            BlockedApp(106, "YouTube", Color(0xFFFF0000)),
        )),
    )

    private fun dateStr(offsetDays: Int): String {
        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, offsetDays) }
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
    }

    /** Equivalent of getUsageSince(400): 400 days of events for 3 apps. */
    val events: List<UsageEvent> = run {
        val rnd = Random(42)
        val list = mutableListOf<UsageEvent>()
        val appIds = listOf(101, 102, 103, 104, 106)
        for (d in 399 downTo 0) {
            val day = dateStr(-d)
            for (app in appIds) {
                if (rnd.nextInt(100) < 8) continue // some days an app is untouched
                list += UsageEvent(
                    date = day,
                    appId = app,
                    minutes = rnd.nextInt(4, 95),
                    opens = rnd.nextInt(2, 28),
                    notificationsBlocked = rnd.nextInt(0, 14),
                )
            }
        }
        list
    }

    val schedules: List<ScheduleRule> = listOf(
        ScheduleRule(1, "Morning focus", 1, 480, 720,  days = (0..6).toList(), enabled = true,  strict = true),
        ScheduleRule(2, "Deep work",     2, 840, 1080, days = listOf(1,2,3,4,5), enabled = true,  strict = false),
        ScheduleRule(3, "Wind down",     3, 1320, 1425, days = (0..6).toList(), enabled = true,  strict = false),
        ScheduleRule(4, "Weekend chill", 1, 600, 660,  days = listOf(0,6),       enabled = false, strict = false),
    )

    val sessions: List<SessionLite> = listOf(
        SessionLite(1, 1, "Focus block", System.currentTimeMillis() - 25 * 60_000L,
            System.currentTimeMillis() + 35 * 60_000L, "active", strictMode = true),
        SessionLite(2, 2, "No games evening", System.currentTimeMillis() - 3 * 3600_000L,
            System.currentTimeMillis() - 3600_000L, "ended", strictMode = false),
    )
}

// ---------------------------------------------------------------------------
// FORMAT HELPERS — mirror @/lib/format
// ---------------------------------------------------------------------------
object Fmt {
    fun minutes(m: Int): String = when {
        m >= 60 -> "${m / 60}h ${"%02d".format(m % 60)}m"
        else    -> "${m}m"
    }
    fun clock(min: Int): String {
        val h = min / 60 % 24; val m = min % 60
        val ampm = if (h < 12) "AM" else "PM"
        val h12 = if (h % 12 == 0) 12 else h % 12
        return "$h12:${"%02d".format(m)} $ampm"
    }
    fun todayStr(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    fun dateWithOffset(days: Int): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(
            Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, days) }.time)
    fun pct(delta: Int, base: Int): Int = if (base == 0) 0 else (delta * 100 / base)
}

// ---------------------------------------------------------------------------
// ACTIVITY
// ---------------------------------------------------------------------------
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = Theme.Bg, surface = Theme.Bg)) {
                Surface(modifier = Modifier.fillMaxSize(), color = Theme.Bg) {
                    DashboardScreen()
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// DASHBOARD — 1:1 port of the page component
// ---------------------------------------------------------------------------
private const val GOAL_MIN = 480

@Composable
fun DashboardScreen() {
    val now = remember { Calendar.getInstance() }
    val today = Fmt.todayStr()
    val nowMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

    // --- aggregation (identical logic to the page) ---
    val byDate = remember { mutableMapOf<String, IntArray>() } // minutes, opens, notifBlocked
    val todayApp = remember { mutableMapOf<Int, Int>() }
    var opensToday = 0; var notifBlockedToday = 0
    for (e in DemoData.events) {
        val slot = byDate.getOrPut(e.date) { intArrayOf(0, 0, 0) }
        slot[0] += e.minutes; slot[1] += e.opens; slot[2] += e.notificationsBlocked
        if (e.date == today) {
            todayApp[e.appId] = (todayApp[e.appId] ?: 0) + e.minutes
            opensToday += e.opens; notifBlockedToday += e.notificationsBlocked
        }
    }
    val minutesOf: (String) -> Int = { byDate[it]?.get(0) ?: 0 }
    val todayMin = minutesOf(today)
    val yMin = minutesOf(Fmt.dateWithOffset(-1))

    val series = remember {
        (0..13).map { i ->
            val d = i - 13
            val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, d) }
            val lbl = SimpleDateFormat("EEE d", Locale.US).format(cal.time)
            lbl to minutesOf(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time))
        }
    }
    val avg14 = if (series.isEmpty()) 0 else series.sumOf { it.second } / series.size
    // under-goal streak (from today, capped like the 400-day scan)
    var streak = 0
    for (i in 0 until 400) {
        if (minutesOf(Fmt.dateWithOffset(-i)) <= GOAL_MIN) streak++ else break
    }

    val blocked30 = (0 until 30).sumOf { byDate[Fmt.dateWithOffset(-it)]?.get(2) ?: 0 }

    val appById = DemoData.groups.flatMap { it.apps }.associateBy { it.id }
    val topApps = todayApp.entries.sortedByDescending { it.value }.take(5)
        .mapNotNull { (id, m) -> appById[id]?.let { it to m } }

    val dow = now.get(Calendar.DAY_OF_WEEK) - 1 // Sunday = 0, matches JS getDay()
    val todaysSchedules = DemoData.schedules.filter { it.days.contains(dow) }.map { s ->
        val overnight = s.endMinutes < s.startMinutes
        val active = s.enabled && (if (overnight)
            nowMin >= s.startMinutes || nowMin < s.endMinutes
        else nowMin >= s.startMinutes && nowMin < s.endMinutes)
        val done = !overnight && nowMin >= s.endMinutes
        Triple(s, active, done)
    }

    val blockedAppCount = DemoData.groups.filter { it.enabled }.flatMap { it.apps }.distinctBy { it.id }.size
    val enabledGroups = DemoData.groups.count { it.enabled }
    val hour = now.get(Calendar.HOUR_OF_DAY)
    val greeting = if (hour < 12) "Good morning." else if (hour < 18) "Good afternoon." else "Good evening."
    val dayLabel = SimpleDateFormat("EEEE, MMMM d", Locale.US).format(now.time)

    Box(modifier = Modifier
        .fillMaxSize()
        .background(Theme.Bg)
        .statusBarsPadding()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ---------- HEADER ----------
            item {
                Column {
                    Text(
                        "$dayLabel · ${Fmt.clock(nowMin)}",
                        color = Theme.Fog, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        greeting,
                        color = Theme.Paper, fontSize = 34.sp, fontWeight = FontWeight.Bold,
                    )
                    Text(
                        if (todayMin <= GOAL_MIN) "You're under goal today."
                        else "Goal exceeded — tighten up.",
                        color = Theme.Fog, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                    )
                }
            }

            // ---------- SCREEN TIME RING + STATS ----------
            item {
                Panel {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ScreenTimeRing(
                            minutes = todayMin, goal = GOAL_MIN,
                            deltaPct = if (yMin > 0) Fmt.pct(todayMin - yMin, yMin) else null,
                            modifier = Modifier.size(150.dp),
                        )
                        Spacer(Modifier.width(20.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                StatCell("Apps blocked", "$blockedAppCount", "across $enabledGroups groups", Theme.Ember)
                                StatCell("Notifs silenced", "$notifBlockedToday", "$blocked30 in 30 days", Theme.Violet)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                StatCell("Opens today", "$opensToday", "pickups tracked", Theme.Cyan)
                                StatCell("Under-goal streak", "${streak}d", "goal ≤ ${Fmt.minutes(GOAL_MIN)}/day", Theme.Ember)
                            }
                        }
                    }
                }
            }

            // ---------- QUICK BLOCK ----------
            item {
                Panel {
                    Column(Modifier.padding(20.dp)) {
                        Text("Quick block", color = Theme.Paper, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text("Block a group instantly for a set duration.",
                            color = Theme.Fog, fontSize = 11.sp)
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DemoData.groups.take(3).forEach { g ->
                                SuggestionChip(
                                    onClick = { /* arm block for this group */ },
                                    label = { Text(g.name, fontSize = 12.sp, color = g.color) },
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = g.color.copy(alpha = 0.10f)),
                                    border = SuggestionChipDefaults.suggestionChipBorder(true, borderColor = g.color.copy(alpha = 0.35f)),
                                )
                            }
                        }
                    }
                }
            }

            // ---------- 14-DAY CHART ----------
            item {
                Panel {
                    Column(Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Screen time — 14 days", color = Theme.Paper,
                                    fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Text("avg ${Fmt.minutes(avg14)}/day · processed locally, never in the cloud",
                                    color = Theme.Fog, fontSize = 10.sp)
                            }
                            val d13 = minutesOf(Fmt.dateWithOffset(-13))
                            Pill(
                                text = if (Fmt.pct(avg14 - d13, d13) <= 0) "▾ improving" else "▴ rising",
                                color = Theme.Lime,
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        UsageBarChart(data = series, avg = avg14)
                    }
                }
            }

            // ---------- ACTIVE SESSIONS ----------
            item {
                Panel {
                    Column(Modifier.padding(20.dp)) {
                        Text("Active sessions", color = Theme.Paper, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(12.dp))
                        val active = DemoData.sessions.filter { it.status == "active" }
                        if (active.isEmpty()) {
                            EmptyNote("No sessions running.")
                        } else active.forEach { s ->
                            val g = DemoData.groups.firstOrNull { it.id == s.groupId }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(g?.color?.copy(alpha = 0.07f) ?: Theme.Panel)
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.size(8.dp).clip(CircleShape).background(g?.color ?: Theme.Fog))
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(s.label, color = Theme.Paper, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    Text(g?.name ?: "—", color = Theme.Fog, fontSize = 11.sp)
                                }
                                if (s.strictMode) StrictBadge()
                            }
                        }
                    }
                }
            }

            // ---------- TODAY'S SCHEDULE ----------
            item {
                Panel {
                    Column(Modifier.padding(20.dp)) {
                        Text("Today's schedule", color = Theme.Paper, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(12.dp))
                        if (todaysSchedules.isEmpty()) {
                            EmptyNote("No blocks scheduled today.")
                        } else todaysSchedules.forEach { (s, active, done) ->
                            val g = DemoData.groups.firstOrNull { it.id == s.groupId }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (active) Theme.Accent.copy(alpha = 0.07f) else Theme.Panel)
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.size(8.dp).clip(CircleShape).background(
                                    when {
                                        active -> Theme.Accent
                                        done   -> Theme.Paper.copy(alpha = 0.20f)
                                        else   -> Theme.Paper.copy(alpha = 0.40f)
                                    }))
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(s.name, color = Theme.Paper, fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        if (s.strict) { Spacer(Modifier.width(6.dp)); StrictBadge() }
                                        if (!s.enabled) { Spacer(Modifier.width(6.dp)); OffBadge() }
                                    }
                                    Text(g?.name ?: "—", color = Theme.Fog, fontSize = 11.sp)
                                }
                                Text("${Fmt.clock(s.startMinutes)} – ${Fmt.clock(s.endMinutes)}",
                                    color = Theme.Fog, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                if (active) {
                                    Spacer(Modifier.width(8.dp))
                                    Pill("Blocking now", Theme.Accent, darkText = true)
                                }
                            }
                        }
                    }
                }
            }

            // ---------- AUTOMATION ----------
            item {
                Panel {
                    Column(Modifier.padding(20.dp)) {
                        Text("Automation", color = Theme.Paper, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text("Build shortcuts & routines around your blocks.",
                            color = Theme.Fog, fontSize = 11.sp)
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlineAction("Open shortcut") { /* fire VIEW intent to the automation URL */ }
                            OutlineAction("Copy link") { /* copy deep link to clipboard */ }
                        }
                    }
                }
            }

            // ---------- TOP APPS ----------
            item {
                Panel {
                    Column(Modifier.padding(20.dp)) {
                        Text("Where today went", color = Theme.Paper, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(14.dp))
                        val maxM = topApps.firstOrNull()?.second ?: 1
                        topApps.forEachIndexed { i, (app, minutes) ->
                            Column(Modifier.padding(vertical = 6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        Modifier.size(26.dp).clip(RoundedCornerShape(8.dp))
                                            .background(app.color.copy(alpha = 0.18f)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(app.name.first().toString(), color = app.color,
                                            fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(app.name, color = Theme.Paper, fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(Fmt.minutes(minutes), color = Theme.Paper,
                                            fontSize = 17.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
                                    }
                                    Text("#${i + 1}", color = Theme.Fog, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                }
                                Spacer(Modifier.height(6.dp))
                                Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape)
                                    .background(Theme.Paper.copy(alpha = 0.06f))) {
                                    Box(Modifier
                                        .fillMaxWidth(min(1f, minutes.toFloat() / maxM))
                                        .height(4.dp).clip(CircleShape).background(app.color))
                                }
                            }
                        }
                        if (topApps.isEmpty()) EmptyNote("No usage recorded today yet.")
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// SMALL COMPONENTS
// ---------------------------------------------------------------------------
@Composable
fun Panel(content: @Composable () -> Unit) {
    Box(Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(20.dp))
        .background(Theme.Panel)
        .background(Theme.Paper.copy(alpha = 0.02f))
    ) { content() }
}

@Composable
fun StatCell(label: String, value: String, sub: String, tone: Color) {
    Box(Modifier
        .width(150.dp)
        .clip(RoundedCornerShape(16.dp))
        .background(Theme.Panel)
        .padding(12.dp)
    ) {
        Column {
            Box(Modifier.size(30.dp).clip(RoundedCornerShape(8.dp))
                .background(tone.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(tone))
            }
            Spacer(Modifier.height(8.dp))
            Text(value, color = Theme.Paper, fontSize = 19.sp, fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace)
            Text(label.uppercase(), color = Theme.Fog, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp)
            Text(sub, color = Theme.Fog.copy(alpha = 0.8f), fontSize = 9.sp)
        }
    }
}

@Composable
fun ScreenTimeRing(minutes: Int, goal: Int, deltaPct: Int?, modifier: Modifier = Modifier) {
    val frac = min(1f, minutes.toFloat() / goal)
    val animated by animateFloatAsState(targetValue = frac, animationSpec = tween(900), label = "ring")
    val over = minutes > goal
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(8.dp)) {
            val stroke = 14.dp.toPx()
            drawArc(
                color = Theme.Paper.copy(alpha = 0.07f),
                startAngle = 0f, sweepAngle = 360f, useCenter = false,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
            drawArc(
                brush = Brush.sweepGradient(
                    0f to Theme.Ember, 0.6f to Theme.Ember, 1f to Theme.Accent,
                    center = Offset(size.width / 2, size.height / 2),
                ),
                startAngle = -90f, sweepAngle = animated * 360f, useCenter = false,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (over) "⚠" else "", color = Theme.Ember, fontSize = 18.sp)
            Text(Fmt.minutes(minutes), color = Theme.Paper, fontSize = 26.sp,
                fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Text("of ${Fmt.minutes(goal)}", color = Theme.Fog, fontSize = 11.sp)
            deltaPct?.let {
                Text(
                    (if (it >= 0) "+$it%" else "$it%") + " vs yesterday",
                    color = if (it <= 0) Theme.Lime else Theme.Ember, fontSize = 10.sp,
                )
            }
        }
    }
}

@Composable
fun UsageBarChart(data: List<Pair<String, Int>>, avg: Int, modifier: Modifier = Modifier) {
    val maxV = (data.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)
    Row(modifier.fillMaxWidth().height(140.dp), horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom) {
        data.forEach { (label, v) ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    val hFrac = v.toFloat() / maxV
                    Box(Modifier
                        .fillMaxHeight(hFrac.coerceAtLeast(0.02f))
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                        .background(
                            if (v <= GOAL_MIN) Theme.Accent.copy(alpha = 0.85f)
                            else Theme.Ember.copy(alpha = 0.9f)
                        ))
                }
                Spacer(Modifier.height(4.dp))
                Text(label.split(" ").last(), color = Theme.Fog, fontSize = 9.sp)
            }
        }
    }
}

@Composable
fun Pill(text: String, color: Color, darkText: Boolean = false) {
    Box(Modifier.clip(CircleShape).background(color.copy(alpha = if (darkText) 1f else 0.12f))
        .padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text(text, color = if (darkText) Color.Black else color,
            fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun StrictBadge() {
    Box(Modifier.clip(RoundedCornerShape(4.dp)).background(Theme.Ember.copy(alpha = 0.15f))
        .padding(horizontal = 5.dp, vertical = 2.dp)) {
        Text("STRICT", color = Theme.Ember, fontSize = 8.sp, fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp)
    }
}

@Composable
fun OffBadge() {
    Box(Modifier.clip(RoundedCornerShape(4.dp)).background(Theme.Paper.copy(alpha = 0.10f))
        .padding(horizontal = 5.dp, vertical = 2.dp)) {
        Text("OFF", color = Theme.Fog, fontSize = 8.sp, fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp)
    }
}

@Composable
fun EmptyNote(text: String) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
        .background(Theme.Paper.copy(alpha = 0.02f))
        .padding(vertical = 28.dp), contentAlignment = Alignment.Center) {
        Text(text, color = Theme.Fog, fontSize = 12.sp)
    }
}

@Composable
fun OutlineAction(text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = Theme.Accent
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            Theme.Accent.copy(alpha = 0.35f)
        )
    ) {
        Text(text, fontSize = 12.sp)
    }
}
