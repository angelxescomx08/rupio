package com.rupio.app.ui.auth


import com.rupio.app.core.tr
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

/** v1.9: revisa todos los campos de una vez; cada error va debajo de su campo. */
object AuthValidation {
    data class Fields(val name: String?, val email: String?, val password: String?) { val ok get() = name == null && email == null && password == null }

    fun check(signUp: Boolean, name: String, email: String, pass: String) = Fields(
        name = if (signUp && name.isBlank()) tr("Escribe tu nombre.", "Enter your name.") else null,
        email = if (!looksLikeEmail(email)) tr("Escribe un correo válido, como nombre@gmail.com.", "Enter a valid email, like name@gmail.com.") else null,
        password = if (pass.length < 8) tr("La contraseña debe tener al menos 8 caracteres.", "The password must be at least 8 characters.") else null,
    )
}

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
    // v1.9 (N10): desde el modo sin cuenta ya sabemos cómo te llamas (antes el campo salía vacío).
    val localName = (c.auth.state.value as? com.rupio.app.data.auth.AuthState.SignedIn)?.takeIf { upgrade && it.localOnly }?.name
        ?.takeIf { it.isNotBlank() && it != "Atleta" && it != "Athlete" }.orEmpty()
    var name by rememberSaveable { mutableStateOf(localName) }
    var email by rememberSaveable { mutableStateOf("") }
    // La contraseña NO va en rememberSaveable: Android guardaría su texto en el estado de la actividad.
    var pass by remember { mutableStateOf("") }
    var showPass by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var forgot by remember { mutableStateOf(false) }
    var privacy by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<String?>(null) }

    // v1.9: cada campo con su error, todos a la vez (antes salía uno solo, en una caja lejos del campo).
    var nameError by remember { mutableStateOf<String?>(null) }
    var emailError by remember { mutableStateOf<String?>(null) }
    var passError by remember { mutableStateOf<String?>(null) }

    fun submit() {
        error = null; info = null
        val f = AuthValidation.check(signUp, name, email, pass)
        nameError = f.name; emailError = f.email; passError = f.password
        if (!f.ok) return
        busy = true
        scope.launch {
            val adopt: (suspend (String) -> Unit)? = if (upgrade) { uid -> c.adoptLocalData(uid) } else null
            try {
                if (signUp) c.auth.signUp(name, email, pass, adopt) else c.auth.signIn(email, pass, adopt)
                onDone()
            }
            catch (e: AuthException) { error = e.message }
            // Nunca un mensaje técnico: solo un código para poder rastrearlo.
            catch (e: Exception) { error = tr("Algo salió mal (código A1). Inténtalo de nuevo.", "Something went wrong (code A1). Please try again.") }
            busy = false
        }
    }

    Column(
        Modifier.fillMaxSize().background(RupioColors.Background).systemBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.offset(x = (-12).dp)) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, tr("Atrás", "Back")) }
        } else Spacer(Modifier.height(32.dp))
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)).background(RupioColors.Primary.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Icon(if (upgrade) Icons.Rounded.CloudUpload else Icons.Rounded.FitnessCenter, null, tint = RupioColors.Primary, modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(if (upgrade) tr("Respalda tus datos", "Back up your data") else "Rupio", style = MaterialTheme.typography.displaySmall)
        Text(
            when {
                upgrade -> tr("Crea una cuenta (o entra con la tuya) y tus $count ${if (count == 1) "entrenamiento" else "entrenamientos"}, rutinas y pesos de este teléfono se suben a la nube. No se pierde nada.",
                        "Create an account (or sign in to yours) and your $count ${if (count == 1) "workout" else "workouts"}, routines and weights on this phone are uploaded to the cloud. Nothing is lost.")
                signUp -> tr("Crea tu cuenta para guardar tu progreso", "Create your account to save your progress")
                else -> tr("Entrena con método. Progresa con ciencia.", "Train with a plan. Progress with science.")
            }, style = MaterialTheme.typography.bodyLarge, color = RupioColors.TextDim)
        Spacer(Modifier.height(32.dp))

        AnimatedVisibility(signUp) {
            Column {
                OutlinedTextField(name, { name = it; nameError = null }, label = { Text(tr("Nombre", "Name")) }, singleLine = true,
                    isError = nameError != null, supportingText = nameError?.let { t -> { Text(t) } },
                    modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.PersonFullName },
                    leadingIcon = { Icon(Icons.Rounded.Person, null) }, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next), shape = RoundedCornerShape(14.dp))
                Spacer(Modifier.height(12.dp))
            }
        }
        OutlinedTextField(email, { email = it.trim(); emailError = null }, label = { Text(tr("Correo electrónico", "Email")) }, singleLine = true,
            isError = emailError != null, supportingText = emailError?.let { t -> { Text(t) } },
            // Conectado al autocompletado de Android: los gestores de contraseñas lo llenan.
            modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.Username + ContentType.EmailAddress },
            leadingIcon = { Icon(Icons.Rounded.Email, null) }, shape = RoundedCornerShape(14.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next))
        Spacer(Modifier.height(12.dp))
        PasswordField(pass, { pass = it; passError = null }, showPass, { showPass = !showPass }, newPassword = signUp, onDone = { submit() },
            error = passError, hint = if (signUp) tr("Mínimo 8 caracteres", "At least 8 characters") else null)
        if (!signUp) TextButton(onClick = { forgot = true; error = null }, modifier = Modifier.align(Alignment.End)) { Text(tr("¿Olvidaste tu contraseña?", "Forgot your password?")) }

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
        // v1.7 (N3): al entrar a una cuenta que ya existe, lo del teléfono se suma a lo de la cuenta (no la reemplaza).
        if (upgrade && !signUp) {
            Spacer(Modifier.height(12.dp))
            Surface(color = RupioColors.Warning.copy(alpha = 0.12f), shape = RoundedCornerShape(12.dp)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Rounded.MergeType, null, tint = RupioColors.Warning); Spacer(Modifier.width(8.dp))
                    Text(tr("Se juntarán con lo que ya tienes en tu cuenta: no se borra nada de ella. Tus récords se recalculan con todo tu historial.", "They'll be merged with what's already in your account: nothing in it is erased. Your PRs are recalculated with your whole history."),
                        color = RupioColors.Text, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        LoadingButton(if (signUp) (if (upgrade) tr("Crear cuenta y subir mis datos", "Create account and upload my data") else tr("Crear cuenta", "Create account")) else (if (upgrade) tr("Entrar y subir mis datos", "Sign in and upload my data") else tr("Iniciar sesión", "Sign in")),
            loading = busy, onClick = { submit() },
            modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp), loadingText = if (signUp) tr("Creando tu cuenta…", "Creating your account…") else tr("Entrando…", "Signing in…"))
        TextButton(onClick = { signUp = !signUp; error = null; nameError = null; emailError = null; passError = null }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(if (signUp) tr("¿Ya tienes cuenta? Inicia sesión", "Already have an account? Sign in") else tr("¿No tienes cuenta? Regístrate", "No account? Sign up"))
        }
        Spacer(Modifier.height(8.dp))
        Text(tr("Tu sesión se queda abierta en este teléfono. Si pasan semanas sin usar la app, te pediremos la contraseña otra vez; tus datos del teléfono no se pierden.", "You stay signed in on this phone. If weeks go by without using the app, we'll ask for your password again; the data on your phone isn't lost."),
            style = MaterialTheme.typography.bodySmall, color = RupioColors.TextDim)
        TextButton(onClick = { privacy = true }, contentPadding = PaddingValues(0.dp)) {
            Text(if (signUp) tr("Al crear tu cuenta aceptas el Aviso de privacidad", "By creating your account you accept the Privacy notice") else tr("Aviso de privacidad", "Privacy notice"), style = MaterialTheme.typography.bodySmall)
        }

        if (!upgrade) {
            Spacer(Modifier.height(20.dp))
            HorizontalDivider(color = RupioColors.Outline)
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = { c.auth.useLocalMode(name.ifBlank { email.substringBefore('@') }, c.restoredLocalOwner()) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Icon(Icons.Rounded.PhoneAndroid, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(tr("Probar sin cuenta", "Try without an account"))
            }
            Text(tr("Sin cuenta, tus datos se guardan solo en este teléfono. Cuando quieras, desde Perfil creas una cuenta y se suben sin perder nada.", "Without an account, your data is saved only on this phone. Whenever you want, create an account from Profile and it's uploaded without losing anything."),
                style = MaterialTheme.typography.bodySmall, color = RupioColors.TextDim, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }

    if (forgot) ForgotPasswordDialog(email) { changed ->
        forgot = false
        if (changed != null) { email = changed; pass = ""; signUp = false; info = tr("Listo. Entra con tu contraseña nueva.", "Done. Sign in with your new password.") }
    }
    if (privacy) PrivacyPolicyDialog { privacy = false }
}
