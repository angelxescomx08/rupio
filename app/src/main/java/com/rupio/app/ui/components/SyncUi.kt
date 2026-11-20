package com.rupio.app.ui.components


import com.rupio.app.core.tr
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

/** Abre, desde cualquier pantalla, el detalle del respaldo o tr("Entrar de nuevo", "Sign in again"). Lo pinta [SyncUiHost] en MainShell. */
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
    val label = tr("Respaldo: ${s.title}", "Backup: ${s.title}") + if (s.pending > 0) tr(", ${s.pending} pendientes", ", ${s.pending} pending") else ""
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
            TextButton(onClick = { ui.reauth = true }, modifier = Modifier.align(Alignment.End)) { Text(tr("Entrar de nuevo", "Sign in again")) }
        }
    }
}

/** Pinta el detalle del respaldo y tr("Entrar de nuevo", "Sign in again") (una sola vez, en MainShell). */
@Composable
fun SyncUiHost(controller: SyncUiController, onCreateAccount: () -> Unit, onFixSet: (String) -> Unit = {}) {
    if (controller.sheet) SyncSheet(onDismiss = { controller.sheet = false },
        onSignInAgain = { controller.sheet = false; controller.reauth = true },
        onCreateAccount = { controller.sheet = false; onCreateAccount() },
        onFixSet = { controller.sheet = false; onFixSet(it) })
    if (controller.reauth) ReauthDialog { controller.reauth = false }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SyncSheet(onDismiss: () -> Unit, onSignInAgain: () -> Unit, onCreateAccount: () -> Unit, onFixSet: (String) -> Unit) {
    val c = container()
    val s = rememberSyncSummary()
    val scope = rememberCoroutineScope()
    var note by remember { mutableStateOf<String?>(null) }
    var rejected by remember { mutableStateOf<List<RejectedChange>>(emptyList()) }
    LaunchedEffect(s.action, s.pending) { rejected = if (s.action == SyncSummary.Action.REVIEW) c.sync.describeRejected() else emptyList() }

    /** tr("Sincronizar ahora", "Sync now"): sin red lo dice en vez de quedarse callado (antes se tocaba una y otra vez sin respuesta). */
    fun syncNow() {
        if (!c.isOnline()) { note = tr("Sin internet: se subirá cuando vuelva la conexión.", "No internet: it will upload when the connection is back."); return }
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
            if (s.level == SyncSummary.Level.BUSY && s.uploading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = RupioColors.Primary, trackColor = RupioColors.Surface3)
            note?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = RupioColors.Warning, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }

            rejected.forEach { r ->
                Surface(shape = RoundedCornerShape(14.dp), color = RupioColors.Surface2) {
                    Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 12.dp)) {
                        Text(r.title, style = MaterialTheme.typography.titleSmall)
                        Text(r.detail, style = MaterialTheme.typography.bodySmall, color = RupioColors.TextDim)
                        Row(Modifier.align(Alignment.End)) {
                            TextButton(onClick = { scope.launch { c.sync.discardRejected(r.table, r.key); rejected = c.sync.describeRejected() } },
                                colors = ButtonDefaults.textButtonColors(contentColor = RupioColors.Danger)) { Text(tr("Descartar", "Discard")) }
                            TextButton(onClick = { c.sync.retryRejected(r.table, r.key); rejected = rejected - r; syncNow() }) { Text(tr("Reintentar", "Retry")) }
                            // v1.7 (N1): una serie rechazada se corrige en el editor del entreno (peso, reps, tiempo o tipo).
                            if (r.table == "workout_sets") TextButton(onClick = { onFixSet(r.key) }) { Text(tr("Corregir", "Fix")) }
                        }
                    }
                }
            }
            if (rejected.isNotEmpty()) Text(tr("«Descartar» deja el dato en tu teléfono tal como está, pero ya no intenta subirlo.", "“Discard” keeps the data on your phone as it is, but stops trying to upload it."),
                style = MaterialTheme.typography.bodySmall, color = RupioColors.TextDim)

            when (s.action) {
                SyncSummary.Action.SIGN_IN_AGAIN -> Button(onClick = onSignInAgain, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(tr("Entrar de nuevo", "Sign in again")) }
                SyncSummary.Action.CREATE_ACCOUNT -> Button(onClick = onCreateAccount, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(tr("Crear cuenta y conservar mis datos", "Create an account and keep my data")) }
                else -> if (s.level != SyncSummary.Level.LOCAL) OutlinedButton(onClick = { syncNow() }, enabled = s.level != SyncSummary.Level.BUSY || s.pending > 0,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Icon(Icons.Rounded.Sync, null); Spacer(Modifier.width(6.dp)); Text(if (s.level == SyncSummary.Level.BUSY && s.title.endsWith("…")) tr("Sincronizando…", "Syncing…") else tr("Sincronizar ahora", "Sync now"))
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
        if (pass.isEmpty()) { error = tr("Escribe tu contraseña.", "Enter your password."); return }
        busy = true; error = null
        scope.launch {
            try { c.auth.reauthenticate(pass); c.scope.launch { c.sync.sync() }; onDone() }
            catch (e: AuthException) { error = e.message }
            catch (e: Exception) { error = tr("Algo salió mal (código E1). Inténtalo de nuevo.", "Something went wrong (code E1). Please try again.") }
            busy = false
        }
    }
    if (forgot) { ForgotPasswordDialog(me.email) { forgot = false }; return }
    AlertDialog(
        onDismissRequest = { if (!busy) onDone() },
        icon = { Icon(Icons.Rounded.Lock, null) },
        title = { Text(tr("Entrar de nuevo", "Sign in again")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(tr("Por seguridad tu sesión venció. Escribe tu contraseña para volver a respaldar; tus entrenos de este teléfono se quedan como están.", "For your security, your session expired. Enter your password to resume backups; the workouts on this phone stay as they are."),
                    style = MaterialTheme.typography.bodyMedium, color = RupioColors.TextDim)
                Text(me.email, style = MaterialTheme.typography.titleSmall)
                PasswordField(pass, { pass = it }, show, { show = !show }, onDone = { submit() })
                error?.let { Text(it, color = RupioColors.Danger, style = MaterialTheme.typography.bodySmall) }
                TextButton(onClick = { forgot = true }, contentPadding = PaddingValues(0.dp)) { Text(tr("¿Olvidaste tu contraseña?", "Forgot your password?")) }
            }
        },
        confirmButton = { TextButton(onClick = { submit() }, enabled = !busy) { Text(if (busy) tr("Entrando…", "Signing in…") else tr("Entrar", "Sign in")) } },
        dismissButton = { TextButton(onClick = onDone, enabled = !busy) { Text(tr("Ahora no", "Not now")) } },
    )
}

/** Campo de contraseña con "mostrar" y conectado al autocompletado / gestores de contraseñas. */
@Composable
fun PasswordField(value: String, onValue: (String) -> Unit, show: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier,
                  label: String = tr("Contraseña", "Password"), newPassword: Boolean = false, onDone: () -> Unit = {},
                  /** v1.9: error de ESTE campo, debajo de él y en rojo; [hint] si no hay error. */
                  error: String? = null, hint: String? = null) {
    OutlinedTextField(value, onValue, label = { Text(label) }, singleLine = true,
        isError = error != null, supportingText = (error ?: hint)?.let { t -> { Text(t) } },
        modifier = modifier.fillMaxWidth().semantics { contentType = if (newPassword) ContentType.NewPassword else ContentType.Password },
        leadingIcon = { Icon(Icons.Rounded.Lock, null) }, shape = RoundedCornerShape(14.dp),
        visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = { IconButton(onClick = onToggle) { Icon(if (show) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, if (show) tr("Ocultar contraseña", "Hide password") else tr("Mostrar contraseña", "Show password")) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }))
}

/** Formato de correo razonable (algo@algo.algo, sin espacios). */
fun looksLikeEmail(s: String): Boolean = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$").matches(s.trim())

/**
 * tr("¿Olvidaste tu contraseña?", "Forgot your password?") en dos pasos, sin salir de la app: 1) correo → Neon Auth manda un código de 6 dígitos;
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
        if (!looksLikeEmail(email)) { error = tr("Escribe un correo válido, como nombre@gmail.com.", "Enter a valid email, like name@gmail.com."); return }
        busy = true; error = null
        scope.launch {
            try { c.auth.requestPasswordReset(email); step = 2 } catch (e: AuthException) { error = e.message }
            busy = false
        }
    }
    fun reset() {
        if (code.trim().length < 6) { error = tr("Escribe el código de 6 dígitos del correo.", "Enter the 6-digit code from the email."); return }
        if (pass.length < 8) { error = tr("La contraseña nueva debe tener al menos 8 caracteres.", "The new password must be at least 8 characters."); return }
        busy = true; error = null
        scope.launch {
            try { c.auth.resetPassword(email, code, pass); step = 3 } catch (e: AuthException) { error = e.message }
            busy = false
        }
    }
    AlertDialog(
        onDismissRequest = { if (!busy) onDone(null) },
        title = { Text(if (step == 3) tr("Contraseña cambiada", "Password changed") else tr("Recuperar contraseña", "Reset password")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (step) {
                    1 -> {
                        Text(tr("Te enviaremos un código de 6 dígitos a tu correo.", "We'll send a 6-digit code to your email."), style = MaterialTheme.typography.bodyMedium, color = RupioColors.TextDim)
                        OutlinedTextField(email, { email = it.trim() }, label = { Text(tr("Correo electrónico", "Email")) }, singleLine = true,
                            modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.EmailAddress },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { send() }), shape = RoundedCornerShape(14.dp))
                    }
                    2 -> {
                        Text(tr("Si hay una cuenta con $email, te llegó un código. Revisa también la carpeta de spam. Vence en unos minutos.", "If there's an account with $email, a code is on its way. Check your spam folder too. It expires in a few minutes."),
                            style = MaterialTheme.typography.bodyMedium, color = RupioColors.TextDim)
                        OutlinedTextField(code, { code = it.filter(Char::isDigit).take(6) }, label = { Text(tr("Código", "Code")) }, singleLine = true,
                            modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.SmsOtpCode },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Next), shape = RoundedCornerShape(14.dp))
                        PasswordField(pass, { pass = it }, show, { show = !show }, label = tr("Contraseña nueva", "New password"), newPassword = true, onDone = { reset() })
                        TextButton(onClick = { code = ""; send() }, contentPadding = PaddingValues(0.dp), enabled = !busy) { Text(tr("Enviar otro código", "Send another code")) }
                    }
                    else -> Text(tr("Ya puedes entrar con tu contraseña nueva.", "You can now sign in with your new password."), style = MaterialTheme.typography.bodyMedium, color = RupioColors.TextDim)
                }
                error?.let { Text(it, color = RupioColors.Danger, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            when (step) {
                1 -> TextButton(onClick = { send() }, enabled = !busy) { Text(if (busy) tr("Enviando…", "Sending…") else tr("Enviar código", "Send code")) }
                2 -> TextButton(onClick = { reset() }, enabled = !busy) { Text(if (busy) tr("Guardando…", "Saving…") else tr("Cambiar contraseña", "Change password")) }
                else -> TextButton(onClick = { onDone(email) }) { Text(tr("Listo", "Done")) }
            }
        },
        dismissButton = { if (step < 3) TextButton(onClick = { onDone(null) }, enabled = !busy) { Text(tr("Cancelar", "Cancel")) } },
    )
}
