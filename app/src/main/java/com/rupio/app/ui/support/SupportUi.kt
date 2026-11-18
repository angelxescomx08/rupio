package com.rupio.app.ui.support

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.launch
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.rupio.app.BuildConfig
import com.rupio.app.container
import com.rupio.app.data.billing.SupportState
import com.rupio.app.domain.Accent
import com.rupio.app.domain.SupportCopy
import com.rupio.app.domain.SupportTier
import com.rupio.app.domain.WallBoost
import com.rupio.app.Routes
import com.rupio.app.safePop
import com.rupio.app.ui.components.*
import com.rupio.app.ui.theme.Metals
import com.rupio.app.ui.theme.RupioColors
import kotlin.math.sin
import kotlin.random.Random

fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) { if (c is Activity) return c; c = c.baseContext }
    return null
}

// ---------------------------------------------------------------------------------------------------------------
// Mensaje al terminar un entrenamiento
// ---------------------------------------------------------------------------------------------------------------

/**
 * Mensaje "Apoya Rupio" como TARJETA dentro del resumen del entreno (antes era un diálogo a pantalla completa):
 * no es urgente ni pide una decisión, así que se puede leer o ignorar. Solo sale en hitos (ver SupportPolicy).
 */
@Composable
fun SupportPromptCard(state: SupportState, workouts: Int, lifted: String, onSeeOptions: () -> Unit, onNotNow: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = RupioColors.Primary.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, RupioColors.Primary.copy(alpha = 0.35f))) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                InitialsAvatar(SupportCopy.DEV_NAME, 40.dp)
                Spacer(Modifier.width(10.dp))
                Text(SupportCopy.TITLE, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(10.dp))
            Text(SupportCopy.story(workouts, lifted), style = MaterialTheme.typography.bodyMedium, color = RupioColors.Text)
            Spacer(Modifier.height(6.dp))
            Text(SupportCopy.ASK, style = MaterialTheme.typography.bodyMedium, color = RupioColors.TextDim)
            Spacer(Modifier.height(6.dp))
            Text(SupportCopy.FREEDOM, style = MaterialTheme.typography.bodySmall, color = RupioColors.TextDim)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onNotNow) { Text(SupportCopy.NOT_NOW, color = RupioColors.TextDim) }
                Spacer(Modifier.width(4.dp))
                FilledTonalButton(onClick = onSeeOptions) { Text("Ver cómo apoyar") }
            }
        }
    }
}

/** Niveles que todavía se pueden comprar (los de arriba del que ya tiene). */
fun availableTiers(state: SupportState): List<SupportTier> =
    SupportTier.entries.filter { it !in state.owned && (state.best == null || it.rank > state.best!!.rank) }

fun defaultTier(state: SupportState): SupportTier? = availableTiers(state).let { a -> if (SupportTier.RECOMMENDED in a) SupportTier.RECOMMENDED else a.firstOrNull() }

/** Tarjetas de los niveles (se elige uno). */
@Composable
fun SupportTiers(state: SupportState, selected: SupportTier?, onSelect: (SupportTier) -> Unit) {
    val available = availableTiers(state)
    if (available.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        available.forEach { t -> TierCard(t, state.price(t), selected == t, recommended = t == SupportTier.RECOMMENDED && available.size > 1,
            pending = t in state.pending) { onSelect(t) } }
        Text("Pago único con Google Play. Puedes pagar con tarjeta, saldo de Google Play o en efectivo en tiendas como OXXO.",
            style = MaterialTheme.typography.labelMedium, color = RupioColors.TextFaint)
    }
}

/** Botón de pagar (con aviso si algo salió mal) y, solo en la versión de pruebas, "simular compra". */
@Composable
fun SupportBuyButton(state: SupportState, selected: SupportTier?, onBuy: (SupportTier) -> Unit, onDebugGrant: ((SupportTier) -> Unit)?) {
    val tier = selected?.takeIf { it in availableTiers(state) } ?: return
    Column(Modifier.fillMaxWidth()) {
        state.message?.let { msg ->
            Surface(color = RupioColors.Surface2, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Info, null, tint = RupioColors.Primary); Spacer(Modifier.width(8.dp))
                    Text(msg, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        LoadingButton(SupportCopy.cta(state.price(tier)), loading = state.buying != null, onClick = { onBuy(tier) },
            modifier = Modifier.fillMaxWidth().height(56.dp), loadingText = "Abriendo Google Play…")
        if (BuildConfig.DEBUG && onDebugGrant != null) {
            TextButton(onClick = { onDebugGrant(tier) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Simular compra (solo versión de pruebas)", color = RupioColors.TextFaint, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun TierCard(t: SupportTier, price: String, selected: Boolean, recommended: Boolean, pending: Boolean, onClick: () -> Unit) {
    val border = if (selected) BorderStroke(2.dp, RupioColors.Primary) else BorderStroke(1.dp, RupioColors.Outline)
    Surface(
        Modifier.fillMaxWidth().selectable(selected, role = Role.RadioButton, onClick = onClick),
        shape = RoundedCornerShape(18.dp), color = if (selected) RupioColors.Primary.copy(alpha = 0.10f) else RupioColors.Surface, border = border,
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t.emoji, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(t.title, style = MaterialTheme.typography.titleMedium)
                    if (recommended) Pill("Recomendado", RupioColors.Primary)
                    if (pending) Pill("Pago pendiente", RupioColors.Warning)
                }
                Text(price, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                RadioButton(selected, onClick = null)
            }
            Spacer(Modifier.height(6.dp))
            t.perks.forEach { p ->
                Row(Modifier.padding(start = 2.dp, top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Check, null, tint = RupioColors.Success, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(p, style = MaterialTheme.typography.bodyMedium, color = RupioColors.TextDim)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Gracias
// ---------------------------------------------------------------------------------------------------------------

/** Pantalla de gracias con confeti, la insignia ganada y (si aplica) los colores para elegir de una vez. */
@Composable
fun ThanksDialog(tier: SupportTier, state: SupportState, onAccent: (Accent) -> Unit, onGoldIcon: (Boolean) -> Unit, onClose: () -> Unit,
                 wallRank: Int? = null, onSeeWall: (() -> Unit)? = null) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = RupioColors.Background) {
            Box(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(36.dp))
                    BigBadge(tier)
                    Spacer(Modifier.height(20.dp))
                    Text(SupportCopy.THANKS_TITLE, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(10.dp))
                    Text(SupportCopy.THANKS_BODY, style = MaterialTheme.typography.bodyLarge, color = RupioColors.TextDim, textAlign = TextAlign.Center)
                    Text(SupportCopy.THANKS_SIGN, style = MaterialTheme.typography.titleMedium, color = RupioColors.Text, modifier = Modifier.padding(top = 6.dp))
                    Spacer(Modifier.height(18.dp))
                    SupporterBadge(tier)
                    Text("Ya está en tu perfil. El mensaje de apoyo no volverá a salir.", style = MaterialTheme.typography.bodySmall,
                        color = RupioColors.TextFaint, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
                    if (tier == SupportTier.MECENAS) {
                        Spacer(Modifier.height(18.dp))
                        Surface(shape = RoundedCornerShape(18.dp), color = Metals.Aurora.ink.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, Metals.Aurora.ink.copy(alpha = 0.6f)), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(if (wallRank != null) "¡Ya estás en el muro! Lugar #$wallRank" else "¡Ya estás en el muro!",
                                    style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                                Text("Todas las personas que usan Rupio verán tu nombre. Puedes cambiarlo o quitarlo cuando quieras.",
                                    style = MaterialTheme.typography.bodySmall, color = RupioColors.TextDim, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                                if (onSeeWall != null) FilledTonalButton(onClick = onSeeWall, modifier = Modifier.padding(top = 10.dp)) { Text("Ver el muro") }
                            }
                        }
                    }
                    if (Accent.entries.count { it.unlockedBy(state.best) } > 1) {
                        Spacer(Modifier.height(24.dp))
                        SectionCard {
                            Text("Elige el color de tu Rupio", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(10.dp))
                            AccentPicker(state, onAccent)
                            if (state.best?.hasGoldPerks == true) {
                                Spacer(Modifier.height(10.dp)); HorizontalDivider(color = RupioColors.Outline)
                                GoldIconRow(state.goldIcon, onGoldIcon)
                            }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = onClose, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp)) {
                        Text("¡Listo!", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.height(8.dp))
                    ShareAppButton(Modifier.fillMaxWidth(), text = "Compártela con tus amigos del gym")
                }
                Confetti(Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun BigBadge(tier: SupportTier) {
    val inf = rememberInfiniteTransition(label = "badge")
    val pulse = inf.animateFloat(1f, 1.06f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse")
    // La escala se lee en la capa gráfica: la animación no recompone la pantalla en cada cuadro.
    Box(Modifier.size(120.dp).graphicsLayer { scaleX = pulse.value; scaleY = pulse.value }.clip(CircleShape).background(tierBrush(tier)),
        contentAlignment = Alignment.Center) {
        Text(tier.emoji, style = MaterialTheme.typography.displaySmall)
    }
}

/** Degradado del metal del nivel (Bronce, Plata, Oro, Aurora). */
private fun tierBrush(t: SupportTier): Brush = Metals.of(t).brush

/** Confeti que cae una vez (unos 3 s) y desaparece. Solo decoración: no bloquea los toques. */
@Composable
fun Confetti(modifier: Modifier = Modifier, pieces: Int = 90) {
    val colors = listOf(RupioColors.Primary, RupioColors.Success, RupioColors.Warning, Color(0xFFFF7AB4), Color(0xFFC59BFF), Color.White)
    val parts = remember {
        val r = Random(7)
        List(pieces) { Piece(r.nextFloat(), -0.1f - r.nextFloat() * 0.5f, (r.nextFloat() - 0.5f) * 0.25f, 0.55f + r.nextFloat() * 0.6f,
            r.nextFloat() * 360f, (r.nextFloat() - 0.5f) * 720f, colors[r.nextInt(colors.size)], 10f + r.nextFloat() * 12f) }
    }
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) { t.animateTo(1f, tween(3200, easing = LinearEasing)) }
    if (t.value >= 1f) return
    Canvas(modifier.semantics { contentDescription = "Confeti" }) {
        val p = t.value
        for (q in parts) {
            val y = (q.y0 + q.speed * p * 1.6f + 0.35f * p * p) * size.height
            val x = (q.x0 + q.drift * p + 0.02f * sin(p * 10f + q.x0 * 6f)) * size.width
            if (y < -20f || y > size.height + 20f) continue
            rotate(q.rot0 + q.spin * p, Offset(x, y)) {
                drawRect(q.color.copy(alpha = (1.2f - p).coerceIn(0f, 1f)), Offset(x - q.w / 2, y - q.w / 4), Size(q.w, q.w / 2))
            }
        }
    }
}

private data class Piece(val x0: Float, val y0: Float, val drift: Float, val speed: Float, val rot0: Float, val spin: Float, val color: Color, val w: Float)

// ---------------------------------------------------------------------------------------------------------------
// Insignias, colores e ícono
// ---------------------------------------------------------------------------------------------------------------

private val Shine = listOf(Color.Transparent, Color.White.copy(alpha = 0.55f), Color.Transparent)

/**
 * Insignia junto al nombre, con el metal del nivel. Oro y Aurora brillan (un reflejo que cruza cada pocos segundos).
 * El reflejo y el giro del emoji se leen en la fase de dibujo/capa: la insignia no se recompone en cada cuadro
 * (antes, la de Fan recomponía ~60 veces por segundo mientras Perfil estaba abierto).
 */
@Composable
fun SupporterBadge(tier: SupportTier, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    val metal = Metals.of(tier)
    if (tier.hasGoldPerks) {
        val inf = rememberInfiniteTransition(label = "metal")
        val x = inf.animateFloat(-1f, 2f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "shine")
        val wobble = inf.animateFloat(-8f, 8f, infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "wobble")
        Box(modifier.clip(shape).background(metal.brush)
            .drawBehind {
                val px = x.value * 300f
                drawRect(Brush.linearGradient(Shine, start = Offset(px, 0f), end = Offset(px + 120f, 60f)))
            }
            .padding(horizontal = 12.dp, vertical = 5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tier.emoji, modifier = Modifier.graphicsLayer { rotationZ = wobble.value })
                Spacer(Modifier.width(6.dp))
                Text(tier.badge, color = metal.on, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
        }
    } else {
        Row(modifier.clip(shape).background(metal.container).border(1.dp, metal.ink.copy(alpha = 0.5f), shape).padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(tier.emoji); Spacer(Modifier.width(6.dp))
            Text(tier.badge, color = metal.ink, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Puntito del metal (filas de las ligas). Null = sin insignia. */
@Composable
fun MetalDot(badge: Int, modifier: Modifier = Modifier) {
    val m = Metals.ofBadge(badge) ?: return
    Box(modifier.size(14.dp).clip(CircleShape).background(m.brush))    // la fila ya dice "insignia Oro" al lector de pantalla
}

/** Círculos de color. Los que aún no se tienen muestran un candado y qué nivel los abre. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccentPicker(state: SupportState, onPick: (Accent) -> Unit) {
    val current = state.effectiveAccent
    FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Accent.entries.forEach { a ->
            val unlocked = a.unlockedBy(state.best)
            val color = Color(a.argb)
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(60.dp)
                .selectable(a == current, enabled = unlocked, role = Role.RadioButton) { onPick(a) }) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(if (unlocked) color else color.copy(alpha = 0.25f))
                    .border(if (a == current) 3.dp else 0.dp, if (a == current) Color.White else Color.Transparent, CircleShape),
                    contentAlignment = Alignment.Center) {
                    when {
                        a == current -> Icon(Icons.Rounded.Check, null, tint = RupioColors.OnPrimary)
                        !unlocked -> Icon(Icons.Rounded.Lock, null, tint = RupioColors.TextDim, modifier = Modifier.size(18.dp))
                    }
                }
                Text(a.label, style = MaterialTheme.typography.labelMedium, color = if (unlocked) RupioColors.Text else RupioColors.TextFaint, maxLines = 1)
                if (!unlocked) Text(a.minTier!!.emoji, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun GoldIconRow(on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF17110A)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.FitnessCenter, null, tint = Metals.Oro.ink)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Ícono dorado", style = MaterialTheme.typography.bodyLarge)
            Text("Cambia el ícono de Rupio en tu pantalla de inicio. Puede tardar unos segundos en verse.",
                style = MaterialTheme.typography.labelMedium, color = RupioColors.TextDim)
        }
        Switch(on, onChange)
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Pantalla "Apoya Rupio": pestañas Apoyar · Muro · Ligas (v1.9)
// ---------------------------------------------------------------------------------------------------------------

/** Pestañas de Apoya Rupio. [id] va en la ruta (support?tab=muro). */
enum class SupportTab(val id: String, val label: String) {
    APOYAR("apoyar", "Apoyar"), MURO("muro", "Muro"), LIGAS("ligas", "Ligas");

    companion object {
        fun of(id: String?): SupportTab = entries.firstOrNull { it.id == id } ?: APOYAR
    }
}

/**
 * Un solo lugar para apoyar y competir. Abre en "Apoyar" (lo principal); "Tu liga" de Progreso abre directo en
 * "Ligas". Solo la pestaña visible está compuesta, y cada una conserva su desplazamiento al ir y volver
 * (los estados de las listas viven aquí).
 */
@Composable
fun SupportScreen(nav: NavHostController, initialTab: SupportTab = SupportTab.APOYAR) {
    val c = container()
    val auth by c.auth.state.collectAsStateWithLifecycle()
    val access = wallAccess(auth)
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableStateOf(initialTab) }
    // "Ver el muro" con esta pantalla ya abierta llega con otra pestaña; al girar el teléfono se respeta la elegida.
    var lastInitial by rememberSaveable { mutableStateOf(initialTab) }
    LaunchedEffect(initialTab) { if (initialTab != lastInitial) { lastInitial = initialTab; tab = initialTab } }
    var leagueView by rememberSaveable { mutableStateOf(LeagueView.GROUP) }
    val supportScroll = rememberScrollState()
    val wallList = rememberLazyListState()
    val groupList = rememberLazyListState()
    val globalList = rememberLazyListState()
    LaunchedEffect(Unit) { c.support.refresh() }
    DisposableEffect(Unit) { onDispose { c.support.clearMessage() } }

    Scaffold(containerColor = RupioColors.Background, snackbarHost = { SnackbarHost(snackbar) }, topBar = {
        Column {
            RupioTopBar("Apoya Rupio", onBack = { nav.safePop() }) {
                if (tab != SupportTab.APOYAR && access == WallAccess.READY) IconButton(onClick = {
                    scope.launch {
                        if (tab == SupportTab.MURO) c.wall.load()
                        else { c.league.load(force = true); if (leagueView == LeagueView.GLOBAL) c.league.loadTop(reset = true) }
                    }
                }) { Icon(Icons.Rounded.Refresh, "Actualizar") }
            }
            SupportTabs(tab) { tab = it }
        }
    }) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                SupportTab.APOYAR -> SupportTabContent(nav, supportScroll, onOpenWall = { tab = SupportTab.MURO })
                SupportTab.MURO -> WallTab(nav, snackbar, wallList)
                SupportTab.LIGAS -> LeagueTab(nav, snackbar, access, leagueView, { leagueView = it }, groupList, globalList)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SupportTabs(tab: SupportTab, onTab: (SupportTab) -> Unit) {
    // Con letra muy grande, las pestañas se desplazan en vez de cortar el texto.
    val big = LocalDensity.current.fontScale > 1.3f
    val tabs: @Composable () -> Unit = {
        SupportTab.entries.forEach { t ->
            Tab(selected = tab == t, onClick = { onTab(t) },
                text = { Text(t.label, style = MaterialTheme.typography.titleSmall, maxLines = 1, softWrap = false) },
                selectedContentColor = RupioColors.Primary, unselectedContentColor = RupioColors.TextDim)
        }
    }
    val divider: @Composable () -> Unit = { HorizontalDivider(color = RupioColors.Outline) }
    if (big) PrimaryScrollableTabRow(tab.ordinal, containerColor = RupioColors.Background, edgePadding = 8.dp, divider = divider) { tabs() }
    else PrimaryTabRow(tab.ordinal, containerColor = RupioColors.Background, divider = divider) { tabs() }
}

/**
 * Pestaña "Apoyar". Orden: quién hace la app → (tus recompensas) → niveles → pagar → compartir (apoyar sin pagar)
 * → restaurar compras (utilidad, al final).
 */
@Composable
private fun SupportTabContent(nav: NavHostController, scroll: ScrollState, onOpenWall: () -> Unit) {
    val c = container()
    val state by c.support.state.collectAsStateWithLifecycle()
    val wall by c.wall.state.collectAsStateWithLifecycle()
    val auth by c.auth.state.collectAsStateWithLifecycle()
    val activity = LocalContext.current.findActivity()
    val best = state.best
    val signed = auth as? com.rupio.app.data.auth.AuthState.SignedIn
    val wallReady = signed != null && !signed.localOnly && !signed.needsVerification
    var askName by remember { mutableStateOf(false) }
    var askAccount by remember { mutableStateOf(false) }
    // Mecenas: primero el nombre y el permiso para publicarlo; luego Google Play.
    val onBuy: (SupportTier) -> Unit = { t ->
        if (t != SupportTier.MECENAS) activity?.let { c.support.buy(it, t) }
        else if (!wallReady) askAccount = true
        else askName = true
    }
    if (askName) WallNameDialog(
        title = "Ser Mecenas · ${state.price(SupportTier.MECENAS)}",
        intro = "Escribe cómo quieres aparecer. Después de pagar entras al muro con este nombre.",
        initial = wall.me?.name ?: signed?.name.orEmpty(), confirm = "Continuar al pago", askConsent = true,
        onDismiss = { askName = false },
    ) { name -> askName = false; activity?.let { c.support.buyWall(it, WallBoost.BIG, name) } }
    if (askAccount) WallAccountDialog(signed?.localOnly == true, onCreate = { askAccount = false; nav.navigate(Routes.ACCOUNT) }, onDismiss = { askAccount = false })

    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Spacer(Modifier.height(2.dp))
        if (best == null) {
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    InitialsAvatar(SupportCopy.DEV_NAME, 48.dp); Spacer(Modifier.width(12.dp))
                    Text(SupportCopy.TITLE, style = MaterialTheme.typography.titleLarge)
                }
                Spacer(Modifier.height(10.dp))
                Text("Hago Rupio yo solo, en mis ratos libres. " + SupportCopy.ASK, style = MaterialTheme.typography.bodyMedium, color = RupioColors.TextDim)
                Spacer(Modifier.height(6.dp))
                Text(SupportCopy.FREEDOM, style = MaterialTheme.typography.bodySmall, color = RupioColors.TextFaint)
            }
        } else {
            SectionCard {
                Text("Tus recompensas", style = MaterialTheme.typography.titleMedium)
                Text("Gracias por apoyar Rupio 💙", style = MaterialTheme.typography.bodyMedium, color = RupioColors.TextDim)
                Spacer(Modifier.height(10.dp))
                SupporterBadge(best)
                if (state.owned.size > 1) Text("Niveles: " + state.owned.sortedBy { it.rank }.joinToString(" · ") { it.emoji + " " + it.title },
                    style = MaterialTheme.typography.labelMedium, color = RupioColors.TextDim, modifier = Modifier.padding(top = 8.dp))
            }
            if (best == SupportTier.MECENAS) WallSummaryCard(wall.me, onOpenWall)
            SectionCard {
                Text("Color de la app", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                AccentPicker(state) { c.support.setAccent(it) }
                if (best.hasGoldPerks) { Spacer(Modifier.height(6.dp)); HorizontalDivider(color = RupioColors.Outline); GoldIconRow(state.goldIcon) { c.support.setGoldIcon(it) } }
            }
            when (best) {
                SupportTier.MECENAS -> {}
                SupportTier.FAN -> Text("¿Quieres aparecer en el muro de agradecimientos? Hazte Mecenas:", style = MaterialTheme.typography.titleSmall)
                else -> Text("¿Quieres más adornos? Puedes subir de nivel:", style = MaterialTheme.typography.titleSmall)
            }
        }
        if (state.pending.isNotEmpty()) Surface(color = RupioColors.Warning.copy(alpha = 0.12f), shape = RoundedCornerShape(12.dp)) {
            Text("Tienes un pago pendiente (${state.pending.joinToString { it.title }}). Cuando lo completes se activa solo.",
                modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
        }
        var selected by rememberSaveable(state.best) { mutableStateOf(defaultTier(state)) }
        SupportTiers(state, selected) { selected = it }
        SupportBuyButton(state, selected, onBuy = onBuy, onDebugGrant = { c.support.debugGrant(it) })
        // Una forma de apoyar sin pagar: que más gente la conozca.
        SectionCard {
            Text(if (best == null) "¿Ahora no puedes? También ayuda muchísimo que la compartas 🙌" else "Que más gente la conozca también ayuda muchísimo 🙌",
                style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(10.dp))
            ShareAppButton(Modifier.fillMaxWidth())
        }
        TextButton(onClick = { c.support.refresh() }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Icon(Icons.Rounded.Restore, null); Spacer(Modifier.width(6.dp)); Text("Restaurar compras")
        }
        Text("Las compras quedan en tu cuenta de Google: si cambias de teléfono o reinstalas, se recuperan solas.",
            style = MaterialTheme.typography.labelMedium, color = RupioColors.TextFaint)
        if (BuildConfig.DEBUG && state.owned.isNotEmpty()) TextButton(onClick = { c.support.debugReset() }) {
            Text("Quitar compras simuladas (solo pruebas)", color = RupioColors.TextFaint)
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** En "Tus recompensas" de un Mecenas: su lugar y un atajo al muro. */
@Composable
fun WallSummaryCard(me: com.rupio.app.data.billing.WallMe?, onOpen: () -> Unit) {
    Surface(onClick = onOpen, shape = RoundedCornerShape(18.dp), color = Metals.Aurora.ink.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, Metals.Aurora.ink.copy(alpha = 0.6f)), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(MecenasBrush), contentAlignment = Alignment.Center) {
                Text(me?.rank?.takeIf { me.hidden.not() }?.let { "#$it" } ?: "🌟", color = Metals.Aurora.on, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Muro de agradecimientos", style = MaterialTheme.typography.titleMedium)
                Text(when {
                    me == null -> "Toca para ver tu lugar"
                    me.hidden -> "Tu nombre está oculto"
                    me.rank != null && me.toNext != null && me.rank > 1 -> "Lugar #${me.rank} · te faltan ${me.toNext} puntos para subir"
                    me.rank != null -> "Lugar #${me.rank}"
                    else -> "${me.points} puntos"
                }, style = MaterialTheme.typography.bodySmall, color = RupioColors.TextDim)
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = RupioColors.TextFaint)
        }
    }
}
