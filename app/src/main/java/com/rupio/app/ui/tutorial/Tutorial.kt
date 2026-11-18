package com.rupio.app.ui.tutorial

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.rupio.app.ui.components.SupersetTag
import com.rupio.app.ui.theme.RupioColors
import com.rupio.app.ui.theme.Tabular
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// =====================================================================================
// Coach marks: oscurece la pantalla y "recorta" un hueco sobre el botón real que explica.
// =====================================================================================
data class CoachStep(val key: String, val title: String, val body: String)

class CoachController {
    val targets = mutableStateMapOf<String, Rect>()
    /** Esquina del overlay en coordenadas de la ventana (los objetivos se miden en esas coordenadas). */
    var hostOffset by mutableStateOf(Offset.Zero)
    var steps by mutableStateOf<List<CoachStep>>(emptyList()); private set
    var index by mutableIntStateOf(0); private set
    private var onDone: (() -> Unit)? = null

    val active get() = steps.isNotEmpty()
    fun start(steps: List<CoachStep>, onDone: () -> Unit) { this.steps = steps; index = 0; this.onDone = onDone }
    fun next() { if (index < steps.lastIndex) index++ else finish() }
    fun finish() { steps = emptyList(); index = 0; onDone?.invoke(); onDone = null }
}

val LocalCoach = staticCompositionLocalOf { CoachController() }

fun Modifier.coachTarget(key: String): Modifier = composed {
    val c = LocalCoach.current
    onGloballyPositioned { c.targets[key] = it.boundsInRoot() }
}

@Composable
fun CoachHost(controller: CoachController, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalCoach provides controller) {
        Box(Modifier.fillMaxSize().onGloballyPositioned { controller.hostOffset = it.boundsInRoot().topLeft }) {
            content()
            AnimatedVisibility(controller.active, enter = fadeIn(), exit = fadeOut()) { CoachOverlay(controller) }
        }
    }
}

@Composable
private fun CoachOverlay(c: CoachController) {
    val step = c.steps.getOrNull(c.index) ?: return
    // Los botones se miden respecto a la ventana; el overlay empieza debajo de la barra de estado.
    val target = c.targets[step.key]?.translate(-c.hostOffset)
    val density = LocalDensity.current
    val pad = with(density) { 8.dp.toPx() }
    val pulse by animateFloatAsState(if (c.index % 2 == 0) 1f else 1.0001f, label = "p")
    BoxWithConstraints(
        Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, null) { c.next() },
    ) {
        val hPx = with(density) { maxHeight.toPx() }
        Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
            drawRect(Color.Black.copy(alpha = 0.78f))
            if (target != null) {
                val r = Rect(target.left - pad, target.top - pad, target.right + pad, target.bottom + pad)
                drawRoundRect(Color.Transparent, Offset(r.left, r.top), Size(r.width, r.height), CornerRadius(18.dp.toPx() * pulse), blendMode = BlendMode.Clear)
                drawRoundRect(RupioColors.Primary, Offset(r.left, r.top), Size(r.width, r.height), CornerRadius(18.dp.toPx()), style = Stroke(2.dp.toPx()))
            }
        }
        val below = target == null || target.center.y < hPx * 0.55f
        val yPx = when {
            target == null -> hPx * 0.35f
            below -> target.bottom + pad * 3
            else -> target.top - pad * 3 - with(density) { 190.dp.toPx() }
        }
        Surface(
            Modifier.padding(horizontal = 20.dp).fillMaxWidth().offset { IntOffset(0, yPx.roundToInt().coerceAtLeast(0)) },
            shape = RoundedCornerShape(20.dp), color = RupioColors.Surface2, tonalElevation = 6.dp,
        ) {
            Column(Modifier.padding(18.dp)) {
                Text("${c.index + 1} de ${c.steps.size}", style = MaterialTheme.typography.labelMedium, color = RupioColors.Primary)
                Spacer(Modifier.height(4.dp))
                Text(step.title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(step.body, style = MaterialTheme.typography.bodyMedium, color = RupioColors.TextDim)
                Spacer(Modifier.height(12.dp))
                Row {
                    TextButton(onClick = { c.finish() }) { Text("Saltar tutorial") }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = { c.next() }) { Text(if (c.index == c.steps.lastIndex) "¡Entendido!" else "Siguiente") }
                }
            }
        }
    }
}

// =====================================================================================
// Bienvenida: 4 páginas con mini-demos que se pueden tocar.
// =====================================================================================
@Composable
fun OnboardingScreen(name: String, onDone: () -> Unit) {
    val pager = rememberPagerState { 4 }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().background(RupioColors.Background).systemBarsPadding().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Rupio", style = MaterialTheme.typography.titleLarge, color = RupioColors.Primary)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onDone) { Text("Saltar") }
        }
        HorizontalPager(pager, Modifier.weight(1f)) { page ->
            Column(Modifier.fillMaxSize().padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                when (page) {
                    0 -> Page(Icons.Rounded.WavingHand, "¡Hola, ${name.substringBefore(' ')}!",
                        "Rupio registra tus entrenamientos y te dice cuándo subir el peso, con reglas basadas en estudios científicos.\n\nNo necesitas saber de gimnasio: te guiamos paso a paso.") {}
                    1 -> Page(Icons.Rounded.CheckCircle, "Registra cada serie",
                        "Escribe el peso y las repeticiones y toca ✓ al terminar. Si lo dejas vacío, se usa el valor sugerido en gris.\n\nPruébalo aquí abajo 👇") { DemoSetRow() }
                    2 -> Page(Icons.Rounded.TrendingUp, "Te dice cuándo subir",
                        "Cuando completas todas tus series en el máximo de repeticiones, Rupio te pide subir el peso un poco (2.5–5 %). Si no, te propone sumar una repetición.") { DemoRecommendation() }
                    else -> Page(Icons.Rounded.Link, "Superseries y descansos",
                        "Agrupa ejercicios en superseries: se ven unidos con una letra y un color. Elige un descanso compartido al terminar la ronda, o uno por ejercicio.") { DemoSuperset() }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.Center) {
            repeat(4) { i ->
                val sel = pager.currentPage == i
                Box(Modifier.padding(4.dp).height(8.dp).width(if (sel) 22.dp else 8.dp).clip(CircleShape).background(if (sel) RupioColors.Primary else RupioColors.Surface3))
            }
        }
        Button(
            onClick = { if (pager.currentPage < 3) scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } else onDone() },
            modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp),
        ) { Text(if (pager.currentPage < 3) "Siguiente" else "Empezar", style = MaterialTheme.typography.titleMedium) }
    }
}

@Composable
private fun Page(icon: ImageVector, title: String, body: String, demo: @Composable () -> Unit) {
    Box(Modifier.size(88.dp).clip(CircleShape).background(RupioColors.Primary.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = RupioColors.Primary, modifier = Modifier.size(44.dp))
    }
    Spacer(Modifier.height(20.dp))
    Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
    Spacer(Modifier.height(10.dp))
    Text(body, style = MaterialTheme.typography.bodyLarge, color = RupioColors.TextDim, textAlign = TextAlign.Center)
    Spacer(Modifier.height(24.dp))
    demo()
}

@Composable
private fun DemoSetRow() {
    var done by remember { mutableStateOf(false) }
    var left by remember { mutableIntStateOf(0) }
    LaunchedEffect(done) { if (done) { left = 5; while (left > 0) { delay(1000); left-- } } else left = 0 }
    val bg by animateColorAsState(if (done) RupioColors.Success.copy(alpha = 0.15f) else RupioColors.Surface, label = "bg")
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(bg).border(1.dp, RupioColors.Outline, RoundedCornerShape(16.dp)).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("1", style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(28.dp))
            Text("135 lb × 10", color = RupioColors.TextFaint, modifier = Modifier.weight(1f))
            Text("135", style = MaterialTheme.typography.titleMedium.merge(Tabular), modifier = Modifier.width(56.dp), textAlign = TextAlign.Center)
            Text("10", style = MaterialTheme.typography.titleMedium.merge(Tabular), modifier = Modifier.width(44.dp), textAlign = TextAlign.Center)
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(if (done) RupioColors.Success else RupioColors.Surface3).clickable { done = !done },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Check, "Completar", tint = if (done) Color.Black else RupioColors.TextDim) }
        }
        AnimatedVisibility(done) {
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Timer, null, tint = RupioColors.Rest)
                Spacer(Modifier.width(8.dp))
                Text(if (left > 0) "Descanso: 0:0$left" else "¡Descanso terminado! (suena una sola vez)", color = RupioColors.Rest, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun DemoRecommendation() {
    Surface(shape = RoundedCornerShape(16.dp), color = RupioColors.Warning.copy(alpha = 0.12f), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.ArrowUpward, null, tint = RupioColors.Warning)
            Spacer(Modifier.width(10.dp))
            Column {
                Text("Sube a 140 lb", fontWeight = FontWeight.SemiBold, color = RupioColors.Warning)
                Text("Completaste 3 × 12 con 135 lb. Vuelve a 8 reps con el nuevo peso.", style = MaterialTheme.typography.bodySmall, color = RupioColors.TextDim)
            }
        }
    }
}

@Composable
private fun DemoSuperset() {
    val c = RupioColors.Supersets[0]
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(RupioColors.Surface).padding(12.dp)) {
        Box(Modifier.width(4.dp).height(96.dp).clip(RoundedCornerShape(2.dp)).background(c))
        Spacer(Modifier.width(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { SupersetTag(0, null); Spacer(Modifier.width(8.dp)); Text("Superserie · descanso compartido 1 min 30 s", style = MaterialTheme.typography.labelMedium, color = RupioColors.TextDim) }
            Row(verticalAlignment = Alignment.CenterVertically) { Text("├", color = c); Spacer(Modifier.width(6.dp)); SupersetTag(0, 0); Spacer(Modifier.width(8.dp)); Text("Press de banca con mancuernas") }
            Row(verticalAlignment = Alignment.CenterVertically) { Text("└", color = c); Spacer(Modifier.width(6.dp)); SupersetTag(0, 1); Spacer(Modifier.width(8.dp)); Text("Remo con mancuerna") }
        }
    }
}
