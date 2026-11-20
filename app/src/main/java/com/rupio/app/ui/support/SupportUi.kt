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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.launch
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.rupio.app.BuildConfig
import com.rupio.app.container
import com.rupio.app.core.tr
import com.rupio.app.data.billing.SupportState
import com.rupio.app.domain.Accent
import com.rupio.app.domain.AppIconStyle
import com.rupio.app.domain.RewardCopy
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
                FilledTonalButton(onClick = onSeeOptions) { Text(tr("Ver cómo apoyar", "See how to support")) }
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
        // v2.1: si ya tienes un nivel y hay mejora, se cobra solo la diferencia (y se dice cuánto costaba completo).
        available.forEach { t -> TierCard(t, state.priceFor(t), selected == t, recommended = t == SupportTier.RECOMMENDED && available.size > 1,
            pending = t in state.pending, fullPrice = state.upgradeFor(t)?.let { state.price(t) }) { onSelect(t) } }
        Text(tr("Pago único con Google Play. Puedes pagar con tarjeta, saldo de Google Play o en efectivo en tiendas como OXXO.",
            "One-time payment through Google Play. You can pay by card, with Google Play balance or in cash at stores like OXXO."),
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
        LoadingButton(SupportCopy.cta(state.priceFor(tier)), loading = state.buying != null, onClick = { onBuy(tier) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), loadingText = tr("Abriendo Google Play…", "Opening Google Play…"))
        if (BuildConfig.DEBUG && onDebugGrant != null) {
            TextButton(onClick = { onDebugGrant(tier) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(tr("Simular compra (solo versión de pruebas)", "Simulate purchase (test build only)"), color = RupioColors.TextFaint, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun TierCard(t: SupportTier, price: String, selected: Boolean, recommended: Boolean, pending: Boolean, fullPrice: String? = null, onClick: () -> Unit) {
    val border = if (selected) BorderStroke(2.dp, RupioColors.Primary) else BorderStroke(1.dp, RupioColors.Outline)
    val metal = Metals.of(t)
    Surface(
        Modifier.fillMaxWidth().selectable(selected, role = Role.RadioButton, onClick = onClick),
        shape = RoundedCornerShape(18.dp), color = if (selected) RupioColors.Primary.copy(alpha = 0.10f) else RupioColors.Surface, border = border,
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // El emoji va sobre el metal del nivel: así se ve de una vez qué insignia y marco te llevas.
                Box(Modifier.size(40.dp).clip(CircleShape).background(metal.brush), contentAlignment = Alignment.Center) {
                    Text(t.emoji, style = MaterialTheme.typography.titleLarge)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(t.title, style = MaterialTheme.typography.titleMedium)
                    Text(metal.name, style = MaterialTheme.typography.labelMedium, color = metal.ink)
                    // v2.1: llena (acento con letra encima). Antes era acento sobre acento al 16 %: 4.07:1 con el azul en
                    // oscuro, debajo de 4.5:1 (WCAG 1.4.3). Llena también se distingue mejor como la opción de en medio.
                    if (recommended) Pill(tr("Recomendado", "Recommended"), RupioColors.Primary, filled = true)
                    if (pending) Pill(tr("Pago pendiente", "Payment pending"), RupioColors.Warning)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(price, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    if (fullPrice != null) Text(tr("en vez de $fullPrice", "instead of $fullPrice"), style = MaterialTheme.typography.labelSmall,
                        color = RupioColors.TextDim, textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough)
                }
                Spacer(Modifier.width(8.dp))
                RadioButton(selected, onClick = null)
            }
            Spacer(Modifier.height(6.dp))
            t.perks.forEach { p -> PerkRow(p) }
        }
    }
}

@Composable
private fun PerkRow(text: String, color: Color = RupioColors.TextDim) {
    Row(Modifier.padding(start = 2.dp, top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Check, null, tint = RupioColors.Success, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = color)
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Gracias
// ---------------------------------------------------------------------------------------------------------------

/**
 * Pantalla de gracias con confeti, la insignia ganada, lo que desbloqueaste (para que quede claro que es por tu pago) y
 * los colores e íconos para elegir de una vez.
 */
@Composable
fun ThanksDialog(tier: SupportTier, state: SupportState, onAccent: (Accent) -> Unit, onIcon: (AppIconStyle) -> Unit, onClose: () -> Unit,
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
                    Text(tr("Ya está en tu perfil. El mensaje de apoyo no volverá a salir.", "It's on your profile now. The support message won't show again."),
                        style = MaterialTheme.typography.bodySmall, color = RupioColors.TextFaint, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
                    Spacer(Modifier.height(18.dp))
                    SectionCard {
                        Text(tr("Lo que desbloqueaste", "What you unlocked"), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        tier.perks.forEach { PerkRow(it, RupioColors.Text) }
                        Spacer(Modifier.height(6.dp))
                        Text(RewardCopy.EXPLAIN, style = MaterialTheme.typography.labelMedium, color = RupioColors.TextDim)
                    }
                    if (tier == SupportTier.MECENAS) {
                        Spacer(Modifier.height(18.dp))
                        Surface(shape = RoundedCornerShape(18.dp), color = Metals.Aurora.ink.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, Metals.Aurora.ink.copy(alpha = 0.6f)), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(if (wallRank != null) tr("¡Ya estás en el muro! Lugar #$wallRank", "You're on the wall! Place #$wallRank") else tr("¡Ya estás en el muro!", "You're on the wall!"),
                                    style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                                Text(tr("Todas las personas que usan Rupio verán tu nombre. Puedes cambiarlo o quitarlo cuando quieras.",
                                    "Everyone who uses Rupio will see your name. You can change it or remove it at any time."),
                                    style = MaterialTheme.typography.bodySmall, color = RupioColors.TextDim, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                                if (onSeeWall != null) FilledTonalButton(onClick = onSeeWall, modifier = Modifier.padding(top = 10.dp)) { Text(tr("Ver el muro", "See the wall")) }
                            }
                        }
                    }
                    if (Accent.entries.count { it.unlockedBy(state.best) } > 1) {
                        Spacer(Modifier.height(24.dp))
                        SectionCard {
                            Text(tr("Elige el color de tu Rupio", "Pick your Rupio color"), style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(10.dp))
                            AccentPicker(state, onAccent)
                            if (AppIconStyle.entries.count { it.unlockedBy(state.best) } > 1) {
                                Spacer(Modifier.height(10.dp)); HorizontalDivider(color = RupioColors.Outline)
                                AppIconPicker(state, onIcon)
                            }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = onClose, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp), shape = RoundedCornerShape(16.dp)) {
                        Text(tr("¡Listo!", "Done!"), style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.height(8.dp))
                    ShareAppButton(Modifier.fillMaxWidth(), text = tr("Compártela con tus amigos del gym", "Share it with your gym friends"))
                }
                Confetti(Modifier.fillMaxSize(), colors = confettiColors(tier))
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

/** v2.0: confeti con los colores del metal (dorado para Fan, aurora para Mecenas); sin nivel, los colores de la app. */
fun confettiColors(tier: SupportTier?): List<Color> = when (tier) {
    null, SupportTier.CAFE, SupportTier.LICUADO -> listOf(RupioColors.Primary, RupioColors.Success, RupioColors.Warning, Color(0xFFFF7AB4), Color(0xFFC59BFF), Color.White)
    else -> Metals.of(tier).stops + Metals.of(tier).inkDark + Color.White
}

/** Confeti que cae una vez (unos 3 s) y desaparece. Solo decoración: no bloquea los toques. */
@Composable
fun Confetti(modifier: Modifier = Modifier, pieces: Int = 90, colors: List<Color> = confettiColors(null)) {
    val parts = remember(colors) {
        val r = Random(7)
        List(pieces) { Piece(r.nextFloat(), -0.1f - r.nextFloat() * 0.5f, (r.nextFloat() - 0.5f) * 0.25f, 0.55f + r.nextFloat() * 0.6f,
            r.nextFloat() * 360f, (r.nextFloat() - 0.5f) * 720f, colors[r.nextInt(colors.size)], 10f + r.nextFloat() * 12f) }
    }
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) { t.animateTo(1f, tween(3200, easing = LinearEasing)) }
    if (t.value >= 1f) return
    Canvas(modifier.semantics { contentDescription = tr("Confeti", "Confetti") }) {
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
// Insignias, marco del avatar, colores e íconos
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

/**
 * v2.0: avatar con el marco del metal de tu nivel (recompensa desde ☕ Café). En Fan y Mecenas el brillo del marco gira
 * despacio. Mide lo mismo que [InitialsAvatar] del mismo tamaño (el marco va por dentro), así que se cambia sin mover
 * nada. Sin nivel es el avatar de siempre. El giro se lee al dibujar: no recompone.
 */
@Composable
fun SupporterAvatar(name: String, size: Dp, tier: SupportTier?, modifier: Modifier = Modifier) {
    if (tier == null) { InitialsAvatar(name, size, modifier); return }
    val metal = Metals.of(tier)
    val ring = (size.value * 0.075f).coerceIn(2.5f, 5f).dp
    val gap = (ring.value * 0.6f).dp
    val sweep = remember(metal) { Brush.sweepGradient(metal.stops + metal.stops.first()) }
    val spin = if (tier.hasGoldPerks) rememberInfiniteTransition(label = "ring")
        .animateFloat(0f, 360f, infiniteRepeatable(tween(7000, easing = LinearEasing)), label = "spin") else null
    // v2.1: en el tema claro el marco Plata (y el borde claro del Bronce y el Oro) casi no se veía sobre las tarjetas
    // blancas (#EEF2F7 sobre #FFFFFF = 1.1:1). Ahí lleva un filo fino del mismo metal oscurecido, como el canto de una
    // moneda: el marco se distingue y conserva su brillo.
    val rim = if (com.rupio.app.ui.theme.Appearance.isDark) null else metal.inkLight.copy(alpha = 0.55f)
    Box(modifier.size(size).drawBehind {
        val w = ring.toPx()
        val r = this.size.minDimension / 2
        rotate(spin?.value ?: 0f) { drawCircle(sweep, radius = r - w / 2, style = Stroke(w)) }
        if (rim != null) {
            val hair = 1.dp.toPx()
            drawCircle(rim, radius = r - hair / 2, style = Stroke(hair))
            drawCircle(rim, radius = r - w + hair / 2, style = Stroke(hair))
        }
    }, contentAlignment = Alignment.Center) {
        InitialsAvatar(name, size - (ring + gap) * 2)
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
    val dark = com.rupio.app.ui.theme.Appearance.isDark
    val cell = pickerCellWidth()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Accent.entries.forEach { a ->
            val unlocked = a.unlockedBy(state.best)
            val color = Color(if (dark) a.argb else a.lightArgb)
            val lockedText = a.minTier?.let { RewardCopy.unlocks(it) }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(cell)
                .selectable(a == current, enabled = unlocked, role = Role.RadioButton) { onPick(a) }
                .semantics { if (!unlocked && lockedText != null) contentDescription = a.label + ". " + lockedText }) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(if (unlocked) color else color.copy(alpha = 0.25f))
                    .border(if (a == current) 3.dp else 0.dp, if (a == current) RupioColors.Text else Color.Transparent, CircleShape),
                    contentAlignment = Alignment.Center) {
                    when {
                        a == current -> Icon(Icons.Rounded.Check, null, tint = RupioColors.OnPrimary)
                        !unlocked -> Icon(Icons.Rounded.Lock, null, tint = RupioColors.TextDim, modifier = Modifier.size(18.dp))
                    }
                }
                PickerLabel(a.label, unlocked)
                if (!unlocked) Text(a.minTier!!.emoji, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/**
 * v2.1: ancho de cada opción de color e ícono. Con letra al 200 % las etiquetas de 60–64 dp se cortaban a "Turq",
 * "Dora" o "Norm" (WCAG 1.4.4); con letra grande la celda crece y la etiqueta usa hasta 2 líneas.
 */
@Composable
private fun pickerCellWidth(): Dp = if (LocalDensity.current.fontScale > 1.3f) 96.dp else 64.dp

@Composable
private fun PickerLabel(text: String, unlocked: Boolean) {
    FitText(text, MaterialTheme.typography.labelMedium, color = if (unlocked) RupioColors.Text else RupioColors.TextFaint,
        textAlign = TextAlign.Center, maxLines = 2, modifier = Modifier.fillMaxWidth())
}

/** v2.0: íconos de la app (antes solo el dorado sí/no). Los bloqueados dicen con qué nivel se abren. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppIconPicker(state: SupportState, onPick: (AppIconStyle) -> Unit) {
    val current = state.effectiveIcon
    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Text(tr("Ícono de la app", "App icon"), style = MaterialTheme.typography.bodyLarge)
        Text(tr("Cambia el ícono de Rupio en tu pantalla de inicio. Puede tardar unos segundos en verse.",
            "Changes Rupio's icon on your home screen. It can take a few seconds to show up."),
            style = MaterialTheme.typography.labelMedium, color = RupioColors.TextDim)
        Spacer(Modifier.height(10.dp))
        val cell = pickerCellWidth()
        FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            AppIconStyle.entries.forEach { s ->
                val unlocked = s.unlockedBy(state.best)
                val lockedText = s.minTier?.let { RewardCopy.unlocks(it) }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(cell)
                    .selectable(s == current, enabled = unlocked, role = Role.RadioButton) { onPick(s) }
                    .semantics { if (!unlocked && lockedText != null) contentDescription = s.label + ". " + lockedText }) {
                    // Anillo de selección por fuera: no tapa el ícono.
                    Box(Modifier.size(58.dp).border(if (s == current) 3.dp else 0.dp, if (s == current) RupioColors.Primary else Color.Transparent, CircleShape),
                        contentAlignment = Alignment.Center) {
                        AppIconPreview(s, 48.dp, Modifier.graphicsLayer { alpha = if (unlocked) 1f else 0.45f })
                        if (!unlocked) Box(Modifier.size(22.dp).align(Alignment.BottomEnd).clip(CircleShape).background(RupioColors.Surface3), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Lock, null, tint = RupioColors.TextDim, modifier = Modifier.size(14.dp))
                        }
                    }
                    PickerLabel(s.label, unlocked)
                    if (!unlocked) Text(s.minTier!!.emoji, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

/**
 * v2.1: el ícono de verdad, con las mismas capas que usa Android (res/mipmap-anydpi-v26/ic_launcher_*.xml). Antes se
 * dibujaba otra mancuerna (la de Material, en diagonal) y el Aurora era un cuadro de color: lo que elegías no se
 * parecía a lo que salía en tu pantalla de inicio. La capa del frente mide 108 dp y el launcher muestra los 72 del
 * centro, así que aquí se dibuja a 1.5× el tamaño y se recorta en círculo, como en la mayoría de los teléfonos.
 */
@Composable
fun AppIconPreview(s: AppIconStyle, size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(CircleShape), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(iconBackground(s)), contentDescription = null,
            modifier = Modifier.requiredSize(size * 1.5f))
        androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(iconForeground(s)), contentDescription = null,
            modifier = Modifier.requiredSize(size * 1.5f))
    }
}

/** Capas de cada ícono (las mismas del manifiesto: res/mipmap-anydpi-v26/ic_launcher_*.xml). */
private fun iconBackground(s: AppIconStyle): Int = when (s) {
    AppIconStyle.NORMAL -> com.rupio.app.R.drawable.ic_launcher_bg
    AppIconStyle.PLATA -> com.rupio.app.R.drawable.ic_launcher_silver_bg
    AppIconStyle.ORO -> com.rupio.app.R.drawable.ic_launcher_gold_bg
    AppIconStyle.AURORA -> com.rupio.app.R.drawable.ic_launcher_aurora_bg
}
private fun iconForeground(s: AppIconStyle): Int = when (s) {
    AppIconStyle.NORMAL -> com.rupio.app.R.drawable.ic_launcher_foreground
    AppIconStyle.PLATA -> com.rupio.app.R.drawable.ic_launcher_silver_foreground
    AppIconStyle.ORO -> com.rupio.app.R.drawable.ic_launcher_gold_foreground
    AppIconStyle.AURORA -> com.rupio.app.R.drawable.ic_launcher_aurora_foreground
}

/**
 * v2.0: "Tus recompensas". Dice qué tienes y POR QUÉ (tu nivel de apoyo): antes la insignia y los colores aparecían sin
 * explicar que eran un "gracias" por pagar.
 */
@Composable
fun MyRewardsCard(state: SupportState, name: String) {
    val best = state.best ?: return
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SupporterAvatar(name, 56.dp, best)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(tr("Tus recompensas", "Your rewards"), style = MaterialTheme.typography.titleMedium)
                Text(RewardCopy.yours(best), style = MaterialTheme.typography.bodySmall, color = RupioColors.TextDim)
            }
        }
        Spacer(Modifier.height(10.dp))
        SupporterBadge(best)
        if (state.owned.size > 1) Text(tr("Niveles: ", "Levels: ") + state.owned.sortedBy { it.rank }.joinToString(" · ") { it.emoji + " " + it.title },
            style = MaterialTheme.typography.labelMedium, color = RupioColors.TextDim, modifier = Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(8.dp))
        // Todo lo que tienes, con la insignia y el marco de tu nivel (no los de los niveles de abajo).
        best.allPerks.forEach { PerkRow(it, RupioColors.Text) }
        Spacer(Modifier.height(6.dp))
        Text(RewardCopy.EXPLAIN, style = MaterialTheme.typography.labelMedium, color = RupioColors.TextFaint)
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Pantalla "Apoya Rupio": pestañas Apoyar · Muro · Ligas (v1.9)
// ---------------------------------------------------------------------------------------------------------------

/** Pestañas de Apoya Rupio. [id] va en la ruta (support?tab=muro). */
enum class SupportTab(val id: String) {
    APOYAR("apoyar"), MURO("muro"), LIGAS("ligas");

    val label: String get() = when (this) { APOYAR -> tr("Apoyar", "Support"); MURO -> tr("Muro", "Wall"); LIGAS -> tr("Ligas", "Leagues") }

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
            RupioTopBar(tr("Apoya Rupio", "Support Rupio"), onBack = { nav.safePop() }) {
                if ((tab == SupportTab.MURO || (tab == SupportTab.LIGAS && access == WallAccess.READY))) IconButton(onClick = {
                    scope.launch {
                        if (tab == SupportTab.MURO) { if (access == WallAccess.READY) c.wall.load() else c.wall.loadPublic(force = true) }
                        else { c.league.load(force = true); if (leagueView == LeagueView.GLOBAL) c.league.loadTop(reset = true) }
                    }
                }) { Icon(Icons.Rounded.Refresh, tr("Actualizar", "Refresh")) }
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
 * Pestaña "Apoyar". Orden: quién hace la app → (tus recompensas) → qué son las recompensas → quiénes ya apoyan (muro)
 * → niveles → pagar → compartir (apoyar sin pagar) → restaurar compras (utilidad, al final).
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
        title = tr("Ser Mecenas · ${state.price(SupportTier.MECENAS)}", "Become a patron · ${state.price(SupportTier.MECENAS)}"),
        intro = tr("Escribe cómo quieres aparecer. Después de pagar entras al muro con este nombre.", "Enter how you want to appear. After paying, you join the wall with this name."),
        initial = wall.me?.name ?: signed?.name.orEmpty(), confirm = tr("Continuar al pago", "Continue to payment"), askConsent = true,
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
                Text(SupportCopy.ABOUT + " " + SupportCopy.ASK, style = MaterialTheme.typography.bodyMedium, color = RupioColors.TextDim)
                Spacer(Modifier.height(6.dp))
                Text(SupportCopy.FREEDOM, style = MaterialTheme.typography.bodySmall, color = RupioColors.TextFaint)
            }
        } else {
            MyRewardsCard(state, signed?.name.orEmpty())
            if (best == SupportTier.MECENAS) WallSummaryCard(wall.me, onOpenWall)
            SectionCard {
                Text(tr("Color de la app", "App color"), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                AccentPicker(state) { c.support.setAccent(it) }
                if (AppIconStyle.entries.count { it.unlockedBy(best) } > 1) {
                    Spacer(Modifier.height(6.dp)); HorizontalDivider(color = RupioColors.Outline); AppIconPicker(state) { c.support.setAppIcon(it) }
                }
            }
        }
        // v2.0: quiénes ya apoyan, a la vista antes de los niveles (antes el muro solo se veía en su pestaña).
        if (best != SupportTier.MECENAS) WallPreviewCard(wall, wallAccess(auth), onOpen = onOpenWall)
        if (best != SupportTier.MECENAS) {
            Column {
                Text(if (best == null) RewardCopy.TITLE else if (best == SupportTier.FAN) tr("¿Quieres aparecer en el muro de agradecimientos? Hazte Mecenas:", "Want to be on the thank-you wall? Become a patron:")
                    else tr("¿Quieres más recompensas? Puedes subir de nivel:", "Want more rewards? You can level up:"), style = MaterialTheme.typography.titleSmall)
                // v2.1: quien ya pagó un nivel podía creer que "subir de nivel" cobra solo la diferencia.
                val diff = best != null && availableTiers(state).any { state.upgradeFor(it) != null }
                Text(when { best == null -> RewardCopy.EXPLAIN; diff -> RewardCopy.upgradeDiff(best!!); else -> RewardCopy.UPGRADE_NOTE },
                    style = MaterialTheme.typography.bodySmall, color = RupioColors.TextDim, modifier = Modifier.padding(top = 2.dp))
            }
        }
        if (state.pending.isNotEmpty()) Surface(color = RupioColors.Warning.copy(alpha = 0.12f), shape = RoundedCornerShape(12.dp)) {
            Text(tr("Tienes un pago pendiente (${state.pending.joinToString { it.title }}). Cuando lo completes se activa solo.",
                "You have a pending payment (${state.pending.joinToString { it.title }}). It turns on by itself once you complete it."),
                modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
        }
        var selected by rememberSaveable(state.best) { mutableStateOf(defaultTier(state)) }
        SupportTiers(state, selected) { selected = it }
        SupportBuyButton(state, selected, onBuy = onBuy, onDebugGrant = { c.support.debugGrant(it) })
        // Una forma de apoyar sin pagar: que más gente la conozca.
        SectionCard {
            Text(if (best == null) tr("¿Ahora no puedes? También ayuda muchísimo que la compartas 🙌", "Can't right now? Sharing it helps a lot too 🙌")
                else tr("Que más gente la conozca también ayuda muchísimo 🙌", "Getting more people to know it helps a lot too 🙌"),
                style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(10.dp))
            ShareAppButton(Modifier.fillMaxWidth())
        }
        TextButton(onClick = { c.support.refresh() }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Icon(Icons.Rounded.Restore, null); Spacer(Modifier.width(6.dp)); Text(tr("Restaurar compras", "Restore purchases"))
        }
        Text(tr("Las compras quedan en tu cuenta de Google: si cambias de teléfono o reinstalas, se recuperan solas.",
            "Purchases stay in your Google account: if you change phones or reinstall, they come back on their own."),
            style = MaterialTheme.typography.labelMedium, color = RupioColors.TextFaint)
        if (BuildConfig.DEBUG && state.owned.isNotEmpty()) TextButton(onClick = { c.support.debugReset() }) {
            Text(tr("Quitar compras simuladas (solo pruebas)", "Remove simulated purchases (test only)"), color = RupioColors.TextFaint)
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
                Text(com.rupio.app.domain.WallCopy.TITLE, style = MaterialTheme.typography.titleMedium)
                Text(when {
                    me == null -> tr("Toca para ver tu lugar", "Tap to see your place")
                    me.hidden -> tr("Tu nombre está oculto", "Your name is hidden")
                    me.rank != null && me.toNext != null && me.rank > 1 -> tr("Lugar #${me.rank} · te faltan ${me.toNext} puntos para subir", "Place #${me.rank} · ${me.toNext} points to move up")
                    me.rank != null -> tr("Lugar #${me.rank}", "Place #${me.rank}")
                    else -> tr("${me.points} puntos", "${me.points} points")
                }, style = MaterialTheme.typography.bodySmall, color = RupioColors.TextDim)
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = RupioColors.TextFaint)
        }
    }
}
