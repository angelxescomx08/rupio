package com.rupio.app.ui.learn

import com.rupio.app.goToTab
import com.rupio.app.safePop
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.rupio.app.Routes
import com.rupio.app.core.searchKey
import com.rupio.app.ui.components.*
import com.rupio.app.ui.theme.RupioColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// ------------------------------------------------------------------ progreso de tutoriales

/** Tutoriales terminados (en este teléfono). */
object LearnProgress {
    private val _done = MutableStateFlow<Set<String>>(emptySet())
    val done: StateFlow<Set<String>> = _done
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("rupio_learn", Context.MODE_PRIVATE)
    fun load(ctx: Context) { _done.value = prefs(ctx).getStringSet("done", emptySet()).orEmpty().toSet() }
    fun markDone(ctx: Context, id: String) {
        val n = _done.value + id; _done.value = n
        prefs(ctx).edit().putStringSet("done", n).apply()
    }
}

// ------------------------------------------------------------------ piezas reutilizables

/** Recuadro ámbar para lo importante. */
@Composable
fun ImportantBox(text: String, modifier: Modifier = Modifier, title: String = "Importante") {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = RupioColors.Warning.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, RupioColors.Warning.copy(alpha = 0.55f))) {
        Row(Modifier.padding(14.dp)) {
            Icon(Icons.Rounded.PriorityHigh, null, tint = RupioColors.Warning, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text(title, style = MaterialTheme.typography.labelLarge, color = RupioColors.Warning, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text(text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun PaperLinks(papers: List<Paper>) {
    if (papers.isEmpty()) return
    val uri = LocalUriHandler.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Lo respalda", style = MaterialTheme.typography.labelMedium, color = RupioColors.TextDim)
        papers.forEach { p ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { runCatching { uri.openUri(p.url) } }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Description, null, tint = RupioColors.Primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(p.title, style = MaterialTheme.typography.bodySmall, color = RupioColors.Primary, modifier = Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Rounded.OpenInNew, "Abrir estudio", tint = RupioColors.TextFaint, modifier = Modifier.size(16.dp))
            }
        }
    }
}

/** Explicación completa de un concepto (en la hoja "?" y en el glosario). */
@Composable
fun ConceptBody(c: Concept) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(c.oneLiner, style = MaterialTheme.typography.titleSmall, color = RupioColors.Text)
        Text(c.simple, style = MaterialTheme.typography.bodyMedium, color = RupioColors.TextDim)
        LabeledBox(Icons.Rounded.Lightbulb, "Ejemplo", c.example)
        LabeledBox(Icons.Rounded.PhoneAndroid, "En Rupio", c.inRupio)
        PaperLinks(c.papers)
    }
}

@Composable
private fun LabeledBox(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, text: String) {
    Surface(shape = RoundedCornerShape(14.dp), color = RupioColors.Surface2, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp)) {
            Icon(icon, null, tint = RupioColors.Primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text(label, style = MaterialTheme.typography.labelMedium, color = RupioColors.TextDim)
                Text(text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** Hoja que explica un concepto sin salir de la pantalla. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConceptSheet(id: String, onDismiss: () -> Unit) {
    val c = Concepts.byId(id) ?: return onDismiss()
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = RupioColors.Surface) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text(c.title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(10.dp))
            ConceptBody(c)
        }
    }
}

/** Botón "?" junto a un título: explica el concepto a quien no sabe nada de gym. */
@Composable
fun HelpIcon(conceptId: String, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }, modifier = modifier.size(36.dp)) {
        Icon(Icons.Rounded.HelpOutline, "¿Qué es esto?", tint = RupioColors.TextFaint, modifier = Modifier.size(18.dp))
    }
    if (open) ConceptSheet(conceptId) { open = false }
}

/** Título de tarjeta + "?" (alineados). */
@Composable
fun TitleWithHelp(text: String, conceptId: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMedium)
        HelpIcon(conceptId)
        Spacer(Modifier.weight(1f))
        trailing?.invoke()
    }
}

// ------------------------------------------------------------------ pantalla Aprende

@Composable
fun LearnScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { LearnProgress.load(ctx) }
    val done by LearnProgress.done.collectAsState()
    Column(Modifier.fillMaxSize()) {
    // Se abre desde Perfil o Progreso (ya no es pestaña): con flecha para regresar.
    RupioTopBar("Aprende", onBack = { nav.safePop() })
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Lo más importante", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                KeyCard("🎯", "Haz siempre el ejercicio igual", "Misma máquina, mismo recorrido, misma velocidad. Si cambias la técnica, tus números ya no dicen si progresas.") { nav.navigate(Routes.concept("tecnica")) }
                KeyCard("📈", "Sobrecarga progresiva", "Poco a poco haz un poco más: más reps o más peso. Rupio te avisa cuándo subir.") { nav.navigate(Routes.concept("sobrecarga")) }
                KeyCard("⏱️", "No hace falta vivir en el gym", "Más volumen ayuda, pero cada vez menos. Con 2–4 días de alrededor de una hora se logran muy buenos resultados.") { nav.navigate(Routes.concept("rendimientos")) }
                KeyCard("🌱", "Tus primeros años valen oro", "Al empezar se progresa más rápido. Construye buenas bases: técnica, constancia y ejercicios estables.") { nav.navigate(Routes.concept("principiante")) }
                KeyCard("🥗", "Para bajar de peso, la dieta manda", "El ejercicio solo casi no baja el peso. Necesitas un déficit calórico (comer menos de lo que gastas).") { nav.navigate(Routes.concept("deficit")) }
            }
        }
        item { SectionTitle("Tutoriales", action = { Text("${done.size} de ${Tutorials.all.size}", style = MaterialTheme.typography.labelMedium, color = RupioColors.TextDim) }) }
        items(Tutorials.all, key = { it.id }) { t ->
            Surface(shape = RoundedCornerShape(18.dp), color = RupioColors.Surface, border = BorderStroke(1.dp, RupioColors.Outline.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth().clickable { nav.navigate(Routes.tutorial(t.id)) }) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(RupioColors.Surface2), contentAlignment = Alignment.Center) {
                        Text(t.emoji, style = MaterialTheme.typography.titleLarge)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t.title, style = MaterialTheme.typography.titleSmall)
                        Text("${t.subtitle} · ${t.minutes} min", style = MaterialTheme.typography.labelMedium, color = RupioColors.TextDim)
                    }
                    if (t.id in done) Icon(Icons.Rounded.CheckCircle, "Terminado", tint = RupioColors.Success)
                    else Icon(Icons.Rounded.ChevronRight, null, tint = RupioColors.TextFaint)
                }
            }
        }
        item { SectionTitle("Consulta") }
        item {
            SectionCard(padding = 4.dp) {
                NavRow(Icons.Rounded.AutoAwesome, "Arma tu rutina", "Guía según tus días, tiempo, equipo y experiencia") { nav.navigate(Routes.GUIDE) }
                HorizontalDivider(color = RupioColors.Outline.copy(alpha = 0.4f))
                NavRow(Icons.Rounded.MenuBook, "Glosario", "${Concepts.all.size} conceptos explicados fácil, con sus estudios") { nav.navigate(Routes.GLOSSARY) }
                HorizontalDivider(color = RupioColors.Outline.copy(alpha = 0.4f))
                NavRow(Icons.Rounded.Favorite, "Beneficios del ejercicio", "Lo que la ciencia dice que ganas al entrenar fuerza") { nav.navigate(Routes.BENEFITS) }
                HorizontalDivider(color = RupioColors.Outline.copy(alpha = 0.4f))
                NavRow(Icons.Rounded.FitnessCenter, "Biblioteca de ejercicios", "Imágenes, técnica y búsqueda de videos") { nav.navigate(Routes.picker("browse", "-")) }
                HorizontalDivider(color = RupioColors.Outline.copy(alpha = 0.4f))
                NavRow(Icons.Rounded.MonitorWeight, "Peso corporal", "Registra tu peso y mira tu tendencia") { nav.goToTab(Routes.BODY_WEIGHT) }
            }
        }
    }
    }
}

@Composable
private fun KeyCard(emoji: String, title: String, body: String, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = RupioColors.Warning.copy(alpha = 0.08f), border = BorderStroke(1.dp, RupioColors.Warning.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Text(emoji, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(body, style = MaterialTheme.typography.bodySmall, color = RupioColors.TextDim)
            }
        }
    }
}

@Composable
private fun NavRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, sub: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = RupioColors.Primary)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(sub, style = MaterialTheme.typography.labelMedium, color = RupioColors.TextDim)
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = RupioColors.TextFaint)
    }
}

// ------------------------------------------------------------------ glosario y concepto

@Composable
fun GlossaryScreen(nav: NavHostController) {
    var q by remember { mutableStateOf("") }
    val key = q.searchKey()
    val list = remember(key) { Concepts.all.filter { key.isBlank() || (it.title + " " + it.oneLiner + " " + it.simple).searchKey().contains(key) } }
    Column(Modifier.fillMaxSize()) {
        RupioTopBar("Glosario", onBack = { nav.safePop() })
        OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp), placeholder = { Text("Buscar: serie, RPE, déficit…") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) }, singleLine = true, shape = RoundedCornerShape(14.dp))
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(list, key = { it.id }) { c ->
                Surface(shape = RoundedCornerShape(14.dp), color = RupioColors.Surface, modifier = Modifier.fillMaxWidth().clickable { nav.navigate(Routes.concept(c.id)) }) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(c.title, style = MaterialTheme.typography.titleSmall)
                            Text(c.oneLiner, style = MaterialTheme.typography.bodySmall, color = RupioColors.TextDim)
                        }
                        Icon(Icons.Rounded.ChevronRight, null, tint = RupioColors.TextFaint)
                    }
                }
            }
            if (list.isEmpty()) item { Text("Nada con \"$q\".", color = RupioColors.TextDim) }
        }
    }
}

@Composable
fun ConceptScreen(nav: NavHostController, id: String) {
    val c = Concepts.byId(id)
    Column(Modifier.fillMaxSize()) {
        RupioTopBar(c?.title ?: "Concepto", onBack = { nav.safePop() })
        if (c == null) return
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 32.dp)) { ConceptBody(c) }
    }
}

@Composable
fun BenefitsScreen(nav: NavHostController) {
    Column(Modifier.fillMaxSize()) {
        RupioTopBar("Beneficios del ejercicio", onBack = { nav.safePop() })
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("El entrenamiento de fuerza no es solo para verse bien. Esto es lo que encontraron estudios grandes:",
                    style = MaterialTheme.typography.bodyMedium, color = RupioColors.TextDim)
            }
            items(Benefits.all) { b ->
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(b.icon, style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.width(10.dp))
                        Text(b.title, style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(b.body, style = MaterialTheme.typography.bodyMedium, color = RupioColors.TextDim)
                    Spacer(Modifier.height(10.dp))
                    PaperLinks(b.papers)
                }
            }
            item {
                Text("Los estudios muestran asociaciones y promedios; cada persona es distinta. Si tienes una condición de salud, consulta a tu médico antes de empezar.",
                    style = MaterialTheme.typography.labelSmall, color = RupioColors.TextFaint)
            }
        }
    }
}
