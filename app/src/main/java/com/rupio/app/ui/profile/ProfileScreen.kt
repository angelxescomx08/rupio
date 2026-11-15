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
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.format.TextStyle

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
    val me = auth as? AuthState.SignedIn ?: return
    val s = settings ?: return

    var editName by remember { mutableStateOf(false) }
    var restPick by remember { mutableStateOf(false) }
    var logout by remember { mutableStateOf(false) }

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
                Text(if (me.localOnly) "Modo local (sin cuenta en la nube)" else me.email, style = MaterialTheme.typography.bodySmall, color = RupioColors.TextDim)
                Text("$count entrenamientos", style = MaterialTheme.typography.labelMedium, color = RupioColors.TextFaint)
            }

            Text("Preferencias", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
            SectionCard(padding = 4.dp) {
                SettingRow(Icons.Rounded.Scale, "Unidad de peso", "Siempre verás también la otra unidad en pequeño", below = true) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        WeightUnit.entries.forEachIndexed { i, u ->
                            SegmentedButton(s.unit == u, { scope.launch { c.settings.setUnit(u) } }, SegmentedButtonDefaults.itemShape(i, 2)) { Text(if (u == WeightUnit.LB) "Libras (lb)" else "Kilos (kg)") }
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
                val offset = zone.rules.getOffset(Instant.now())
                SettingRow(Icons.Rounded.Public, "Zona horaria", "${zone.getDisplayName(TextStyle.FULL, Fmt.locale)} (UTC${offset.id.replace("Z", "+00:00")}) · se detecta sola; las horas se muestran en tu zona y si entrenaste en otra se indica")
            }

            Text("Cuenta y datos", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
            SectionCard(padding = 4.dp) {
                if (!me.localOnly) {
                    SettingRow(Icons.Rounded.CloudSync, "Sincronización",
                        when {
                            sync.running -> "Sincronizando…"
                            sync.lastError != null -> "Sin conexión por ahora · se sube sola cuando haya internet"
                            pending > 0 -> "$pending cambios por subir"
                            sync.lastSuccess != null -> "Al día · " + Dates.formatTime(sync.lastSuccess!!, zone)
                            else -> "Aún no se sincroniza"
                        }) {
                        TextButton(onClick = { SyncEngine.now(context) }) { Text("Ahora") }
                    }
                    HorizontalDivider(color = RupioColors.Outline.copy(alpha = 0.4f))
                    SettingRow(Icons.Rounded.Lock, "Mantener sesión siempre", "No te pedirá la contraseña otra vez en este teléfono") {
                        Switch(s.keepSignedIn, { scope.launch { c.settings.setKeepSignedIn(it) } })
                    }
                } else {
                    SettingRow(Icons.Rounded.PhoneAndroid, "Tus datos", "Se guardan solo en este teléfono. Crea una cuenta para respaldarlos en la nube.")
                }
            }

            Text("Tu cuerpo", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
            SectionCard(padding = 4.dp) {
                SettingRow(Icons.Rounded.MonitorWeight, "Peso corporal", "Registra tu peso y mira tu tendencia", onClick = { nav.navigate(Routes.BODY_WEIGHT) })
            }

            OutlinedButton(onClick = { logout = true }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).height(50.dp), shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = RupioColors.Danger)) { Icon(Icons.Rounded.Logout, null); Spacer(Modifier.width(6.dp)); Text("Cerrar sesión") }
            Text("Rupio 1.0 · hecho con datos abiertos de free-exercise-db (dominio público)", style = MaterialTheme.typography.labelSmall, color = RupioColors.TextFaint,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 16.dp))
        }
    }

    if (editName) {
        var t by remember { mutableStateOf(me.name) }
        AlertDialog(onDismissRequest = { editName = false }, title = { Text("Tu nombre") },
            text = { Column { OutlinedTextField(t, { t = it }, singleLine = true); Spacer(Modifier.height(8.dp)); Row(verticalAlignment = Alignment.CenterVertically) { InitialsAvatar(t, 36.dp); Spacer(Modifier.width(8.dp)); Text("Así se verá tu avatar", color = RupioColors.TextDim) } } },
            confirmButton = { TextButton(onClick = { if (t.isNotBlank()) c.auth.updateName(t.trim()); editName = false }) { Text("Guardar") } },
            dismissButton = { TextButton(onClick = { editName = false }) { Text("Cancelar") } })
    }
    if (restPick) RestPickerDialog("Descanso por defecto", s.defaultRestSec, { restPick = false }, { scope.launch { c.settings.setDefaultRest(it) }; restPick = false })
    if (logout) ConfirmDialog("¿Cerrar sesión?",
        if (me.localOnly) "Estás en modo local: tus datos solo viven en este teléfono y se borrarán al salir."
        else if (pending > 0) "Hay $pending cambios sin subir. Intentaremos sincronizarlos antes de salir; si no hay internet se perderán."
        else "Tus datos están en la nube y volverán al iniciar sesión.", "Cerrar sesión", { logout = false }, danger = true) {
        logout = false
        scope.launch {
            if (!me.localOnly) runCatching { c.sync.sync() }
            c.auth.signOut(); c.wipeLocalData()
        }
    }
}

@Composable
private fun SettingRow(icon: ImageVector, title: String, sub: String?, onClick: (() -> Unit)? = null, below: Boolean = false, trailing: (@Composable () -> Unit)? = null) {
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
        Icon(icon, null, tint = RupioColors.Primary)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (sub != null) Text(sub, style = MaterialTheme.typography.labelMedium, color = RupioColors.TextDim)
        }
        if (trailing != null) { Spacer(Modifier.width(8.dp)); trailing() }
        else if (onClick != null) Icon(Icons.Rounded.ChevronRight, null, tint = RupioColors.TextFaint)
    }
}
