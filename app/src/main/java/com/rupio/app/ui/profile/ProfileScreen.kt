package com.rupio.app.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.rupio.app.Routes
import com.rupio.app.container
import com.rupio.app.core.Dates
import com.rupio.app.core.Fmt
import com.rupio.app.core.WeightUnit
import com.rupio.app.data.auth.AuthState
import com.rupio.app.data.remote.SyncEngine
import com.rupio.app.ui.components.*
import com.rupio.app.ui.theme.RupioColors
import com.rupio.app.ui.support.SupporterBadge
import com.rupio.app.data.prefs.RestAlertMode
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.format.TextStyle

/** v1.9: el botón de salir sin cuenta (antes "Cerrar sesión", que no decía que se borra todo). */
const val LOCAL_EXIT = "Borrar mis datos y salir"

@Composable
fun ProfileScreen(nav: NavHostController) {
    val c = container()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val auth by c.auth.state.collectAsStateWithLifecycle()
    val settings by c.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val sync by c.sync.status.collectAsStateWithLifecycle()
    val pending by c.db.workouts().observePending().collectAsStateWithLifecycle(initialValue = 0)
    val count by c.workouts.observeCount().collectAsStateWithLifecycle(initialValue = 0)
    val zone by c.zone.collectAsStateWithLifecycle()
    val support by c.support.state.collectAsStateWithLifecycle()
    val active by c.workouts.active.collectAsStateWithLifecycle(initialValue = null)
    val me = auth as? AuthState.SignedIn ?: return
    val s = settings ?: return

    var editName by remember { mutableStateOf(false) }
    var restPick by remember { mutableStateOf(false) }
    var logout by remember { mutableStateOf(false) }
    var restAlert by remember { mutableStateOf(false) }
    var privacy by remember { mutableStateOf(false) }
    var changeEmail by remember { mutableStateOf(false) }
    var withdraw by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    val syncUi = LocalSyncUi.current
    val summary = rememberSyncSummary()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        RupioTopBar("Perfil", onBack = null)
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionCard {
                // Avatar y lápiz arriba, nombre abajo a todo lo ancho: un nombre largo nunca se parte a media palabra.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    InitialsAvatar(me.name, 64.dp)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { editName = true }) { Icon(Icons.Rounded.Edit, "Cambiar nombre") }
                }
                Spacer(Modifier.height(8.dp))
                Text(me.name, style = MaterialTheme.typography.titleLarge)
                support.best?.let { Spacer(Modifier.height(6.dp)); SupporterBadge(it, Modifier.clickable { nav.navigate(Routes.SUPPORT) }) }
                Spacer(Modifier.height(4.dp))
                Text(if (me.localOnly) "Modo local (sin cuenta en la nube)" else me.email, style = MaterialTheme.typography.bodySmall, color = RupioColors.TextDim)
                Text(Fmt.plural(count, "entrenamiento", "entrenamientos"), style = MaterialTheme.typography.labelMedium, color = RupioColors.TextFaint)
            }

            // v1.9: sin cuenta, lo más importante va primero: tus datos solo viven en este teléfono (antes estaba tras dos scrolls).
            if (me.localOnly) Surface(shape = RoundedCornerShape(20.dp), color = RupioColors.Warning.copy(alpha = 0.10f),
                border = androidx.compose.foundation.BorderStroke(1.dp, RupioColors.Warning.copy(alpha = 0.5f)), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.PhoneAndroid, null, tint = RupioColors.Warning)
                        Spacer(Modifier.width(10.dp))
                        Text("Tus datos se guardan solo en este teléfono", style = MaterialTheme.typography.titleSmall)
                    }
                    Text("No se respaldan: si pierdes o cambias el teléfono, se pierden. Con una cuenta se suben a la nube sin perder nada.",
                        style = MaterialTheme.typography.bodySmall, color = RupioColors.TextDim, modifier = Modifier.padding(top = 4.dp))
                    Button(onClick = { nav.navigate(Routes.ACCOUNT) }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = 48.dp),
                        shape = RoundedCornerShape(14.dp)) { Icon(Icons.Rounded.CloudUpload, null); Spacer(Modifier.width(8.dp)); Text("Crear cuenta y conservar mis datos") }
                }
            }

            // Apoyo: una fila amable y siempre a la mano (para quien quiera apoyar sin esperar al mensaje).
            // v1.9: el Muro de agradecimientos vive solo dentro de Apoya Rupio (antes también aquí, arriba de todo).
            if (support.isSupporter) {
                SectionCard(padding = 4.dp) {
                    SettingRow(Icons.Rounded.Favorite, "Tus recompensas", "Color de la app" + (if (support.best?.hasGoldPerks == true) ", ícono dorado" else "") + " y tu insignia",
                        onClick = { nav.navigate(Routes.SUPPORT) })
                    HorizontalDivider(color = RupioColors.Outline.copy(alpha = 0.4f))
                    SettingRow(Icons.Rounded.EmojiEvents, "Ligas semanales", "Opcional: compite por volumen con personas de tu nivel", onClick = { nav.navigate(Routes.LEAGUE) })
                }
            } else {
                Surface(onClick = { nav.navigate(Routes.SUPPORT) }, shape = RoundedCornerShape(20.dp), color = RupioColors.Primary.copy(alpha = 0.10f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, RupioColors.Primary.copy(alpha = 0.35f)), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("❤️", style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Apoya Rupio", style = MaterialTheme.typography.titleMedium)
                            Text("Es gratis y sin anuncios. Si te sirve, puedes apoyarlo con un pago único desde " + com.rupio.app.domain.SupportTier.CAFE.let { support.price(it) } + ".",
                                style = MaterialTheme.typography.bodySmall, color = RupioColors.TextDim)
                        }
                        Icon(Icons.Rounded.ChevronRight, null, tint = RupioColors.TextFaint)
                    }
                }
            }

            // Aprende (antes era una pestaña; ahora Peso ocupa su lugar en la barra) y compartir, arriba y a un toque.
            SectionCard(padding = 4.dp) {
                if (!support.isSupporter) {
                    SettingRow(Icons.Rounded.EmojiEvents, "Ligas semanales", "Opcional: compite por volumen con personas de tu nivel", onClick = { nav.navigate(Routes.LEAGUE) })
                    HorizontalDivider(color = RupioColors.Outline.copy(alpha = 0.4f))
                }
                SettingRow(Icons.Rounded.School, "Aprende", "Tutoriales, glosario con estudios y beneficios del ejercicio", onClick = { nav.navigate(Routes.LEARN) })
                HorizontalDivider(color = RupioColors.Outline.copy(alpha = 0.4f))
                SettingRow(Icons.Rounded.Share, "Compartir Rupio", "Mándasela a tus amigos del gym", onClick = { ShareApp.share(context) })
            }

            Text("Preferencias", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
            SectionCard(padding = 4.dp) {
                SettingRow(Icons.Rounded.Scale, "Unidad de las pesas", "Siempre verás también la otra unidad en pequeño", below = true) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        WeightUnit.entries.forEachIndexed { i, u ->
                            SegmentedButton(s.unit == u, { scope.launch { c.settings.setUnit(u) } }, SegmentedButtonDefaults.itemShape(i, 2)) { Text(if (u == WeightUnit.LB) "Libras (lb)" else "Kilos (kg)") }
                        }
                    }
                }
                HorizontalDivider(color = RupioColors.Outline.copy(alpha = 0.4f))
                SettingRow(Icons.Rounded.MonitorWeight, "Unidad del peso corporal", "Aparte de las pesas: casi todos se pesan en kilos", below = true) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        listOf(WeightUnit.KG, WeightUnit.LB).forEachIndexed { i, u ->
                            SegmentedButton(s.bodyUnit == u, { scope.launch { c.settings.setBodyUnit(u) } }, SegmentedButtonDefaults.itemShape(i, 2)) { Text(if (u == WeightUnit.LB) "Libras (lb)" else "Kilos (kg)") }
                        }
                    }
                }
                HorizontalDivider(color = RupioColors.Outline.copy(alpha = 0.4f))
                SettingRow(Icons.Rounded.Timer, "Descanso por defecto", Fmt.restLabel(s.defaultRestSec) + " · para ejercicios nuevos", onClick = { restPick = true })
                HorizontalDivider(color = RupioColors.Outline.copy(alpha = 0.4f))
                SettingRow(Icons.Rounded.CalendarMonth, "La semana empieza en", null, below = true) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        listOf(1 to "Lunes", 7 to "Domingo").forEachIndexed { i, (v, l) ->
                            SegmentedButton(s.weekStart == v, { scope.launch { c.settings.setWeekStart(v) } }, SegmentedButtonDefaults.itemShape(i, 2)) { Text(l) }
                        }
                    }
                }
                HorizontalDivider(color = RupioColors.Outline.copy(alpha = 0.4f))
                SettingRow(Icons.Rounded.NotificationsActive, "Aviso de fin de descanso", s.restAlert.label + " · toca para probarlo", onClick = { restAlert = true })
                HorizontalDivider(color = RupioColors.Outline.copy(alpha = 0.4f))
                SettingRow(Icons.Rounded.LightMode, "Pantalla encendida al entrenar", "Para ver el descanso sin desbloquear el teléfono") {
                    Switch(s.keepScreenOn, { scope.launch { c.settings.setKeepScreenOn(it) } })
                }
                HorizontalDivider(color = RupioColors.Outline.copy(alpha = 0.4f))
                val offset = zone.rules.getOffset(Instant.now())
                SettingRow(Icons.Rounded.Public, "Zona horaria", "${zone.getDisplayName(TextStyle.FULL, Fmt.locale)} (UTC${offset.id.replace("Z", "+00:00")}) · se detecta sola; las horas se muestran en tu zona y si entrenaste en otra se indica")
            }

            Text("Cuenta y datos", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
            SectionCard(padding = 4.dp) {
                if (!me.localOnly) {
                    // Un mensaje por causa (sesión vencida, sin internet, servidor caído, cambios rechazados, pendientes)
                    // y siempre cuántos cambios faltan. Al tocar, el detalle con lo que se puede hacer.
                    SettingRow(Icons.Rounded.CloudSync, summary.title, summary.body, iconTint = summary.level.color(), onClick = { syncUi.sheet = true }) {
                        TextButton(onClick = { if (summary.action == com.rupio.app.domain.SyncSummary.Action.SIGN_IN_AGAIN) syncUi.reauth = true else syncUi.sheet = true }) {
                            Text(when (summary.action) {
                                com.rupio.app.domain.SyncSummary.Action.SIGN_IN_AGAIN -> "Entrar"
                                com.rupio.app.domain.SyncSummary.Action.REVIEW -> "Revisar"
                                else -> "Ver"
                            })
                        }
                    }
                }
                if (!me.localOnly) {
                    HorizontalDivider(color = RupioColors.Outline.copy(alpha = 0.4f))
                    SettingRow(Icons.Rounded.AlternateEmail, "Cambiar correo", me.email, onClick = { changeEmail = true })
                    HorizontalDivider(color = RupioColors.Outline.copy(alpha = 0.4f))
                }
                // v1.7 (N2): el consentimiento del peso corporal (dato de salud) se puede retirar; se borra el historial.
                val consentAt = s.healthConsentAt
                SettingRow(Icons.Rounded.MonitorHeart, "Permiso de peso corporal",
                    if (consentAt != null) "Lo diste el ${Dates.formatDay(Instant.ofEpochMilli(consentAt).atZone(zone).toLocalDate().toEpochDay().toInt()).replaceFirstChar { it.lowercase() }} · toca para retirarlo"
                    else "No lo has dado. Se pide la primera vez que registras tu peso.",
                    onClick = if (consentAt != null) ({ withdraw = true }) else null)
                HorizontalDivider(color = RupioColors.Outline.copy(alpha = 0.4f))
                // v1.7 (N9): una copia de tus datos en CSV, para guardarla o abrirla en Excel / Google Sheets.
                SettingRow(Icons.Rounded.Download, "Exportar mis datos",
                    if (exporting) "Preparando los archivos…" else "Entrenamientos, series y peso corporal en archivos CSV",
                    onClick = {
                        if (!exporting) scope.launch {
                            exporting = true
                            try { com.rupio.app.data.export.DataExport.share(context, com.rupio.app.data.export.DataExport.write(context, c.db)) }
                            catch (e: Exception) { c.notices.emit(com.rupio.app.Notice("No se pudieron exportar tus datos (código E1). Inténtalo de nuevo.")) }
                            exporting = false
                        }
                    })
                HorizontalDivider(color = RupioColors.Outline.copy(alpha = 0.4f))
                SettingRow(Icons.Rounded.PrivacyTip, "Aviso de privacidad", "Qué datos guarda Rupio y para qué", onClick = { privacy = true })
                if (!me.localOnly) {
                    HorizontalDivider(color = RupioColors.Outline.copy(alpha = 0.4f))
                    SettingRow(Icons.Rounded.DeleteForever, "Eliminar mi cuenta", "Borra tu cuenta y todos tus datos", iconTint = RupioColors.Danger,
                        onClick = { nav.navigate(Routes.DELETE_ACCOUNT) })
                }
            }


            // v1.9: sin cuenta no hay sesión que cerrar: el botón dice lo que hace (borra todo lo del teléfono).
            OutlinedButton(onClick = { logout = true }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).heightIn(min = 50.dp), shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = RupioColors.Danger)) {
                Icon(if (me.localOnly) Icons.Rounded.DeleteForever else Icons.Rounded.Logout, null); Spacer(Modifier.width(6.dp))
                Text(if (me.localOnly) LOCAL_EXIT else "Cerrar sesión")
            }
            Text("Rupio ${com.rupio.app.BuildConfig.VERSION_NAME} · lista de ejercicios basada en free-exercise-db (dominio público)", style = MaterialTheme.typography.labelSmall, color = RupioColors.TextFaint,
                modifier = Modifier.align(Alignment.CenterHorizontally))
            // Crédito que pide la licencia gratuita de RepDB: enlace visible en créditos (docs/IMAGENES_REPDB.md). No quitar.
            val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
            Text("Imágenes de ejercicios: Exercise data by RepDB (repdb.co)", style = MaterialTheme.typography.labelSmall, color = RupioColors.TextDim,
                textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                modifier = Modifier.align(Alignment.CenterHorizontally)
                    .clickable(onClickLabel = "Abrir repdb.co") { runCatching { uriHandler.openUri("https://repdb.co") } }
                    .padding(top = 4.dp, bottom = 16.dp).heightIn(min = 32.dp))
        }
    }

    if (editName) RenameDialog("Tu nombre", me.name, { editName = false }, below = { t ->
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) { InitialsAvatar(t, 36.dp); Spacer(Modifier.width(8.dp)); Text("Así se verá tu avatar", color = RupioColors.TextDim) }
    }) { t -> if (t.isNotBlank()) c.auth.updateName(t.trim()); editName = false }
    if (restAlert) RestAlertDialog(s.restAlert, onPick = { scope.launch { c.settings.setRestAlert(it) } }, onClose = { restAlert = false })
    if (privacy) com.rupio.app.ui.legal.PrivacyPolicyDialog { privacy = false }
    if (changeEmail) ChangeEmailDialog(me.email) { changeEmail = false }
    if (withdraw) ConfirmDialog("¿Retirar tu permiso?",
        "Se borra todo tu historial de peso corporal de este teléfono" + (if (me.localOnly) "" else " y de tu cuenta") +
            ". Si después quieres volver a registrar tu peso, te pediremos el permiso otra vez. No se puede deshacer.",
        "Retirar y borrar", { withdraw = false }, danger = true) {
        withdraw = false
        scope.launch { c.withdrawHealthConsent(); c.notices.emit(com.rupio.app.Notice("Listo. Se borró tu historial de peso corporal.")) }
    }
    if (restPick) RestPickerDialog("Descanso por defecto", s.defaultRestSec, { restPick = false }, { scope.launch { c.settings.setDefaultRest(it) }; restPick = false })
    if (logout) ConfirmDialog(if (me.localOnly) "¿Borrar tus datos y salir?" else "¿Cerrar sesión?",
        (if (me.localOnly) "Tus ${Fmt.plural(count, "entrenamiento", "entrenamientos")}, rutinas y pesos solo viven en este teléfono y se borrarán para siempre. Si quieres conservarlos, mejor usa «Crear cuenta y conservar mis datos»."
        else if (pending > 0) "Hay $pending cambios sin subir. Primero los guardaremos en la nube (tarda unos segundos); si no hay internet te preguntaremos qué hacer."
        else "Tus datos están en la nube y volverán al iniciar sesión.") +
            (if (active != null) "\n\nTienes un entrenamiento en curso: se descartará." else ""),
        if (me.localOnly) "Borrar y salir" else "Cerrar sesión", { logout = false }, danger = true) {
        logout = false
        // Pasos visibles (subir → cerrar → borrar) en una pantalla que explica qué pasa: ver SignOutOverlay.
        c.signOut.start(me.localOnly)
    }
}

/**
 * v1.7 (N6): cambiar el correo. El servicio de cuentas (Neon Auth) tiene apagado el cambio de correo y no hay forma
 * de encenderlo, así que se pide por correo al responsable: escribir DESDE el correo actual prueba que la cuenta es
 * tuya. El cambio se hace en el servidor (ver docs/QA_V17.md) y, al volver a entrar, la app pide el código que llega
 * al correo nuevo.
 */
@Composable
private fun ChangeEmailDialog(current: String, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val to = com.rupio.app.ui.legal.PrivacyPolicy.CONTACT_EMAIL
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Cambiar correo") },
        text = {
            Column {
                Text("Por ahora el correo no se puede cambiar desde la app. Escríbenos desde tu correo actual ($current) con el correo nuevo y lo cambiamos por ti; tus datos se conservan.",
                    style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                Text("Después cierra sesión y entra con el correo nuevo: te mandaremos un código para confirmarlo.",
                    style = MaterialTheme.typography.bodySmall, color = RupioColors.TextDim)
                Spacer(Modifier.height(8.dp))
                androidx.compose.foundation.text.selection.SelectionContainer { Text(to, style = MaterialTheme.typography.titleSmall, color = RupioColors.Primary) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val uri = android.net.Uri.parse("mailto:$to?subject=" + android.net.Uri.encode("Rupio: cambiar mi correo") +
                    "&body=" + android.net.Uri.encode("Correo actual de mi cuenta: $current\nCorreo nuevo: \n"))
                runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_SENDTO, uri).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                onClose()
            }) { Text("Escribir correo") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cerrar") } },
    )
}

/** Cómo suena el fin del descanso, con "Probar" para oírlo en el momento (antes no había ningún control). */
@Composable
private fun RestAlertDialog(current: RestAlertMode, onPick: (RestAlertMode) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var mode by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Aviso de fin de descanso") },
        text = {
            Column(Modifier.selectableGroup()) {
                RestAlertMode.entries.forEach { m ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .selectable(selected = mode == m, role = Role.RadioButton, onClick = { mode = m; onPick(m) })
                        .padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = mode == m, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(m.label, style = MaterialTheme.typography.bodyLarge)
                            Text(m.detail, style = MaterialTheme.typography.bodySmall, color = RupioColors.TextDim)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { com.rupio.app.timer.RestAlert.preview(ctx, mode) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Probar")
                }
                TextButton(onClick = {
                    runCatching {
                        ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                            .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                            .putExtra(android.provider.Settings.EXTRA_CHANNEL_ID, com.rupio.app.RupioApp.CHANNEL_REST)
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }) { Text("Más ajustes del aviso en Android") }
                Text("Si escuchas música con audífonos, elige «Con el volumen de la música» para que el aviso no se pierda.",
                    style = MaterialTheme.typography.bodySmall, color = RupioColors.TextDim)
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Listo") } },
    )
}

@Composable
private fun SettingRow(icon: ImageVector, title: String, sub: String?, onClick: (() -> Unit)? = null, below: Boolean = false,
                       iconTint: androidx.compose.ui.graphics.Color = RupioColors.Primary, trailing: (@Composable () -> Unit)? = null) {
    if (below && trailing != null) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = RupioColors.Primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.bodyLarge)
                    if (sub != null) Text(sub, style = MaterialTheme.typography.labelMedium, color = RupioColors.TextDim)
                }
            }
            Spacer(Modifier.height(10.dp))
            Box(Modifier.padding(start = 36.dp)) { trailing() }
        }
        return
    }
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = iconTint)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (sub != null) Text(sub, style = MaterialTheme.typography.labelMedium, color = RupioColors.TextDim)
        }
        if (trailing != null) { Spacer(Modifier.width(8.dp)); trailing() }
        else if (onClick != null) Icon(Icons.Rounded.ChevronRight, null, tint = RupioColors.TextFaint)
    }
}
