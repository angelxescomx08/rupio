package com.rupio.app.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rupio.app.container
import com.rupio.app.data.auth.AuthException
import com.rupio.app.ui.components.ForgotPasswordDialog
import com.rupio.app.ui.components.LoadingButton
import com.rupio.app.ui.components.PasswordField
import com.rupio.app.ui.components.looksLikeEmail
import com.rupio.app.ui.legal.PrivacyPolicyDialog
import com.rupio.app.ui.theme.RupioColors
import kotlinx.coroutines.launch

/**
 * Inicio de sesión y registro.
 * [upgrade]: viene del modo sin cuenta ("Crear cuenta y conservar mis datos"): al terminar, todo lo del teléfono
 * pasa a la cuenta y se sube. [onBack] regresa a la app sin cambiar nada.
 */
@Composable
fun AuthScreen(upgrade: Boolean = false, onBack: (() -> Unit)? = null, onDone: () -> Unit = {}) {
    val c = container()
    val scope = rememberCoroutineScope()
    val count by c.workouts.observeCount().collectAsStateWithLifecycle(initialValue = 0)
    var signUp by rememberSaveable { mutableStateOf(upgrade) }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    // La contraseña NO va en rememberSaveable: Android guardaría su texto en el estado de la actividad.
    var pass by remember { mutableStateOf("") }
    var showPass by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var forgot by remember { mutableStateOf(false) }
    var privacy by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<String?>(null) }

    fun submit() {
        error = null; info = null
        if (signUp && name.isBlank()) { error = "Escribe tu nombre."; return }
        if (!looksLikeEmail(email)) { error = "Escribe un correo válido, como nombre@gmail.com."; return }
        if (pass.length < 8) { error = "La contraseña debe tener al menos 8 caracteres."; return }
        busy = true
        scope.launch {
            val adopt: (suspend (String) -> Unit)? = if (upgrade) { uid -> c.adoptLocalData(uid) } else null
            try {
                if (signUp) c.auth.signUp(name, email, pass, adopt) else c.auth.signIn(email, pass, adopt)
                onDone()
            }
            catch (e: AuthException) { error = e.message }
            // Nunca un mensaje técnico: solo un código para poder rastrearlo.
            catch (e: Exception) { error = "Algo salió mal (código A1). Inténtalo de nuevo." }
            busy = false
        }
    }

    Column(
        Modifier.fillMaxSize().background(RupioColors.Black).systemBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.offset(x = (-12).dp)) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Atrás") }
        } else Spacer(Modifier.height(32.dp))
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)).background(RupioColors.Primary.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Icon(if (upgrade) Icons.Rounded.CloudUpload else Icons.Rounded.FitnessCenter, null, tint = RupioColors.Primary, modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(if (upgrade) "Respalda tus datos" else "Rupio", style = MaterialTheme.typography.displaySmall)
        Text(
            when {
                upgrade -> "Crea una cuenta (o entra con la tuya) y tus $count ${if (count == 1) "entrenamiento" else "entrenamientos"}, rutinas y pesos de este teléfono se suben a la nube. No se pierde nada."
                signUp -> "Crea tu cuenta para guardar tu progreso"
                else -> "Entrena con método. Progresa con ciencia."
            }, style = MaterialTheme.typography.bodyLarge, color = RupioColors.TextDim)
        Spacer(Modifier.height(32.dp))

        AnimatedVisibility(signUp) {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text("Nombre") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.PersonFullName },
                    leadingIcon = { Icon(Icons.Rounded.Person, null) }, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next), shape = RoundedCornerShape(14.dp))
                Spacer(Modifier.height(12.dp))
            }
        }
        OutlinedTextField(email, { email = it.trim() }, label = { Text("Correo electrónico") }, singleLine = true,
            // Conectado al autocompletado de Android: los gestores de contraseñas lo llenan.
            modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.Username + ContentType.EmailAddress },
            leadingIcon = { Icon(Icons.Rounded.Email, null) }, shape = RoundedCornerShape(14.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next))
        Spacer(Modifier.height(12.dp))
        PasswordField(pass, { pass = it }, showPass, { showPass = !showPass }, newPassword = signUp, onDone = { submit() })
        if (signUp) Text("Mínimo 8 caracteres", style = MaterialTheme.typography.bodySmall, color = RupioColors.TextDim, modifier = Modifier.padding(start = 16.dp, top = 4.dp))
        else TextButton(onClick = { forgot = true; error = null }, modifier = Modifier.align(Alignment.End)) { Text("¿Olvidaste tu contraseña?") }

        (error ?: info)?.let {
            Spacer(Modifier.height(8.dp))
            val isError = error != null
            Surface(color = (if (isError) RupioColors.Danger else RupioColors.Success).copy(alpha = 0.12f), shape = RoundedCornerShape(12.dp)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (isError) Icons.Rounded.ErrorOutline else Icons.Rounded.CheckCircle, null, tint = if (isError) RupioColors.Danger else RupioColors.Success); Spacer(Modifier.width(8.dp))
                    Text(it, color = RupioColors.Text, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        LoadingButton(if (signUp) (if (upgrade) "Crear cuenta y subir mis datos" else "Crear cuenta") else (if (upgrade) "Entrar y subir mis datos" else "Iniciar sesión"),
            loading = busy, onClick = { submit() },
            modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp), loadingText = if (signUp) "Creando tu cuenta…" else "Entrando…")
        TextButton(onClick = { signUp = !signUp; error = null }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(if (signUp) "¿Ya tienes cuenta? Inicia sesión" else "¿No tienes cuenta? Regístrate")
        }
        Spacer(Modifier.height(8.dp))
        Text("Tu sesión se queda abierta en este teléfono. Si pasan semanas sin usar la app, te pediremos la contraseña otra vez; tus datos del teléfono no se pierden.",
            style = MaterialTheme.typography.bodySmall, color = RupioColors.TextDim)
        TextButton(onClick = { privacy = true }, contentPadding = PaddingValues(0.dp)) {
            Text(if (signUp) "Al crear tu cuenta aceptas el Aviso de privacidad" else "Aviso de privacidad", style = MaterialTheme.typography.bodySmall)
        }

        if (!upgrade) {
            Spacer(Modifier.height(20.dp))
            HorizontalDivider(color = RupioColors.Outline)
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = { c.auth.useLocalMode(name.ifBlank { email.substringBefore('@') }, c.restoredLocalOwner()) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Icon(Icons.Rounded.PhoneAndroid, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Probar sin cuenta")
            }
            Text("Sin cuenta, tus datos se guardan solo en este teléfono. Cuando quieras, desde Perfil creas una cuenta y se suben sin perder nada.",
                style = MaterialTheme.typography.bodySmall, color = RupioColors.TextDim, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }

    if (forgot) ForgotPasswordDialog(email) { changed ->
        forgot = false
        if (changed != null) { email = changed; pass = ""; signUp = false; info = "Listo. Entra con tu contraseña nueva." }
    }
    if (privacy) PrivacyPolicyDialog { privacy = false }
}
