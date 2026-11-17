package com.rupio.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rupio.app.container
import com.rupio.app.data.auth.AuthException
import com.rupio.app.data.auth.AuthState
import com.rupio.app.data.remote.RejectedChange
import com.rupio.app.domain.SyncSummary
import com.rupio.app.ui.theme.RupioColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Abre, desde cualquier pantalla, el detalle del respaldo o "Entrar de nuevo". Lo pinta [SyncUiHost] en MainShell. */
class SyncUiController {
    var sheet by mutableStateOf(false)
    var reauth by mutableStateOf(false)
}

val LocalSyncUi = staticCompositionLocalOf { SyncUiController() }

/** Estado del respaldo, recalculado cada 30 s para que "hace 5 min" no se quede viejo. */
@Composable
fun rememberSyncSummary(): SyncSummary {
    val c = container()
    val status by c.sync.status.collectAsStateWithLifecycle()
    val pending by c.db.workouts().observePending().collectAsStateWithLifecycle(initialValue = 0)
    val auth by c.auth.state.collectAsStateWithLifecycle()
    val zone by c.zone.collectAsStateWithLifecycle()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = System.currentTimeMillis() } }
    val me = auth as? AuthState.SignedIn
    return remember(status, pending, me?.localOnly, me?.sessionExpired, now, zone) {
        SyncSummary.of(status, pending, me?.localOnly == true, me?.sessionExpired == true, System.currentTimeMillis(), zone)
    }
}

fun SyncSummary.Level.color(): Color = when (this) {
    SyncSummary.Level.ACT -> RupioColors.Danger
    SyncSummary.Level.WARN -> RupioColors.Warning
    SyncSummary.Level.BUSY -> RupioColors.Primary
    SyncSummary.Level.OK -> RupioColors.Success
    SyncSummary.Level.LOCAL -> RupioColors.TextDim
}

private fun SyncSummary.icon(): ImageVector = when (level) {
    SyncSummary.Level.ACT -> Icons.Rounded.CloudOff
    SyncSummary.Level.WARN -> if (action == SyncSummary.Action.REVIEW) Icons.Rounded.SyncProblem else Icons.Rounded.CloudOff
    SyncSummary.Level.BUSY -> Icons.Rounded.CloudUpload
    SyncSummary.Level.OK -> Icons.Rounded.CloudDone
    SyncSummary.Level.LOCAL -> Icons.Rounded.PhoneAndroid
}

/** Nube discreta para la barra de Inicio e Historial: color del estado y número de pendientes. Al tocarla, el detalle. */
@Composable
fun SyncStatusIcon(modifier: Modifier = Modifier) {
    val s = rememberSyncSummary()
    val ui = LocalSyncUi.current
    val label = "Respaldo: ${s.title}" + if (s.pending > 0) ", ${s.pending} pendientes" else ""
    IconButton(onClick = { ui.sheet = true }, modifier = modifier.semantics { contentDescription = label }) {
        BadgedBox(badge = {
            if (s.pending > 0 && s.level != SyncSummary.Level.OK && s.level != SyncSummary.Level.LOCAL) Badge(containerColor = s.level.color(), contentColor = Color.Black) {
                Text(if (s.pending > 99) "99+" else "${s.pending}")
            }
        }) { Icon(s.icon(), null, tint = s.level.color()) }
    }
}

/** Tarjeta grande en Inicio SOLO cuando hace falta actuar (sesión vencida): no se puede pasar por alto. */
@Composable
fun SyncAlertCard(modifier: Modifier = Modifier) {
    val s = rememberSyncSummary()
    val ui = LocalSyncUi.current
    if (s.level != SyncSummary.Level.ACT) return
    Surface(modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }, shape = RoundedCornerShape(18.dp),
        color = RupioColors.Danger.copy(alpha = 0.12f), border = BorderStroke(1.dp, RupioColors.Danger.copy(alpha = 0.6f))) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CloudOff, null, tint = RupioColors.Danger)
                Spacer(Modifier.width(10.dp))
                Text(s.title, style = MaterialTheme.typography.titleSmall)
            }
            Text(s.body, style = MaterialTheme.typography.bodySmall, color = RupioColors.TextDim, modifier = Modifier.padding(top = 4.dp))
            TextButton(onClick = { ui.reauth = true }, modifier = Modifier.align(Alignment.End)) { Text("Entrar de nuevo") }
        }
    }
}

/** Pinta el detalle del respaldo y "Entrar de nuevo" (una sola vez, en MainShell). */
@Composable
fun SyncUiHost(controller: SyncUiController, onCreateAccount: () -> Unit) {
    if (controller.sheet) SyncSheet(onDismiss = { controller.sheet = false },
        onSignInAgain = { controller.sheet = false; controller.reauth = true },
        onCreateAccount = { controller.sheet = false; onCreateAccount() })
    if (controller.reauth) ReauthDialog { controller.reauth = false }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SyncSheet(onDismiss: () -> Unit, onSignInAgain: () -> Unit, onCreateAccount: () -> Unit) {
    val c = container()
    val s = rememberSyncSummary()
    val scope = rememberCoroutineScope()
    var note by remember { mutableStateOf<String?>(null) }
    var rejected by remember { mutableStateOf<List<RejectedChange>>(emptyList()) }
    LaunchedEffect(s.action, s.pending) { rejected = if (s.action == SyncSummary.Action.REVIEW) c.sync.describeRejected() else emptyList() }

    /** "Sincronizar ahora": sin red lo dice en vez de quedarse callado (antes se tocaba una y otra vez sin respuesta). */
    fun syncNow() {
        if (!c.isOnline()) { note = "Sin internet: se subirá cuando vuelva la conexión."; return }
        note = null
        c.scope.launch { c.sync.sync() }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = RupioColors.Surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(s.icon(), null, tint = s.level.color())
                Spacer(Modifier.width(10.dp))
                Text(s.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            }
            Text(s.body, style = MaterialTheme.typography.bodyMedium, color = RupioColors.TextDim, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            if (s.level == SyncSummary.Level.BUSY && s.title.startsWith("Subiendo")) LinearProgressIndicator(Modifier.fillMaxWidth(), color = RupioColors.Primary, trackColor = RupioColors.Surface3)
            note?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = RupioColors.Warning, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }

            rejected.forEach { r ->
                Surface(shape = RoundedCornerShape(14.dp), color = RupioColors.Surface2) {
                    Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 12.dp)) {
                        Text(r.title, style = MaterialTheme.typography.titleSmall)
                        Text(r.detail, style = MaterialTheme.typography.bodySmall, color = RupioColors.TextDim)
                        Row(Modifier.align(Alignment.End)) {
                            TextButton(onClick = { scope.launch { c.sync.discardRejected(r.table, r.key); rejected = c.sync.describeRejected() } },
                                colors = ButtonDefaults.textButtonColors(contentColor = RupioColors.Danger)) { Text("Descartar") }
                            TextButton(onClick = { c.sync.retryRejected(r.table, r.key); rejected = rejected - r; syncNow() }) { Text("Reintentar") }
                        }
                    }
                }
            }
            if (rejected.isNotEmpty()) Text("«Descartar» deja el dato en tu teléfono tal como está, pero ya no intenta subirlo.",
                style = MaterialTheme.typography.bodySmall, color = RupioColors.TextDim)

            when (s.action) {
                SyncSummary.Action.SIGN_IN_AGAIN -> Button(onClick = onSignInAgain, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Entrar de nuevo") }
                SyncSummary.Action.CREATE_ACCOUNT -> Button(onClick = onCreateAccount, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Crear cuenta y conservar mis datos") }
                else -> if (s.level != SyncSummary.Level.LOCAL) OutlinedButton(onClick = { syncNow() }, enabled = s.level != SyncSummary.Level.BUSY || s.pending > 0,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Icon(Icons.Rounded.Sync, null); Spacer(Modifier.width(6.dp)); Text(if (s.level == SyncSummary.Level.BUSY && s.title.endsWith("…")) "Sincronizando…" else "Sincronizar ahora")
                }
            }
        }
    }
}

/** Sesión vencida: solo pide la contraseña de la MISMA cuenta. Nada del teléfono se borra. */
@Composable
fun ReauthDialog(onDone: () -> Unit) {
    val c = container()
    val scope = rememberCoroutineScope()
    val me = c.auth.state.collectAsStateWithLifecycle().value as? AuthState.SignedIn ?: return
    var pass by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var forgot by remember { mutableStateOf(false) }
    fun submit() {
        if (pass.isEmpty()) { error = "Escribe tu contraseña."; return }
        busy = true; error = null
        scope.launch {
            try { c.auth.reauthenticate(pass); c.scope.launch { c.sync.sync() }; onDone() }
            catch (e: AuthException) { error = e.message }
            catch (e: Exception) { error = "Algo salió mal (código E1). Inténtalo de nuevo." }
            busy = false
        }
    }
    if (forgot) { ForgotPasswordDialog(me.email) { forgot = false }; return }
    AlertDialog(
        onDismissRequest = { if (!busy) onDone() },
        icon = { Icon(Icons.Rounded.Lock, null) },
        title = { Text("Entrar de nuevo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Por seguridad tu sesión venció. Escribe tu contraseña para volver a respaldar; tus entrenos de este teléfono se quedan como están.",
                    style = MaterialTheme.typography.bodyMedium, color = RupioColors.TextDim)
                Text(me.email, style = MaterialTheme.typography.titleSmall)
                PasswordField(pass, { pass = it }, show, { show = !show }, onDone = { submit() })
                error?.let { Text(it, color = RupioColors.Danger, style = MaterialTheme.typography.bodySmall) }
                TextButton(onClick = { forgot = true }, contentPadding = PaddingValues(0.dp)) { Text("¿Olvidaste tu contraseña?") }
            }
        },
        confirmButton = { TextButton(onClick = { submit() }, enabled = !busy) { Text(if (busy) "Entrando…" else "Entrar") } },
        dismissButton = { TextButton(onClick = onDone, enabled = !busy) { Text("Ahora no") } },
    )
}

/** Campo de contraseña con "mostrar" y conectado al autocompletado / gestores de contraseñas. */
@Composable
fun PasswordField(value: String, onValue: (String) -> Unit, show: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier,
                  label: String = "Contraseña", newPassword: Boolean = false, onDone: () -> Unit = {}) {
    OutlinedTextField(value, onValue, label = { Text(label) }, singleLine = true,
        modifier = modifier.fillMaxWidth().semantics { contentType = if (newPassword) ContentType.NewPassword else ContentType.Password },
        leadingIcon = { Icon(Icons.Rounded.Lock, null) }, shape = RoundedCornerShape(14.dp),
        visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = { IconButton(onClick = onToggle) { Icon(if (show) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, if (show) "Ocultar contraseña" else "Mostrar contraseña") } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }))
}

/** Formato de correo razonable (algo@algo.algo, sin espacios). */
fun looksLikeEmail(s: String): Boolean = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$").matches(s.trim())

/**
 * "¿Olvidaste tu contraseña?" en dos pasos, sin salir de la app: 1) correo → Neon Auth manda un código de 6 dígitos;
 * 2) código + contraseña nueva. La respuesta del paso 1 es la misma exista o no la cuenta.
 */
@Composable
fun ForgotPasswordDialog(initialEmail: String, onDone: (changedEmail: String?) -> Unit) {
    val c = container()
    val scope = rememberCoroutineScope()
    var step by remember { mutableIntStateOf(1) }
    var email by remember { mutableStateOf(initialEmail) }
    var code by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun send() {
        if (!looksLikeEmail(email)) { error = "Escribe un correo válido, como nombre@gmail.com."; return }
        busy = true; error = null
        scope.launch {
            try { c.auth.requestPasswordReset(email); step = 2 } catch (e: AuthException) { error = e.message }
            busy = false
        }
    }
    fun reset() {
        if (code.trim().length < 6) { error = "Escribe el código de 6 dígitos del correo."; return }
        if (pass.length < 8) { error = "La contraseña nueva debe tener al menos 8 caracteres."; return }
        busy = true; error = null
        scope.launch {
            try { c.auth.resetPassword(email, code, pass); step = 3 } catch (e: AuthException) { error = e.message }
            busy = false
        }
    }
    AlertDialog(
        onDismissRequest = { if (!busy) onDone(null) },
        title = { Text(if (step == 3) "Contraseña cambiada" else "Recuperar contraseña") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (step) {
                    1 -> {
                        Text("Te enviaremos un código de 6 dígitos a tu correo.", style = MaterialTheme.typography.bodyMedium, color = RupioColors.TextDim)
                        OutlinedTextField(email, { email = it.trim() }, label = { Text("Correo electrónico") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.EmailAddress },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { send() }), shape = RoundedCornerShape(14.dp))
                    }
                    2 -> {
                        Text("Si hay una cuenta con $email, te llegó un código. Revisa también la carpeta de spam. Vence en unos minutos.",
                            style = MaterialTheme.typography.bodyMedium, color = RupioColors.TextDim)
                        OutlinedTextField(code, { code = it.filter(Char::isDigit).take(6) }, label = { Text("Código") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.SmsOtpCode },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Next), shape = RoundedCornerShape(14.dp))
                        PasswordField(pass, { pass = it }, show, { show = !show }, label = "Contraseña nueva", newPassword = true, onDone = { reset() })
                        TextButton(onClick = { code = ""; send() }, contentPadding = PaddingValues(0.dp), enabled = !busy) { Text("Enviar otro código") }
                    }
                    else -> Text("Ya puedes entrar con tu contraseña nueva.", style = MaterialTheme.typography.bodyMedium, color = RupioColors.TextDim)
                }
                error?.let { Text(it, color = RupioColors.Danger, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            when (step) {
                1 -> TextButton(onClick = { send() }, enabled = !busy) { Text(if (busy) "Enviando…" else "Enviar código") }
                2 -> TextButton(onClick = { reset() }, enabled = !busy) { Text(if (busy) "Guardando…" else "Cambiar contraseña") }
                else -> TextButton(onClick = { onDone(email) }) { Text("Listo") }
            }
        },
        dismissButton = { if (step < 3) TextButton(onClick = { onDone(null) }, enabled = !busy) { Text("Cancelar") } },
    )
}
