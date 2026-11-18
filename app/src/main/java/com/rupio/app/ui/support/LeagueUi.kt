package com.rupio.app.ui.support

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.rupio.app.Routes
import com.rupio.app.container
import com.rupio.app.core.Dates
import com.rupio.app.core.Fmt
import com.rupio.app.core.WeightUnit
import com.rupio.app.data.auth.AuthState
import com.rupio.app.data.league.League
import com.rupio.app.data.league.LeagueException
import com.rupio.app.data.league.LeagueGlobalMe
import com.rupio.app.data.league.LeagueRow
import com.rupio.app.data.league.LeagueState
import com.rupio.app.data.league.TopRow
import com.rupio.app.domain.LeagueCopy
import com.rupio.app.domain.LeagueRules
import com.rupio.app.domain.LeagueZone
import com.rupio.app.ui.components.SectionCard
import com.rupio.app.ui.theme.Metals
import com.rupio.app.ui.theme.RupioColors
import com.rupio.app.ui.theme.Tabular
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.roundToLong

/** Las dos vistas de la pestaña Ligas. "Mi grupo" va primero: compararse con 30 de tu nivel motiva más que un ranking de todos (Jia 2017). */
enum class LeagueView(val label: String) { GROUP("Mi grupo"), GLOBAL("Global") }

/**
 * Kilos de la tabla: completos y con separador de miles ("12,450 lb"). El formato corto ("12.5 mil lb") haría que dos
 * lugares seguidos se vieran iguales.
 */
fun leagueKg(kg: Double, unit: WeightUnit): String =
    if (!kg.isFinite() || kg < 0) "— ${unit.label}" else String.format(Fmt.locale, "%,d %s", unit.fromKg(kg).roundToLong(), unit.label)

// ---------------------------------------------------------------------------------------------------------------
// Pestaña (con estado)
// ---------------------------------------------------------------------------------------------------------------

@Composable
fun LeagueTab(
    nav: NavHostController, snackbar: SnackbarHostState, access: WallAccess,
    view: LeagueView, onView: (LeagueView) -> Unit, groupList: LazyListState, globalList: LazyListState,
) {
    val c = container()
    val state by c.league.state.collectAsStateWithLifecycle()
    val settings by c.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val auth by c.auth.state.collectAsStateWithLifecycle()
    val zone by c.zone.collectAsStateWithLifecycle()
    val unit = settings?.unit ?: WeightUnit.LB
    val today = remember(zone) { Dates.today(zone) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(access) { if (access == WallAccess.READY) c.league.load() }

    var joining by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    fun act(block: suspend () -> Unit) = scope.launch {
        try { block() } catch (e: LeagueException) { snackbar.showSnackbar(e.message.orEmpty()) }
        catch (e: Exception) { snackbar.showSnackbar(com.rupio.app.data.league.LeagueRepository.errorText(e)) }
    }

    LeagueContent(
        state = state, unit = unit, access = access, today = today, view = view, onView = onView,
        groupList = groupList, globalList = globalList,
        onJoin = { joining = true }, onLeave = { leaving = true },
        onCreateAccount = { nav.navigate(Routes.ACCOUNT) },
        onRetry = { scope.launch { c.league.load(force = true) } },
        onLoadMore = { scope.launch { c.league.loadTop() } },
    )

    if (joining) {
        val wallName = c.wall.state.value.me?.name
        WallNameDialog(
            title = if (state.league?.member == true) "Tu apodo en las ligas" else "Participar en las ligas",
            intro = if (state.league?.member == true) null else "Elige cómo te verán en tu grupo. Puede ser un apodo.",
            initial = state.nickname ?: wallName ?: (auth as? AuthState.SignedIn)?.name.orEmpty(),
            confirm = if (state.league?.member == true && state.league?.hidden == false) "Guardar" else "Participar",
            askConsent = true, onDismiss = { joining = false }, label = "Apodo", consentText = LeagueCopy.CONSENT,
        ) { name -> joining = false; act { c.league.join(name) } }
    }
    if (leaving) AlertDialog(
        onDismissRequest = { leaving = false },
        title = { Text("¿Salir de las ligas?") },
        text = { Text("Tu apodo deja de verse y sales del grupo de esta semana. Tus entrenos no cambian. Puedes volver cuando quieras.") },
        confirmButton = { TextButton(onClick = { leaving = false; act { c.league.setHidden(true) } }) { Text("Salir") } },
        dismissButton = { TextButton(onClick = { leaving = false }) { Text("Cancelar") } },
    )
}

// ---------------------------------------------------------------------------------------------------------------
// Contenido (sin estado propio: lo usan la pestaña, las pruebas y las capturas)
// ---------------------------------------------------------------------------------------------------------------

@Composable
fun LeagueContent(
    state: LeagueState, unit: WeightUnit, access: WallAccess, today: LocalDate,
    view: LeagueView, onView: (LeagueView) -> Unit, groupList: LazyListState, globalList: LazyListState,
    onJoin: () -> Unit, onLeave: () -> Unit, onCreateAccount: () -> Unit, onRetry: () -> Unit, onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val l = state.league
    when {
        access != WallAccess.READY -> Scroll(modifier) { NeedsAccountCard(access == WallAccess.LOCAL_ONLY, onCreateAccount) }
        l == null -> Scroll(modifier) {
            if (state.error != null) ErrorLine(state.error, onRetry)
            else Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
        !l.member -> Scroll(modifier) { state.error?.let { ErrorLine(it, onRetry) }; IntroCard(onJoin) }
        l.hidden -> Scroll(modifier) {
            state.error?.let { ErrorLine(it, onRetry) }
            SectionCard {
                Text(LeagueCopy.TITLE, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(LeagueCopy.HIDDEN, style = MaterialTheme.typography.bodyMedium, color = RupioColors.TextDim)
                Spacer(Modifier.height(12.dp))
                Button(onClick = onJoin, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Volver a participar") }
            }
        }
        !l.inGroup -> Scroll(modifier) {
            state.error?.let { ErrorLine(it, onRetry) }
            LeagueHeader(l, today)
            SectionCard {
                Text(LeagueCopy.NO_WORKOUT, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(4.dp))
                Text(LeagueCopy.SYNC_NOTE, style = MaterialTheme.typography.bodySmall, color = RupioColors.TextDim)
            }
            Footer(onJoin, onLeave)
        }
        else -> Column(modifier.fillMaxSize()) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                LeagueView.entries.forEachIndexed { i, v ->
                    SegmentedButton(view == v, { onView(v) }, SegmentedButtonDefaults.itemShape(i, LeagueView.entries.size)) { Text(v.label, maxLines = 1) }
                }
            }
            state.error?.let { Box(Modifier.padding(horizontal = 16.dp)) { ErrorLine(it, onRetry) } }
            Box(Modifier.weight(1f)) {
                if (view == LeagueView.GROUP) GroupList(l, unit, today, groupList, onJoin, onLeave)
                else GlobalList(state, unit, globalList, onLoadMore)
            }
            // "Tu lugar" fijo fuera de la lista: siempre a la vista, sin buscarte.
            if (view == LeagueView.GROUP) GroupMeBar(l, unit, groupList) else GlobalMeBar(state.global)
        }
    }
}

@Composable
private fun Scroll(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp, 8.dp, 16.dp, 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
}

@Composable
private fun ErrorLine(text: String, onRetry: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = RupioColors.Warning, modifier = Modifier.weight(1f))
        TextButton(onClick = onRetry) { Text("Reintentar") }
    }
}

@Composable
private fun NeedsAccountCard(localOnly: Boolean, onCreateAccount: () -> Unit) {
    SectionCard {
        Text("🏁  ${LeagueCopy.TITLE}", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(LeagueCopy.NEEDS_ACCOUNT, style = MaterialTheme.typography.bodyMedium, color = RupioColors.TextDim)
        if (localOnly) {
            Spacer(Modifier.height(10.dp))
            Button(onClick = onCreateAccount, modifier = Modifier.fillMaxWidth()) { Text("Crear cuenta y conservar mis datos") }
        }
    }
}

/** Quien todavía no participa: qué es, cómo se gana, las 6 ligas y el botón. */
@Composable
private fun IntroCard(onJoin: () -> Unit) {
    SectionCard {
        Text("🏁  ${LeagueCopy.TITLE}", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(8.dp))
        Text(LeagueCopy.INTRO, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(10.dp))
        TierLadder(1, mineLabel = "empiezas aquí")
        Spacer(Modifier.height(10.dp))
        Text(LeagueCopy.SCORE, style = MaterialTheme.typography.bodySmall, color = RupioColors.TextDim)
        Spacer(Modifier.height(4.dp))
        Text(LeagueCopy.PAY, style = MaterialTheme.typography.bodySmall, color = RupioColors.TextDim)
        Spacer(Modifier.height(14.dp))
        Button(onClick = onJoin, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp), shape = RoundedCornerShape(14.dp)) {
            Text("Participar", fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Las 6 ligas de abajo hacia arriba; la tuya resaltada (con texto, no solo color). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TierLadder(current: Int, mineLabel: String = "tú") {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (t in 1..LeagueRules.TIERS) {
            val mine = t == current
            val shape = RoundedCornerShape(50)
            Text("$t · ${LeagueRules.tierName(t)}" + if (mine) " ($mineLabel)" else "", style = MaterialTheme.typography.labelMedium,
                color = if (mine) RupioColors.Primary else RupioColors.TextDim, fontWeight = if (mine) FontWeight.Bold else FontWeight.Normal, maxLines = 1,
                modifier = Modifier.clip(shape).background(if (mine) RupioColors.Primary.copy(alpha = 0.14f) else RupioColors.Surface2)
                    .then(if (mine) Modifier.border(1.dp, RupioColors.Primary.copy(alpha = 0.6f), shape) else Modifier)
                    .padding(horizontal = 10.dp, vertical = 5.dp))
        }
    }
}

/** Tu liga, la semana y cuántos días quedan. */
@Composable
private fun LeagueHeader(l: League, today: LocalDate) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(RupioColors.Primary.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                Text("${l.tier}", style = MaterialTheme.typography.titleMedium.merge(Tabular), color = RupioColors.Primary, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Liga ${l.tierName}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                val week = l.week
                // Dos líneas fijas (antes se partía donde cayera y dejaba "personas" sola).
                if (week != null) Text("Semana ${LeagueRules.weekLabel(week)}", style = MaterialTheme.typography.bodySmall, color = RupioColors.TextDim)
                Text(buildString {
                    if (week != null) append(LeagueRules.daysLeftText(today, week))
                    if (l.inGroup) { if (isNotEmpty()) append(" · "); append("${l.size} ${if (l.size == 1) "persona" else "personas"}") }
                }, style = MaterialTheme.typography.bodySmall, color = RupioColors.TextDim)
            }
        }
        if (l.inGroup && l.up == 0) {
            Spacer(Modifier.height(8.dp))
            Text(LeagueCopy.SMALL_GROUP, style = MaterialTheme.typography.bodySmall, color = RupioColors.TextFaint)
        }
    }
}

@Composable
private fun Footer(onRename: () -> Unit, onLeave: () -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(LeagueCopy.SCORE, style = MaterialTheme.typography.labelMedium, color = RupioColors.TextFaint, textAlign = TextAlign.Center)
        Text(LeagueCopy.PAY, style = MaterialTheme.typography.labelMedium, color = RupioColors.TextFaint, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 2.dp))
        FlowRowButtons(onRename, onLeave)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowButtons(onRename: () -> Unit, onLeave: () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        TextButton(onClick = onRename) { Text("Cambiar apodo") }
        TextButton(onClick = onLeave) { Text("Salir de las ligas", color = RupioColors.TextDim) }
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Mi grupo
// ---------------------------------------------------------------------------------------------------------------

/** Separadores de zona ("Suben", "Se mantienen", "Bajan"): con texto, no solo color. */
private val ZoneTitle = mapOf(LeagueZone.UP to "Zona para subir", LeagueZone.STAY to "Se mantienen", LeagueZone.DOWN to "Zona para bajar")

/** Filas y separadores, en orden. Se arma una vez por respuesta del servidor (no en cada recomposición). */
@Immutable private class GroupEntry(val key: String, val row: LeagueRow?, val zone: LeagueZone)

private fun groupEntries(l: League): List<GroupEntry> {
    val out = ArrayList<GroupEntry>(l.rows.size + 3)
    var prev: LeagueZone? = null
    for (r in l.rows) {
        // Sin zona para subir (grupo chico) no hace falta el separador "Se mantienen" arriba.
        if (r.zone != prev && (r.zone != LeagueZone.STAY || prev != null)) out += GroupEntry("zone-${r.zone}", null, r.zone)
        prev = r.zone
        out += GroupEntry(r.key, r, r.zone)
    }
    return out
}

@Composable
private fun GroupList(l: League, unit: WeightUnit, today: LocalDate, state: LazyListState, onRename: () -> Unit, onLeave: () -> Unit) {
    val entries = remember(l) { groupEntries(l) }
    LazyColumn(Modifier.fillMaxSize(), state = state, contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 16.dp)) {
        item(key = "header", contentType = "header") { LeagueHeader(l, today); Spacer(Modifier.height(10.dp)) }
        items(entries, key = { it.key }, contentType = { if (it.row == null) "zone" else "row" }) { e ->
            val row = e.row
            if (row == null) ZoneDivider(e.zone) else LeagueRowItem(row, unit, l.size)
        }
        item(key = "footer", contentType = "footer") { Spacer(Modifier.height(12.dp)); Footer(onRename, onLeave) }
    }
}

@Composable
private fun ZoneDivider(zone: LeagueZone) {
    val (icon, color) = when (zone) {
        LeagueZone.UP -> Icons.Rounded.ArrowUpward to RupioColors.Success
        LeagueZone.DOWN -> Icons.Rounded.ArrowDownward to RupioColors.Danger
        LeagueZone.STAY -> Icons.Rounded.Remove to RupioColors.TextFaint
    }
    Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp).semantics { heading() }, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(ZoneTitle.getValue(zone), style = MaterialTheme.typography.labelLarge, color = color)
    }
}

/**
 * Una fila. Sin Surface (es lo que más cuesta en listas largas): fondo con Modifier solo en tu fila.
 * Los textos se calculan una vez por fila ([remember]), no en cada recomposición.
 */
@Composable
private fun LeagueRowItem(r: LeagueRow, unit: WeightUnit, size: Int) {
    val kg = remember(r.kg, unit) { leagueKg(r.kg, unit) }
    val desc = remember(r, kg) {
        buildString {
            append("Lugar ${r.rank} de $size, ${r.name}"); if (r.me) append(", eres tú")
            append(", $kg, ${r.workouts} ${if (r.workouts == 1) "entreno" else "entrenos"}")
            Metals.ofBadge(r.badge)?.let { append(", insignia ${it.name}") }
        }
    }
    val shape = RoundedCornerShape(12.dp)
    val workouts = "${r.workouts} ${if (r.workouts == 1) "entreno" else "entrenos"}"
    RankRow(
        rank = r.rank, rankColor = if (r.me) RupioColors.Primary else RupioColors.TextDim,
        name = if (r.me) "${r.name} (tú)" else r.name, bold = r.me, badge = r.badge, sub = workouts, kg = kg, desc = desc,
        modifier = if (r.me) Modifier.clip(shape).background(RupioColors.Primary.copy(alpha = 0.12f)).border(1.dp, RupioColors.Primary.copy(alpha = 0.55f), shape) else Modifier,
    )
}

/**
 * Fila de ranking (grupo y global). Con letra grande (> 130 %) los kilos pasan debajo del nombre: en 320 dp al 200 %
 * el apodo se cortaba a 4 letras porque los kilos ocupaban media fila.
 */
@Composable
private fun RankRow(rank: Int, rankColor: Color, name: String, bold: Boolean, badge: Int, sub: String, kg: String, desc: String, modifier: Modifier = Modifier) {
    val big = LocalDensity.current.fontScale > 1.3f
    Row(
        modifier.fillMaxWidth().heightIn(min = 52.dp).semantics(mergeDescendants = true) { contentDescription = desc }.padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$rank", style = MaterialTheme.typography.titleSmall.merge(Tabular), color = rankColor, textAlign = TextAlign.End, modifier = Modifier.widthIn(min = 28.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = if (big) 2 else 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (badge > 0) { Spacer(Modifier.width(6.dp)); MetalDot(badge) }
            }
            if (big) Text(kg, style = MaterialTheme.typography.titleSmall.merge(Tabular))
            Text(sub, style = MaterialTheme.typography.labelSmall, color = RupioColors.TextFaint)
        }
        if (!big) { Spacer(Modifier.width(8.dp)); Text(kg, style = MaterialTheme.typography.titleSmall.merge(Tabular), maxLines = 1) }
    }
}

/** Tu lugar, fijo abajo. Tocarlo te lleva a tu fila. */
@Composable
private fun GroupMeBar(l: League, unit: WeightUnit, list: LazyListState) {
    val me = l.me ?: return
    val scope = rememberCoroutineScope()
    val entries = remember(l) { groupEntries(l) }
    val msg = remember(l, unit) {
        val kgs = l.rows.map { it.kg }
        fun need(target: Int) = LeagueRules.kgToReach(target, me.rank, me.kg, kgs)?.let { leagueKg(it, unit) }
        when (me.zone) {
            LeagueZone.UP -> if (l.tier >= LeagueRules.TIERS) "Vas en zona alta de Leyenda, la liga más alta" else "Vas en zona para subir a ${LeagueRules.tierName(l.tier + 1)}"
            LeagueZone.DOWN -> need(l.size - l.down)?.let { "Te faltan $it para salir de la zona para bajar" } ?: "Zona para bajar"
            LeagueZone.STAY -> if (l.up > 0) need(l.up)?.let { "Te faltan $it para la zona para subir" } ?: "" else need(1)?.let { "Te faltan $it para el primer lugar" } ?: "Vas en primer lugar"
        }
    }
    Surface(color = RupioColors.Surface2, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.clickable { scope.launch { list.animateScrollToItem(1 + entries.indexOfFirst { it.row?.me == true }.coerceAtLeast(0)) } }
            .padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                // Con letra grande sin los kilos (ya están en tu fila): la barra no se come la pantalla.
                val big = LocalDensity.current.fontScale > 1.3f
                Text("Tú · lugar ${me.rank} de ${l.size}" + if (big) "" else " · ${leagueKg(me.kg, unit)}", style = MaterialTheme.typography.titleSmall.merge(Tabular))
                if (msg.isNotBlank()) Text(msg, style = MaterialTheme.typography.bodySmall, color = zoneColor(me.zone))
            }
            Icon(Icons.Rounded.MyLocation, "Ir a tu lugar", tint = RupioColors.TextDim)
        }
    }
}

private fun zoneColor(z: LeagueZone): Color = when (z) { LeagueZone.UP -> RupioColors.Success; LeagueZone.DOWN -> RupioColors.Danger; LeagueZone.STAY -> RupioColors.TextDim }

// ---------------------------------------------------------------------------------------------------------------
// Global
// ---------------------------------------------------------------------------------------------------------------

/** Pide la página siguiente cuando faltan 15 filas para el final (keyset en el servidor). */
private const val PREFETCH = 15

@Composable
private fun GlobalList(state: LeagueState, unit: WeightUnit, list: LazyListState, onLoadMore: () -> Unit) {
    val loadMore by rememberUpdatedState(onLoadMore)
    LaunchedEffect(list) {
        // Se lee el layout en un snapshotFlow (no en la composición): desplazarse no recompone la lista.
        snapshotFlow { val i = list.layoutInfo; ((i.visibleItemsInfo.lastOrNull()?.index ?: -1) >= i.totalItemsCount - PREFETCH) to i.totalItemsCount }
            .distinctUntilChanged().filter { it.first }.collect { loadMore() }
    }
    LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 16.dp)) {
        item(key = "header", contentType = "header") {
            Column(Modifier.padding(bottom = 8.dp)) {
                Text("Top de la semana · todas las ligas", style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                Text("Solo para ver hasta dónde llega la comunidad: subir y bajar de liga se decide en tu grupo.",
                    style = MaterialTheme.typography.bodySmall, color = RupioColors.TextDim)
            }
        }
        items(state.top, key = { it.key }, contentType = { "row" }) { r -> TopRowItem(r, unit) }
        item(key = "footer", contentType = "footer") {
            Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                when {
                    state.topError != null -> ErrorLine(state.topError, onLoadMore)
                    state.topEnd && state.top.isEmpty() -> Text("Nadie ha entrenado esta semana todavía.", color = RupioColors.TextDim)
                    state.topEnd -> Text("Son todas las personas de esta semana.", style = MaterialTheme.typography.labelMedium, color = RupioColors.TextFaint)
                    else -> CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                }
            }
        }
    }
}

@Composable
private fun TopRowItem(r: TopRow, unit: WeightUnit) {
    val kg = remember(r.kg, unit) { leagueKg(r.kg, unit) }
    val tier = LeagueRules.tierName(r.tier)
    val desc = remember(r, kg) { "Lugar ${r.rank}, ${r.name}, liga $tier, $kg" + (Metals.ofBadge(r.badge)?.let { ", insignia ${it.name}" } ?: "") }
    RankRow(r.rank, RupioColors.TextDim, r.name, bold = false, badge = r.badge, sub = "Liga $tier", kg = kg, desc = desc)
}

@Composable
private fun GlobalMeBar(g: LeagueGlobalMe?) {
    g ?: return
    Surface(color = RupioColors.Surface2, modifier = Modifier.fillMaxWidth()) {
        Text("Tú · lugar ${String.format(Fmt.locale, "%,d", g.rank)} de ${String.format(Fmt.locale, "%,d", g.total)} · top ${g.topPct} %",
            style = MaterialTheme.typography.titleSmall.merge(Tabular), modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp))
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Tarjeta "Tu liga" (Progreso)
// ---------------------------------------------------------------------------------------------------------------

/**
 * En Progreso, debajo del volumen. Abre la pestaña Ligas. No sale sin cuenta ni a quien se salió de las ligas
 * (no se insiste). Usa lo guardado: se ve al instante y solo pide al servidor si lo último tiene más de 5 min.
 */
/** ¿Se muestra la tarjeta? Sin cuenta lista o si se salió de las ligas, no. */
fun showLeagueCard(access: WallAccess, l: League?): Boolean = access == WallAccess.READY && l?.hidden != true

@Composable
fun LeagueCardContent(l: League?, unit: WeightUnit, onOpen: () -> Unit) {
    val me = l?.me
    val (title, sub) = when {
        l == null || !l.member -> LeagueCopy.TITLE to "Opcional: compite por volumen con personas de tu nivel"
        me == null -> "Liga ${l.tierName}" to LeagueCopy.NO_WORKOUT
        else -> "Liga ${l.tierName} · lugar ${me.rank} de ${l.size}" to (leagueKg(me.kg, unit) + " esta semana · " + when (me.zone) {
            LeagueZone.UP -> "zona para subir"; LeagueZone.DOWN -> "zona para bajar"; LeagueZone.STAY -> "te mantienes"
        })
    }
    SectionCard(Modifier.clickable(onClick = onOpen)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🏁", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(sub, style = MaterialTheme.typography.bodySmall, color = me?.zone?.takeIf { it != LeagueZone.STAY }?.let(::zoneColor) ?: RupioColors.TextDim)
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = RupioColors.TextFaint)
        }
    }
}
