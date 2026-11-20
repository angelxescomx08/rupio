package com.rupio.app.ui.legal


import com.rupio.app.core.tr
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.rupio.app.container
import com.rupio.app.data.auth.AuthException
import com.rupio.app.data.auth.AuthState
import com.rupio.app.safePop
import com.rupio.app.ui.components.ForgotPasswordDialog
import com.rupio.app.ui.components.LoadingButton
import com.rupio.app.ui.components.PasswordField
import com.rupio.app.ui.components.RupioTopBar
import com.rupio.app.ui.components.SectionCard
import com.rupio.app.ui.theme.RupioColors
import kotlinx.coroutines.launch

/**
 * tr("Eliminar mi cuenta", "Delete my account") (requisito de Google Play). Dice exactamente qué se borra, pide la contraseña para confirmar
 * que eres tú y borra todo en el servidor en una sola operación (si falla, no se borra nada).
 */
@Composable
fun DeleteAccountScreen(nav: NavHostController) {
    val c = container()
    val scope = rememberCoroutineScope()
    val me = c.auth.state.collectAsStateWithLifecycle().value as? AuthState.SignedIn ?: return
    val count by c.workouts.observeCount().collectAsStateWithLifecycle(initialValue = 0)
    var pass by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    var sure by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var forgot by remember { mutableStateOf(false) }

    fun delete() {
        if (pass.isEmpty()) { error = tr("Escribe tu contraseña para confirmar.", "Enter your password to confirm."); return }
        busy = true; error = null
        scope.launch {
            try { c.deleteAccount(pass); c.accountDeleted.value = true }
            catch (e: AuthException) { error = e.message }
            catch (e: Exception) { error = tr("Algo salió mal (código D1). No se borró nada; inténtalo de nuevo.", "Something went wrong (code D1). Nothing was erased; please try again.") }
            busy = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        RupioTopBar(tr("Eliminar mi cuenta", "Delete my account"), onBack = { if (!busy) nav.safePop() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.DeleteForever, null, tint = RupioColors.Danger)
                    Spacer(Modifier.width(10.dp))
                    Text(tr("Se borra para siempre", "It's erased forever"), style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(8.dp))
                Text(tr("De la nube y de este teléfono se eliminan:", "Deleted from the cloud and this phone:"), style = MaterialTheme.typography.bodyMedium, color = RupioColors.TextDim)
                Spacer(Modifier.height(6.dp))
                (listOf(
                    tr("Tu cuenta (${me.email})", "Your account (${me.email})"),
                    tr("Tus $count ${if (count == 1) "entrenamiento" else "entrenamientos"}, con sus series y récords", "Your $count ${if (count == 1) "workout" else "workouts"}, with their sets and PRs"),
                    tr("Tus rutinas y ejercicios personalizados", "Your routines and custom exercises"),
                    tr("Tu registro de peso corporal", "Your body weight log"),
                ) + (if (com.rupio.app.container().support.state.collectAsState().value.isPatron) listOf(tr("Tu lugar en el muro de agradecimientos", "Your place on the thank-you wall")) else emptyList())
                ).forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                Spacer(Modifier.height(8.dp))
                Text(tr("No se puede deshacer. Las copias de seguridad automáticas del servidor se borran solas en un máximo de 30 días. " +
                    "Tus compras de apoyo siguen ligadas a tu cuenta de Google Play.",
                    "This can't be undone. The server's automatic backups are erased on their own within 30 days at most. " +
                        "Your supporter purchases stay linked to your Google Play account."), style = MaterialTheme.typography.bodySmall, color = RupioColors.TextDim)
            }
            SectionCard {
                Text(tr("Confirma que eres tú", "Confirm it's you"), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                PasswordField(pass, { pass = it }, show, { show = !show }, onDone = { if (sure) delete() })
                TextButton(onClick = { forgot = true }, contentPadding = PaddingValues(0.dp)) { Text(tr("¿Olvidaste tu contraseña?", "Forgot your password?")) }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(sure, { sure = it })
                    Text(tr("Entiendo que mis datos se borran y no se pueden recuperar", "I understand my data is erased and can't be recovered"), style = MaterialTheme.typography.bodyMedium)
                }
            }
            error?.let {
                Surface(color = RupioColors.Danger.copy(alpha = 0.12f), shape = RoundedCornerShape(12.dp), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                    Text(it, Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
            LoadingButton(tr("Eliminar mi cuenta para siempre", "Delete my account forever"), loading = busy, onClick = { delete() }, enabled = sure,
                modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp), loadingText = tr("Eliminando…", "Deleting…"),
                container = RupioColors.Danger, content = RupioColors.OnFill)
            TextButton(onClick = { nav.safePop() }, enabled = !busy, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text(tr("Mejor no", "Never mind")) }
            Spacer(Modifier.height(16.dp))
        }
    }
    if (forgot) ForgotPasswordDialog(me.email) { forgot = false }
}

/** Confirmación después de borrar (se ve aunque la app ya esté en la pantalla de inicio de sesión). */
@Composable
fun AccountDeletedDialog(onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        icon = { Icon(Icons.Rounded.CheckCircle, null, tint = RupioColors.Success) },
        title = { Text(tr("Tu cuenta se eliminó", "Your account was deleted")) },
        text = { Text(tr("Borramos tu cuenta y todos tus datos de la nube y de este teléfono. Gracias por haber entrenado con Rupio.", "We deleted your account and all your data from the cloud and this phone. Thanks for training with Rupio."), color = RupioColors.TextDim) },
        confirmButton = { TextButton(onClick = onClose) { Text(tr("Entendido", "Got it")) } },
    )
}
