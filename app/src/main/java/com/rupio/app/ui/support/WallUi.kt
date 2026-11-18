package com.rupio.app.ui.support

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.rupio.app.Routes
import com.rupio.app.container
import com.rupio.app.data.auth.AuthState
import com.rupio.app.data.billing.SupportState
import com.rupio.app.data.billing.WallEntry
import com.rupio.app.data.billing.WallError
import com.rupio.app.data.billing.WallException
import com.rupio.app.data.billing.WallMe
import com.rupio.app.data.billing.WallState
import com.rupio.app.domain.SupportCopy
import com.rupio.app.domain.SupportTier
import com.rupio.app.domain.WallBoost
import com.rupio.app.domain.WallCopy
import com.rupio.app.domain.WallName
import com.rupio.app.safePop
import com.rupio.app.ui.components.InitialsAvatar
import com.rupio.app.ui.components.RupioTopBar
import com.rupio.app.ui.components.SectionCard
import com.rupio.app.ui.theme.Metals
import com.rupio.app.ui.theme.RupioColors
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

// ---------------------------------------------------------------------------------------------------------------
// Pantalla del muro
// ---------------------------------------------------------------------------------------------------------------

/** Qué tan lista está la cuenta para el muro (y las ligas). */
enum class WallAccess { READY, LOCAL_ONLY, NOT_READY }

fun wallAccess(auth: AuthState): WallAccess {
    val signed = auth as? AuthState.SignedIn
    return when {
        signed == null -> WallAccess.NOT_READY
        signed.localOnly -> WallAccess.LOCAL_ONLY
        signed.needsVerification -> WallAccess.NOT_READY
        else -> WallAccess.READY
    }
}

/** Pestaña "Muro" de Apoya Rupio (v1.9: antes era su propia pantalla). */
@Composable
fun WallTab(nav: NavHostController, snackbar: SnackbarHostState, listState: LazyListState) {
    val c = container()
    val wall by c.wall.state.collectAsStateWithLifecycle()
    val support by c.support.state.collectAsStateWithLifecycle()
    val auth by c.auth.state.collectAsStateWithLifecycle()
    val activity = LocalContext.current.findActivity()
    val scope = rememberCoroutineScope()
    val signed = auth as? AuthState.SignedIn
    val access = wallAccess(auth)
    LaunchedEffect(access) { if (access == WallAccess.READY) c.wall.load() }
    // Se re-carga la lista cuando un aporte se registró (tu lugar cambia).
    LaunchedEffect(wall.me?.points) { if (access == WallAccess.READY && wall.loaded) c.wall.load() }

    var joining by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var hiding by remember { mutableStateOf(false) }

    fun act(block: suspend () -> Unit) = scope.launch {
        try { block() } catch (e: WallException) { snackbar.showSnackbar(e.error.userText) }
        catch (e: Exception) { snackbar.showSnackbar(WallError.from(e).userText) }
    }

    WallContent(
        wall = wall, support = support, access = access, listState = listState,
        onJoin = { joining = true },
        onBoost = { b -> activity?.let { c.support.buyWall(it, b, null) } },
        onRename = { renaming = true },
        onHide = { h -> if (h) hiding = true else act { c.wall.setHidden(false) } },
        onCreateAccount = { nav.navigate(Routes.ACCOUNT) },
        onRetry = { scope.launch { c.wall.load() } },
    )

    val defaultName = wall.me?.name ?: signed?.name.orEmpty()
    if (joining) WallNameDialog(
        title = "Ser Mecenas · ${support.price(SupportTier.MECENAS)}",
        intro = "Escribe cómo quieres aparecer. Después de pagar entras al muro con este nombre.",
        initial = defaultName, confirm = "Continuar al pago", askConsent = true,
        onDismiss = { joining = false },
    ) { name -> joining = false; activity?.let { c.support.buyWall(it, WallBoost.BIG, name) } }
    if (renaming) WallNameDialog(
        title = "Tu nombre en el muro", intro = null, initial = defaultName, confirm = "Guardar", askConsent = true,
        onDismiss = { renaming = false },
    ) { name -> renaming = false; act { c.wall.setName(name) } }
    if (hiding) AlertDialog(
        onDismissRequest = { hiding = false },
        title = { Text("¿Quitar tu nombre del muro?") },
        text = { Text("Dejará de verse para los demás. Conservas tu nivel Mecenas y tus puntos, y puedes volver a aparecer cuando quieras.") },
        confirmButton = { TextButton(onClick = { hiding = false; act { c.wall.setHidden(true) } }) { Text("Quitar") } },
        dismissButton = { TextButton(onClick = { hiding = false }) { Text("Cancelar") } },
    )
}

/** Contenido del muro, sin estado propio (lo usan la pantalla y las capturas de prueba). */
@Composable
fun WallContent(
    wall: WallState, support: SupportState, access: WallAccess, modifier: Modifier = Modifier,
    onJoin: () -> Unit, onBoost: (WallBoost) -> Unit, onRename: () -> Unit, onHide: (Boolean) -> Unit,
    onCreateAccount: () -> Unit, onRetry: () -> Unit, listState: LazyListState = rememberLazyListState(),
) {
    val me = wall.me
    // Key estable = nombre + desde cuándo (no el lugar: si alguien sube, las demás filas no se vuelven a crear).
    // Nunca repetida: LazyColumn falla con keys iguales.
    val keys = remember(wall.entries) { stableKeys(wall.entries.map { it.name + "|" + it.sinceMs }) }
    LazyColumn(modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item(key = "intro", contentType = "card") {
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    InitialsAvatar(SupportCopy.DEV_NAME, 44.dp); Spacer(Modifier.width(12.dp))
                    Text("Gracias 💙", style = MaterialTheme.typography.titleLarge)
                }
                Spacer(Modifier.height(8.dp))
                Text(WallCopy.INTRO, style = MaterialTheme.typography.bodyMedium, color = RupioColors.TextDim)
                Spacer(Modifier.height(4.dp))
                Text(WallCopy.ORDER, style = MaterialTheme.typography.labelMedium, color = RupioColors.TextFaint)
            }
        }
        support.message?.let { msg -> item { InfoBanner(msg) } }
        if (support.wallRegistering) item { InfoBanner("Registrando tu aporte en el muro…", loading = true) }
        when (access) {
            WallAccess.LOCAL_ONLY, WallAccess.NOT_READY -> item {
                SectionCard {
                    Text("🌟  Aparece en el muro", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(WallCopy.NEEDS_ACCOUNT, style = MaterialTheme.typography.bodyMedium, color = RupioColors.TextDim)
                    if (access == WallAccess.LOCAL_ONLY) {
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = onCreateAccount, modifier = Modifier.fillMaxWidth()) { Text("Crear cuenta y conservar mis datos") }
                    }
                }
            }
            WallAccess.READY -> {
                if (me != null && me.isPatron) item { MyWallCard(me, support, onBoost, onRename, onHide) }
                else if (wall.loaded) item { JoinCard(support, onJoin) }
            }
        }
        if (access == WallAccess.READY) {
            wall.error?.let { err -> item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(err, style = MaterialTheme.typography.bodyMedium, color = RupioColors.Warning, modifier = Modifier.weight(1f))
                    TextButton(onClick = onRetry) { Text("Reintentar") }
                }
            } }
            if (wall.loading && wall.entries.isEmpty()) item {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
            if (wall.loaded && wall.entries.isEmpty() && wall.error == null) item {
                Text("Todavía no hay nadie en el muro. ¡Puedes ser la primera persona! 🌟", style = MaterialTheme.typography.bodyMedium,
                    color = RupioColors.TextDim, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp))
            }
            if (wall.entries.isNotEmpty()) item {
                Text("${wall.me?.total ?: wall.entries.size} ${if ((wall.me?.total ?: wall.entries.size) == 1) "persona" else "personas"} en el muro",
                    style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp).semantics { heading() })
            }
            itemsIndexed(wall.entries, key = { i, _ -> keys[i] }, contentType = { _, _ -> "row" }) { _, e -> WallRow(e) }
        }
    }
}

/** Las mismas keys, con un sufijo en las repetidas (dos personas con el mismo nombre y el mismo milisegundo). */
internal fun stableKeys(raw: List<String>): List<String> {
    val seen = HashSet<String>(raw.size * 2)
    return raw.mapIndexed { i, k -> if (seen.add(k)) k else "$k#$i" }
}

@Composable
private fun InfoBanner(text: String, loading: Boolean = false) {
    Surface(color = RupioColors.Surface2, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Icon(Icons.Rounded.Info, null, tint = RupioColors.Primary)
            Spacer(Modifier.width(10.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private val sinceFmt = java.time.format.DateTimeFormatter.ofPattern("MMMM 'de' yyyy", com.rupio.app.core.Fmt.locale)

/** "noviembre de 2026" (vacío si no hay fecha). */
fun wallSince(ms: Long): String = if (ms <= 0) "" else Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate().format(sinceFmt)

/** Degradado del nivel Mecenas: el metal Aurora (se crea una sola vez, en Metals). */
val MecenasBrush: Brush get() = Metals.Aurora.brush

@Composable
private fun JoinCard(support: SupportState, onJoin: () -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = RupioColors.Surface, border = BorderStroke(1.5.dp, Metals.Aurora.ink.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(MecenasBrush), contentAlignment = Alignment.Center) { Text(SupportTier.MECENAS.emoji) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("¿Quieres aparecer aquí?", style = MaterialTheme.typography.titleMedium)
                    Text("Nivel Mecenas · pago único", style = MaterialTheme.typography.labelMedium, color = RupioColors.TextDim)
                }
            }
            Spacer(Modifier.height(8.dp))
            SupportTier.MECENAS.perks.forEach { p ->
                Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Check, null, tint = RupioColors.Success, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp))
                    Text(p, style = MaterialTheme.typography.bodyMedium, color = RupioColors.TextDim)
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(onClick = onJoin, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp), shape = RoundedCornerShape(14.dp)) {
                Text("Ser Mecenas · ${support.price(SupportTier.MECENAS)}", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun MyWallCard(me: WallMe, support: SupportState, onBoost: (WallBoost) -> Unit, onRename: () -> Unit, onHide: (Boolean) -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = RupioColors.Surface, border = BorderStroke(1.5.dp, Metals.Aurora.ink.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(CircleShape).background(MecenasBrush), contentAlignment = Alignment.Center) {
                    Text(if (me.hidden || me.rank == null) SupportTier.MECENAS.emoji else "#${me.rank}", style = MaterialTheme.typography.titleMedium,
                        color = Metals.Aurora.on, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(me.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(when {
                        me.blocked -> "Tu nombre está oculto por moderación. Cámbialo para volver a aparecer."
                        me.hidden -> "Tu nombre está oculto"
                        me.rank != null -> WallCopy.rank(me.rank) + " · ${me.points} puntos"
                        else -> "${me.points} puntos"
                    }, style = MaterialTheme.typography.labelMedium, color = if (me.blocked) RupioColors.Warning else RupioColors.TextDim)
                }
            }
            if (!me.hidden && !me.blocked && me.rank != null) {
                Spacer(Modifier.height(10.dp))
                val toNext = me.toNext
                if (toNext != null && me.rank > 1) Text(WallCopy.toPass(toNext, me.rank - 1), style = MaterialTheme.typography.bodyMedium)
                else Text("¡Vas en primer lugar! Gracias por tanto apoyo. 🥇", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(10.dp))
                Text("Sube de lugar con un aporte extra:", style = MaterialTheme.typography.labelLarge, color = RupioColors.TextDim)
                Spacer(Modifier.height(6.dp))
                val suggested = WallBoost.toPass(toNext?.takeIf { me.rank > 1 })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    WallBoost.entries.forEach { b ->
                        BoostButton(b, support.price(b), highlighted = b == suggested, enabled = support.buying == null && !support.wallRegistering,
                            modifier = Modifier.weight(1f)) { onBoost(b) }
                    }
                }
                Text(WallCopy.POINTS_NOTE, style = MaterialTheme.typography.labelSmall, color = RupioColors.TextFaint, modifier = Modifier.padding(top = 6.dp))
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onRename) { Text("Cambiar nombre") }
                TextButton(onClick = { onHide(!me.hidden) }) { Text(if (me.hidden) "Volver a aparecer" else "Quitarme del muro", color = RupioColors.TextDim) }
            }
        }
    }
}

@Composable
private fun BoostButton(b: WallBoost, price: String, highlighted: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Surface(onClick = onClick, enabled = enabled, shape = shape, modifier = modifier.heightIn(min = 64.dp).semantics { contentDescription = "Aportar $price, suma ${b.points} puntos" },
        color = if (highlighted) Metals.Aurora.ink.copy(alpha = 0.16f) else RupioColors.Surface2,
        border = BorderStroke(if (highlighted) 1.5.dp else 1.dp, if (highlighted) Metals.Aurora.ink else RupioColors.Outline)) {
        Column(Modifier.padding(horizontal = 6.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("+${b.points}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(price, style = MaterialTheme.typography.labelMedium, color = RupioColors.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun WallRow(e: WallEntry) {
    val medal = when (e.rank) { 1 -> "🥇"; 2 -> "🥈"; 3 -> "🥉"; else -> null }
    val since = remember(e.sinceMs) {
        wallSince(e.sinceMs)
    }
    Surface(shape = RoundedCornerShape(14.dp), color = if (e.me) Metals.Aurora.ink.copy(alpha = 0.12f) else RupioColors.Surface,
        border = if (e.me) BorderStroke(1.dp, Metals.Aurora.ink.copy(alpha = 0.6f)) else null, modifier = Modifier.fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = "Lugar ${e.rank}, ${e.name}" + (if (e.me) ", eres tú" else "") + (if (since.isNotBlank()) ", desde $since" else "") }) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(40.dp), contentAlignment = Alignment.Center) {
                if (medal != null) Text(medal, style = MaterialTheme.typography.headlineSmall)
                else Text("#${e.rank}", style = MaterialTheme.typography.titleSmall, color = RupioColors.TextDim)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(e.name + if (e.me) " (tú)" else "", style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (since.isNotBlank()) Text("Desde $since", style = MaterialTheme.typography.labelSmall, color = RupioColors.TextFaint)
            }
            Text(SupportTier.MECENAS.emoji)
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Nombre + permiso para publicarlo
// ---------------------------------------------------------------------------------------------------------------

/**
 * Pide el nombre público y el permiso EXPRESO para mostrarlo (casilla sin marcar: LFPDPPP pide consentimiento para
 * publicar datos personales). Valida lo mismo que el servidor (2–30 caracteres, sin enlaces ni correos).
 */
@Composable
fun WallNameDialog(title: String, intro: String?, initial: String, confirm: String, askConsent: Boolean, onDismiss: () -> Unit,
                   label: String = "Nombre en el muro", consentText: String = WallCopy.CONSENT, onConfirm: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial.take(WallName.MAX)) }
    var consent by rememberSaveable { mutableStateOf(false) }
    val error = WallName.problem(name)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                intro?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = RupioColors.TextDim) }
                OutlinedTextField(name, { name = it.take(WallName.MAX) }, label = { Text(label) }, singleLine = true,
                    isError = name.isNotEmpty() && error != null, supportingText = { Text(if (name.isNotEmpty() && error != null) error else "${WallName.clean(name).length}/${WallName.MAX} · puede ser un apodo") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words), modifier = Modifier.fillMaxWidth())
                if (askConsent) Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).toggleable(consent, role = Role.Checkbox) { consent = it }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Checkbox(consent, onCheckedChange = null, modifier = Modifier.padding(top = 2.dp, end = 8.dp))
                    Text(consentText, style = MaterialTheme.typography.bodySmall, color = RupioColors.TextDim)
                }
            }
        },
        confirmButton = { TextButton(enabled = error == null && (consent || !askConsent), onClick = { onConfirm(WallName.clean(name)) }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** Para entrar al muro hace falta cuenta. */
@Composable
fun WallAccountDialog(localOnly: Boolean, onCreate: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Necesitas una cuenta") },
        text = { Text(WallCopy.NEEDS_ACCOUNT + if (localOnly) " Tus entrenos se conservan." else " Confirma tu correo o vuelve a entrar.") },
        confirmButton = { if (localOnly) TextButton(onClick = onCreate) { Text("Crear cuenta") } else TextButton(onClick = onDismiss) { Text("Entendido") } },
        dismissButton = { if (localOnly) TextButton(onClick = onDismiss) { Text("Ahora no") } },
    )
}

/** Un aporte extra ya cuenta: tu lugar nuevo (con confeti). */
@Composable
fun WallRaisedDialog(rank: Int?, previousRank: Int?, onSeeWall: () -> Unit, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.9f), shape = RoundedCornerShape(24.dp), color = RupioColors.Surface) {
            Box {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(88.dp).clip(CircleShape).background(MecenasBrush), contentAlignment = Alignment.Center) {
                        Text(rank?.let { "#$it" } ?: "🌟", style = MaterialTheme.typography.headlineMedium, color = Metals.Aurora.on, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(14.dp))
                    Text("¡Gracias otra vez! 🙌", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(6.dp))
                    Text(when {
                        rank == null -> "Tu aporte ya cuenta en el muro."
                        previousRank != null && rank < previousRank -> "Subiste del lugar #$previousRank al #$rank."
                        else -> "Tu aporte ya cuenta. Vas en el lugar #$rank."
                    }, style = MaterialTheme.typography.bodyLarge, color = RupioColors.TextDim, textAlign = TextAlign.Center)
                    Text(SupportCopy.THANKS_SIGN, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp))
                    Spacer(Modifier.height(18.dp))
                    Button(onClick = onSeeWall, modifier = Modifier.fillMaxWidth()) { Text("Ver el muro") }
                    TextButton(onClick = onClose) { Text("Cerrar") }
                }
                Confetti(Modifier.matchParentSize(), pieces = 50)
            }
        }
    }
}
