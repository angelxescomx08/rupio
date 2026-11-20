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
import com.rupio.app.core.AppLanguage
import com.rupio.app.core.tr
import com.rupio.app.data.prefs.AppearancePrefs
import com.rupio.app.ui.support.SupporterAvatar
import com.rupio.app.ui.support.WallPreviewCard
import com.rupio.app.ui.support.findActivity
import com.rupio.app.ui.support.wallAccess
import com.rupio.app.ui.theme.Appearance
import com.rupio.app.ui.theme.ThemeMode

/** v1.9: el botón de salir sin cuenta (antes "Cerrar sesión", que no decía que se borra todo). */
val LOCAL_EXIT: String get() = tr("Borrar mis datos y salir", "Erase my data and exit")

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
    val wall by c.wall.state.collectAsStateWithLifecycle()
    val active by c.workouts.active.collectAsStateWithLifecycle(initialValue = null)
    val me = auth as? AuthState.SignedIn ?: return
    val s = settings ?: return
    // v2.0: idioma y tema. En Android 13+ el sistema recrea la pantalla al cambiar el idioma; antes se hace a mano.
    var langChoice by remember { mutableStateOf(AppearancePrefs.languageChoice(context)) }
    fun setLanguage(l: AppLanguage?) {
        langChoice = l
        AppearancePrefs.setLanguageChoice(context, l)
        c.onLanguageChanged()
        if (android.os.Build.VERSION.SDK_INT < 33) context.findActivity()?.recreate()
    }

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
        RupioTopBar(tr("Perfil", "Profile"), onBack = null)
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionCard {
                // Avatar y lápiz arriba, nombre abajo a todo lo ancho: un nombre largo nunca se parte a media palabra.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // v2.0: con el marco del metal de tu nivel de apoyo (recompensa desde ☕ Café).
                    SupporterAvatar(me.name, 64.dp, support.best)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { editName = true }) { Icon(Icons.Rounded.Edit, tr("Cambiar nombre", "Change name")) }
                }
                Spacer(Modifier.height(8.dp))
                Text(me.name, style = MaterialTheme.typography.titleLarge)
                support.best?.let { Spacer(Modifier.height(6.dp)); SupporterBadge(it, Modifier.clickable(onClickLabel = tr("Ver tus recompensas", "See your rewards")) { nav.navigate(Routes.SUPPORT) }) }
                Spacer(Modifier.height(4.dp))
                Text(if (me.localOnly) tr("Modo local (sin cuenta en la nube)", "Local mode (no cloud account)") else me.email, style = MaterialTheme.typography.bodySmall, color = RupioColors.TextDim)
                Text(Fmt.plural(count, tr("entrenamiento", "workout"), tr("entrenamientos", "workouts")), style = MaterialTheme.typography.labelMedium, color = RupioColors.TextFaint)
            }

            // v1.9: sin cuenta, lo más importante va primero: tus datos solo viven en este teléfono (antes estaba tras dos scrolls).
            if (me.localOnly) Surface(shape = RoundedCornerShape(20.dp), color = RupioColors.Warning.copy(alpha = 0.10f),
                border = androidx.compose.foundation.BorderStroke(1.dp, RupioColors.Warning.copy(alpha = 0.5f)), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.PhoneAndroid, null, tint = RupioColors.Warning)
                        Spacer(Modifier.width(10.dp))
                        Text(tr("Tus datos se guardan solo en este teléfono", "Your data is saved only on this phone"), style = MaterialTheme.typography.titleSmall)
                    }
                    Text(tr("No se respaldan: si pierdes o cambias el teléfono, se pierden. Con una cuenta se suben a la nube sin perder nada.", "It isn't backed up: if you lose or change your phone, it's gone. With an account it's uploaded to the cloud without losing anything."),
                        style = MaterialTheme.typography.bodySmall, color = RupioColors.TextDim, modifier = Modifier.padding(top = 4.dp))
                    Button(onClick = { nav.navigate(Routes.ACCOUNT) }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = 48.dp),
                        shape = RoundedCornerShape(14.dp)) { Icon(Icons.Rounded.CloudUpload, null); Spacer(Modifier.width(8.dp)); Text(tr("Crear cuenta y conservar mis datos", "Create an account and keep my data")) }
                }
            }

            // Apoyo: una fila amable y siempre a la mano (para quien quiera apoyar sin esperar al mensaje).
            // v1.9: el Muro de agradecimientos vive solo dentro de Apoya Rupio (antes también aquí, arriba de todo).
            if (support.isSupporter) {
                SectionCard(padding = 4.dp) {
                    SettingRow(Icons.Rounded.Favorite, tr("Tus recompensas", "Your rewards"), tr("Por apoyar Rupio: insignia, marco, colores e íconos", "For supporting Rupio: badge, ring, colors and icons"),
                        onClick = { nav.navigate(Routes.SUPPORT) })
                    HorizontalDivider(color = RupioColors.Outline.copy(alpha = 0.4f))
                    SettingRow(Icons.Rounded.EmojiEvents, tr("Ligas semanales", "Weekly leagues"), tr("Opcional: compite por volumen con personas de tu nivel", "Optional: compete on volume with people at your level"), onClick = { nav.navigate(Routes.LEAGUE) })
                }
            } else {
                Surface(onClick = { nav.navigate(Routes.SUPPORT) }, shape = RoundedCornerShape(20.dp), color = RupioColors.Primary.copy(alpha = 0.10f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, RupioColors.Primary.copy(alpha = 0.35f)), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("❤️", style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(tr("Apoya Rupio", "Support Rupio"), style = MaterialTheme.typography.titleMedium)
                            val from = support.price(com.rupio.app.domain.SupportTier.CAFE)
                            Text(tr("Es gratis y sin anuncios. Si te sirve, puedes apoyarlo con un pago único desde $from y llevarte recompensas de agradecimiento.",
                                "It's free with no ads. If it helps you, you can support it with a one-time payment from $from and get thank-you rewards."),
                                style = MaterialTheme.typography.bodySmall, color = RupioColors.TextDim)
                        }
                        Icon(Icons.Rounded.ChevronRight, null, tint = RupioColors.TextFaint)
                    }
                }
            }

            // v2.0: el muro de agradecimientos también aquí (antes solo dentro de Apoya Rupio y casi nadie lo veía).
            if (!support.isPatron) WallPreviewCard(wall, wallAccess(auth), onOpen = { nav.navigate(Routes.WALL) })

            // Aprende (antes era una pestaña; ahora Peso ocupa su lugar en la barra) y compartir, arriba y a un toque.
            SectionCard(padding = 4.dp) {
                if (!support.isSupporter) {
                    SettingRow(Icons.Rounded.EmojiEvents, tr("Ligas semanales", "Weekly leagues"), tr("Opcional: compite por volumen con personas de tu nivel", "Optional: compete on volume with people at your level"), onClick = { nav.navigate(Routes.LEAGUE) })
                    HorizontalDivider(color = RupioColors.Outline.copy(alpha = 0.4f))
                }
                SettingRow(Icons.Rounded.School, tr("Aprende", "Learn"), tr("Tutoriales, glosario con estudios y beneficios del ejercicio", "Tutorials, a glossary backed by studies and the benefits of exercise"), onClick = { nav.navigate(Routes.LEARN) })
                HorizontalDivider(color = RupioColors.Outline.copy(alpha = 0.4f))
                SettingRow(Icons.Rounded.Share, tr("Compartir Rupio", "Share Rupio"), tr("Mándasela a tus amigos del gym", "Send it to your gym friends"), onClick = { ShareApp.share(context) })
            }

            Text(tr("Preferencias", "Preferences"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
            SectionCard(padding = 4.dp) {
                // v2.0: idioma. Los nombres de cada idioma van en ese idioma (así los reconoce quien no entiende el otro).
                SettingRow(Icons.Rounded.Translate, tr("Idioma", "Language"), tr("Automático usa el idioma del teléfono", "Auto follows your phone's language"), below = true) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        listOf<AppLanguage?>(null, AppLanguage.ES, AppLanguage.EN).forEachIndexed { i, l ->
                            SegmentedButton(langChoice == l, { if (langChoice != l) setLanguage(l) }, SegmentedButtonDefaults.itemShape(i, 3)) {
                                Text(l?.nativeName ?: tr("Automático", "Auto"), maxLines = 1)
                            }
                        }
                    }
                }
                HorizontalDivider(color = RupioColors.Outline.copy(alpha = 0.4f))
                // v2.0: tema claro u oscuro (el claro se lee mejor con mucha luz, como en el gimnasio).
                SettingRow(Icons.Rounded.DarkMode, tr("Tema", "Theme"), tr("Automático sigue el modo oscuro del teléfono", "Auto follows your phone's dark mode"), below = true) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        listOf(ThemeMode.SYSTEM to tr("Automático", "Auto"), ThemeMode.LIGHT to tr("Claro", "Light"), ThemeMode.DARK to tr("Oscuro", "Dark")).forEachIndexed { i, (m, label) ->
                            SegmentedButton(Appearance.themeMode == m, { AppearancePrefs.setThemeChoice(context, m) }, SegmentedButtonDefaults.itemShape(i, 3)) { Text(label, maxLines = 1) }
                        }
                    }
                }
                HorizontalDivider(color = RupioColors.Outline.copy(alpha = 0.4f))
                SettingRow(Icons.Rounded.Scale, tr("Unidad de las pesas", "Lifting unit"), tr("Siempre verás también la otra unidad en pequeño", "You'll always see the other unit in small print too"), below = true) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        WeightUnit.entries.forEachIndexed { i, u ->
                            SegmentedButton(s.unit == u, { scope.launch { c.settings.setUnit(u) } }, SegmentedButtonDefaults.itemShape(i, 2)) { Text(if (u == WeightUnit.LB) tr("Libras (lb)", "Pounds (lb)") else tr("Kilos (kg)", "Kilos (kg)")) }
                        }
                    }
                }
                HorizontalDivider(color = RupioColors.Outline.copy(alpha = 0.4f))
                SettingRow(Icons.Rounded.MonitorWeight, tr("Unidad del peso corporal", "Body weight unit"), tr("Aparte de las pesas: casi todos se pesan en kilos", "Separate from lifting: most people weigh themselves in kilos"), below = true) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        listOf(WeightUnit.KG, WeightUnit.LB).forEachIndexed { i, u ->
                            SegmentedButton(s.bodyUnit == u, { scope.launch { c.settings.setBodyUnit(u) } }, SegmentedButtonDefaults.itemShape(i, 2)) { Text(if (u == WeightUnit.LB) tr("Libras (lb)", "Pounds (lb)") else tr("Kilos (kg)", "Kilos (kg)")) }
                        }
                    }
                }
                HorizontalDivider(color = RupioColors.Outline.copy(alpha = 0.4f))
                SettingRow(Icons.Rounded.Timer, tr("Descanso por defecto", "Default rest"), Fmt.restLabel(s.defaultRestSec) + tr(" · para ejercicios nuevos", " · for new exercises"), onClick = { restPick = true })
                HorizontalDivider(color = RupioColors.Outline.copy(alpha = 0.4f))
                SettingRow(Icons.Rounded.CalendarMonth, tr("La semana empieza en", "Week starts on"), null, below = true) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        listOf(1 to tr("Lunes", "Monday"), 7 to tr("Domingo", "Sunday")).forEachIndexed { i, (v, l) ->
                            SegmentedButton(s.weekStart == v, { scope.launch { c.settings.setWeekStart(v) } }, SegmentedButtonDefaults.itemShape(i, 2)) { Text(l) }
                        }
                    }
                }
                HorizontalDivider(color = RupioColors.Outline.copy(alpha = 0.4f))
                SettingRow(Icons.Rounded.NotificationsActive, tr("Aviso de fin de descanso", "Rest timer alert"), s.restAlert.label + tr(" · toca para probarlo", " · tap to try it"), onClick = { restAlert = true })
                HorizontalDivider(color = RupioColors.Outline.copy(alpha = 0.4f))
                SettingRow(Icons.Rounded.LightMode, tr("Pantalla encendida al entrenar", "Keep screen on while training"), tr("Para ver el descanso sin desbloquear el teléfono", "To see your rest without unlocking your phone")) {
                    Switch(s.keepScreenOn, { scope.launch { c.settings.setKeepScreenOn(it) } })
                }
                HorizontalDivider(color = RupioColors.Outline.copy(alpha = 0.4f))
                val offset = zone.rules.getOffset(Instant.now())
                SettingRow(Icons.Rounded.Public, tr("Zona horaria", "Time zone"), "${zone.getDisplayName(TextStyle.FULL, Fmt.locale)} (UTC${offset.id.replace("Z", "+00:00")}) · " +
                    tr("se detecta sola; las horas se muestran en tu zona y si entrenaste en otra se indica", "detected automatically; times show in your zone, and if you trained in another one it says so"))
            }

            Text(tr("Cuenta y datos", "Account and data"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
            SectionCard(padding = 4.dp) {
                if (!me.localOnly) {
                    // Un mensaje por causa (sesión vencida, sin internet, servidor caído, cambios rechazados, pendientes)
                    // y siempre cuántos cambios faltan. Al tocar, el detalle con lo que se puede hacer.
                    SettingRow(Icons.Rounded.CloudSync, summary.title, summary.body, iconTint = summary.level.color(), onClick = { syncUi.sheet = true }) {
                        TextButton(onClick = { if (summary.action == com.rupio.app.domain.SyncSummary.Action.SIGN_IN_AGAIN) syncUi.reauth = true else syncUi.sheet = true }) {
                            Text(when (summary.action) {
                                com.rupio.app.domain.SyncSummary.Action.SIGN_IN_AGAIN -> tr("Entrar", "Sign in")
                                com.rupio.app.domain.SyncSummary.Action.REVIEW -> tr("Revisar", "Review")
                                else -> tr("Ver", "View")
                            })
                        }
                    }
                }
                if (!me.localOnly) {
                    HorizontalDivider(color = RupioColors.Outline.copy(alpha = 0.4f))
                    SettingRow(Icons.Rounded.AlternateEmail, tr("Cambiar correo", "Change email"), me.email, onClick = { changeEmail = true })
                    HorizontalDivider(color = RupioColors.Outline.copy(alpha = 0.4f))
                }
                // v1.7 (N2): el consentimiento del peso corporal (dato de salud) se puede retirar; se borra el historial.
                val consentAt = s.healthConsentAt
                SettingRow(Icons.Rounded.MonitorHeart, tr("Permiso de peso corporal", "Body weight permission"),
                    if (consentAt != null) tr("Lo diste el ", "You gave it on ") + Dates.formatDay(Instant.ofEpochMilli(consentAt).atZone(zone).toLocalDate().toEpochDay().toInt()).let { d -> tr(d.replaceFirstChar { it.lowercase() }, d) } + tr(" · toca para retirarlo", " · tap to withdraw it")
                    else tr("No lo has dado. Se pide la primera vez que registras tu peso.", "You haven't given it. It's requested the first time you log your weight."),
                    onClick = if (consentAt != null) ({ withdraw = true }) else null)
                HorizontalDivider(color = RupioColors.Outline.copy(alpha = 0.4f))
                // v1.7 (N9): una copia de tus datos en CSV, para guardarla o abrirla en Excel / Google Sheets.
                SettingRow(Icons.Rounded.Download, tr("Exportar mis datos", "Export my data"),
                    if (exporting) tr("Preparando los archivos…", "Preparing the files…") else tr("Entrenamientos, series y peso corporal en archivos CSV", "Workouts, sets and body weight as CSV files"),
                    onClick = {
                        if (!exporting) scope.launch {
                            exporting = true
                            try { com.rupio.app.data.export.DataExport.share(context, com.rupio.app.data.export.DataExport.write(context, c.db)) }
                            catch (e: Exception) { c.notices.emit(com.rupio.app.Notice(tr("No se pudieron exportar tus datos (código E1). Inténtalo de nuevo.", "Your data couldn't be exported (code E1). Please try again."))) }
                            exporting = false
                        }
                    })
                HorizontalDivider(color = RupioColors.Outline.copy(alpha = 0.4f))
                SettingRow(Icons.Rounded.PrivacyTip, tr("Aviso de privacidad", "Privacy notice"), tr("Qué datos guarda Rupio y para qué", "What data Rupio keeps and why"), onClick = { privacy = true })
                if (!me.localOnly) {
                    HorizontalDivider(color = RupioColors.Outline.copy(alpha = 0.4f))
                    SettingRow(Icons.Rounded.DeleteForever, tr("Eliminar mi cuenta", "Delete my account"), tr("Borra tu cuenta y todos tus datos", "Deletes your account and all your data"), iconTint = RupioColors.Danger,
                        onClick = { nav.navigate(Routes.DELETE_ACCOUNT) })
                }
            }


            // v1.9: sin cuenta no hay sesión que cerrar: el botón dice lo que hace (borra todo lo del teléfono).
            OutlinedButton(onClick = { logout = true }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).heightIn(min = 50.dp), shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = RupioColors.Danger)) {
                Icon(if (me.localOnly) Icons.Rounded.DeleteForever else Icons.Rounded.Logout, null); Spacer(Modifier.width(6.dp))
                Text(if (me.localOnly) LOCAL_EXIT else tr("Cerrar sesión", "Sign out"))
            }
            Text("Rupio ${com.rupio.app.BuildConfig.VERSION_NAME} · " + tr("lista de ejercicios basada en free-exercise-db (dominio público)", "exercise list based on free-exercise-db (public domain)"), style = MaterialTheme.typography.labelSmall, color = RupioColors.TextFaint,
                modifier = Modifier.align(Alignment.CenterHorizontally))
            // Crédito que pide la licencia gratuita de RepDB: enlace visible en créditos (docs/IMAGENES_REPDB.md). No quitar.
            val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
            Text(tr("Imágenes de ejercicios: Exercise data by RepDB (repdb.co)", "Exercise images: Exercise data by RepDB (repdb.co)"), style = MaterialTheme.typography.labelSmall, color = RupioColors.TextDim,
                textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                modifier = Modifier.align(Alignment.CenterHorizontally)
                    .clickable(onClickLabel = tr("Abrir repdb.co", "Open repdb.co")) { runCatching { uriHandler.openUri("https://repdb.co") } }
                    .padding(top = 4.dp, bottom = 16.dp).heightIn(min = 32.dp))
        }
    }

    if (editName) RenameDialog(tr("Tu nombre", "Your name"), me.name, { editName = false }, below = { t ->
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) { InitialsAvatar(t, 36.dp); Spacer(Modifier.width(8.dp)); Text(tr("Así se verá tu avatar", "This is how your avatar will look"), color = RupioColors.TextDim) }
    }) { t -> if (t.isNotBlank()) c.auth.updateName(t.trim()); editName = false }
    if (restAlert) RestAlertDialog(s.restAlert, onPick = { scope.launch { c.settings.setRestAlert(it) } }, onClose = { restAlert = false })
    if (privacy) com.rupio.app.ui.legal.PrivacyPolicyDialog { privacy = false }
    if (changeEmail) ChangeEmailDialog(me.email) { changeEmail = false }
    if (withdraw) ConfirmDialog(tr("¿Retirar tu permiso?", "Withdraw your permission?"),
        if (me.localOnly) tr("Se borra todo tu historial de peso corporal de este teléfono. Si después quieres volver a registrar tu peso, te pediremos el permiso otra vez. No se puede deshacer.",
            "All your body weight history is erased from this phone. If you want to log your weight again later, we'll ask for permission again. This can't be undone.")
        else tr("Se borra todo tu historial de peso corporal de este teléfono y de tu cuenta. Si después quieres volver a registrar tu peso, te pediremos el permiso otra vez. No se puede deshacer.",
            "All your body weight history is erased from this phone and from your account. If you want to log your weight again later, we'll ask for permission again. This can't be undone."),
        tr("Retirar y borrar", "Withdraw and erase"), { withdraw = false }, danger = true) {
        withdraw = false
        scope.launch { c.withdrawHealthConsent(); c.notices.emit(com.rupio.app.Notice(tr("Listo. Se borró tu historial de peso corporal.", "Done. Your body weight history was erased."))) }
    }
    if (restPick) RestPickerDialog(tr("Descanso por defecto", "Default rest"), s.defaultRestSec, { restPick = false }, { scope.launch { c.settings.setDefaultRest(it) }; restPick = false })
    if (logout) ConfirmDialog(if (me.localOnly) tr("¿Borrar tus datos y salir?", "Erase your data and exit?") else tr("¿Cerrar sesión?", "Sign out?"),
        (if (me.localOnly) tr("Tus ${Fmt.plural(count, "entrenamiento", "entrenamientos")}, rutinas y pesos solo viven en este teléfono y se borrarán para siempre. Si quieres conservarlos, mejor usa «Crear cuenta y conservar mis datos».",
            "Your ${Fmt.plural(count, "workout", "workouts")}, routines and weights only live on this phone and will be erased forever. To keep them, use \"Create an account and keep my data\" instead.")
        else if (pending > 0) tr("Hay $pending cambios sin subir. Primero los guardaremos en la nube (tarda unos segundos); si no hay internet te preguntaremos qué hacer.",
            "There are $pending changes not uploaded yet. We'll save them to the cloud first (it takes a few seconds); if there's no internet, we'll ask you what to do.")
        else tr("Tus datos están en la nube y volverán al iniciar sesión.", "Your data is in the cloud and will come back when you sign in.")) +
            (if (active != null) tr("\n\nTienes un entrenamiento en curso: se descartará.", "\n\nYou have a workout in progress: it will be discarded.") else ""),
        if (me.localOnly) tr("Borrar y salir", "Erase and exit") else tr("Cerrar sesión", "Sign out"), { logout = false }, danger = true) {
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
        title = { Text(tr("Cambiar correo", "Change email")) },
        text = {
            Column {
                Text(tr("Por ahora el correo no se puede cambiar desde la app. Escríbenos desde tu correo actual ($current) con el correo nuevo y lo cambiamos por ti; tus datos se conservan.",
                    "For now, your email can't be changed from the app. Write to us from your current email ($current) with the new one and we'll change it for you; your data is kept."),
                    style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                Text(tr("Después cierra sesión y entra con el correo nuevo: te mandaremos un código para confirmarlo.", "Then sign out and sign in with the new email: we'll send you a code to confirm it."),
                    style = MaterialTheme.typography.bodySmall, color = RupioColors.TextDim)
                Spacer(Modifier.height(8.dp))
                androidx.compose.foundation.text.selection.SelectionContainer { Text(to, style = MaterialTheme.typography.titleSmall, color = RupioColors.Primary) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val uri = android.net.Uri.parse("mailto:$to?subject=" + android.net.Uri.encode(tr("Rupio: cambiar mi correo", "Rupio: change my email")) +
                    "&body=" + android.net.Uri.encode(tr("Correo actual de mi cuenta: $current\nCorreo nuevo: \n", "My account's current email: $current\nNew email: \n")))
                runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_SENDTO, uri).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                onClose()
            }) { Text(tr("Escribir correo", "Write email")) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(tr("Cerrar", "Close")) } },
    )
}

/** Cómo suena el fin del descanso, con tr("Probar", "Try it") para oírlo en el momento (antes no había ningún control). */
@Composable
private fun RestAlertDialog(current: RestAlertMode, onPick: (RestAlertMode) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var mode by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(tr("Aviso de fin de descanso", "Rest timer alert")) },
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
                    Icon(Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text(tr("Probar", "Try it"))
                }
                TextButton(onClick = {
                    runCatching {
                        ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                            .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                            .putExtra(android.provider.Settings.EXTRA_CHANNEL_ID, com.rupio.app.RupioApp.CHANNEL_REST)
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }) { Text(tr("Más ajustes del aviso en Android", "More alert settings in Android")) }
                Text(tr("«Automático» ya usa el volumen de la música cuando tienes audífonos o algo está sonando. Elige «Con el volumen de la música» si prefieres que siempre vaya por ahí.", "\"Automatic\" already uses the music volume when you have headphones on or something is playing. Choose \"With the music volume\" if you always want it that way."),
                    style = MaterialTheme.typography.bodySmall, color = RupioColors.TextDim)
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(tr("Listo", "Done")) } },
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
