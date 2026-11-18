package com.rupio.app.bench

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.ReportDrawnWhen
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.rupio.app.core.WeightUnit
import com.rupio.app.data.billing.SupportState
import com.rupio.app.data.billing.WallEntry
import com.rupio.app.data.billing.WallMe
import com.rupio.app.data.billing.WallState
import com.rupio.app.data.league.League
import com.rupio.app.data.league.LeagueGlobalMe
import com.rupio.app.data.league.LeagueRow
import com.rupio.app.data.league.LeagueState
import com.rupio.app.data.league.TopRow
import com.rupio.app.domain.LeagueRules
import com.rupio.app.ui.support.LeagueContent
import com.rupio.app.ui.support.LeagueView
import com.rupio.app.ui.support.WallAccess
import com.rupio.app.ui.support.WallContent
import com.rupio.app.ui.theme.RupioColors
import com.rupio.app.ui.theme.RupioTheme
import java.time.LocalDate

/**
 * Pantalla de medición (solo compilación "benchmark"): las mismas composables de la app con datos de ejemplo, sin red.
 *   adb shell am start -W -n com.rupio.app.bench/com.rupio.app.bench.LeagueBenchActivity --es screen group|global|wall
 * "global" simula el keyset: empieza con 50 filas y suma 50 cada vez que la lista pide más (hasta 2,000).
 */
class LeagueBenchActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val screen = intent.getStringExtra("screen") ?: "group"
        val monday = LocalDate.now().let { it.minusDays((it.dayOfWeek.value - 1).toLong()) }
        setContent {
            RupioTheme {
                Surface(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), color = RupioColors.Background) {
                    when (screen) {
                        "wall" -> {
                            val entries = remember { (1..300).map { WallEntry(it, name(it), 1_763_000_000_000L + it * 3_600_000L, it == 7) } }
                            WallContent(WallState(entries, WallMe(name(7), 600, 7, 50, false, false, 0, 300), loaded = true), SupportState(), WallAccess.READY,
                                onJoin = {}, onBoost = {}, onRename = {}, onHide = {}, onCreateAccount = {}, onRetry = {})
                            ReportDrawnWhen { true }
                        }
                        else -> {
                            var top by remember { mutableStateOf(page(0)) }
                            val state = LeagueState(league = group(monday), global = LeagueGlobalMe(1234, 60_000, 3), top = top, topEnd = top.size >= 2000, loaded = true)
                            LeagueContent(state, WeightUnit.LB, WallAccess.READY, LocalDate.now(), if (screen == "global") LeagueView.GLOBAL else LeagueView.GROUP, {},
                                rememberLazyListState(), rememberLazyListState(), onJoin = {}, onLeave = {}, onCreateAccount = {}, onRetry = {},
                                onLoadMore = { if (top.size < 2000) top = top + page(top.size) })
                            ReportDrawnWhen { true }
                        }
                    }
                }
            }
        }
    }

    private val names = listOf("Caro Fit", "El Profe Toño", "Mau", "Fer del gym", "Lupita", "Sebas", "José Ángel", "Dani R.", "La Güera", "Chuy")
    private fun name(i: Int) = names[i % names.size] + " $i"

    private fun group(monday: LocalDate): League {
        val rows = (1..30).map { r ->
            LeagueRow("${name(r)}|$r", r, name(r), r % 5, 24_800.0 - r * 610, 1 + r % 5, r == 7, LeagueRules.zone(r, 30, 5, 5))
        }
        return League(true, monday, 3, false, 30, 5, 5, rows)
    }

    private fun page(from: Int) = (from until from + 50).map { i ->
        TopRow("t$i", i + 1, name(i), i % 5, 1 + i % 6, 90_000.0 - i * 40, 90_000.0 - i * 40, "x$i")
    }
}
