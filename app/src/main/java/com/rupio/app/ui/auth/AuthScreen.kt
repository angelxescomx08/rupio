package com.rupio.app.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rupio.app.container
import com.rupio.app.data.auth.AuthException
import com.rupio.app.ui.theme.RupioColors
import kotlinx.coroutines.launch

@Composable
fun AuthScreen() {
    val c = container()
    val scope = rememberCoroutineScope()
    var signUp by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var pass by rememberSaveable { mutableStateOf("") }
    var showPass by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun submit() {
        error = null
        if (signUp && name.isBlank()) { error = "Escribe tu nombre."; return }
        if (!email.contains('@')) { error = "Escribe un correo válido."; return }
        if (pass.length < 8) { error = "La contraseña debe tener al menos 8 caracteres."; return }
        busy = true
        scope.launch {
            try { if (signUp) c.auth.signUp(name, email, pass) else c.auth.signIn(email, pass) }
            catch (e: AuthException) { error = e.message }
            catch (e: Exception) { error = "Algo salió mal: ${e.message}" }
            busy = false
        }
    }

    Column(
        Modifier.fillMaxSize().background(RupioColors.Black).systemBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
    ) {
        Spacer(Modifier.height(32.dp))
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)).background(RupioColors.Primary.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.FitnessCenter, null, tint = RupioColors.Primary, modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text("Rupio", style = MaterialTheme.typography.displaySmall)
        Text(if (signUp) "Crea tu cuenta para guardar tu progreso" else "Entrena con método. Progresa con ciencia.", style = MaterialTheme.typography.bodyLarge, color = RupioColors.TextDim)
        Spacer(Modifier.height(32.dp))

        AnimatedVisibility(signUp) {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text("Nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Rounded.Person, null) }, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next), shape = RoundedCornerShape(14.dp))
                Spacer(Modifier.height(12.dp))
            }
        }
        OutlinedTextField(email, { email = it.trim() }, label = { Text("Correo electrónico") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Rounded.Email, null) }, shape = RoundedCornerShape(14.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next))
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(pass, { pass = it }, label = { Text("Contraseña") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Rounded.Lock, null) }, shape = RoundedCornerShape(14.dp),
            supportingText = { if (signUp) Text("Mínimo 8 caracteres") },
            visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = { IconButton(onClick = { showPass = !showPass }) { Icon(if (showPass) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, "Mostrar contraseña") } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }))

        error?.let {
            Spacer(Modifier.height(8.dp))
            Surface(color = RupioColors.Danger.copy(alpha = 0.12f), shape = RoundedCornerShape(12.dp)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.ErrorOutline, null, tint = RupioColors.Danger); Spacer(Modifier.width(8.dp))
                    Text(it, color = RupioColors.Text, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Button(onClick = { submit() }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp)) {
            if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            else Text(if (signUp) "Crear cuenta" else "Iniciar sesión", style = MaterialTheme.typography.titleMedium)
        }
        TextButton(onClick = { signUp = !signUp; error = null }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(if (signUp) "¿Ya tienes cuenta? Inicia sesión" else "¿No tienes cuenta? Regístrate")
        }
        Spacer(Modifier.height(8.dp))
        Text("Tu sesión queda guardada en este teléfono: no tendrás que volver a entrar hasta que cierres sesión.",
            style = MaterialTheme.typography.bodySmall, color = RupioColors.TextFaint)

        Spacer(Modifier.height(28.dp))
        HorizontalDivider(color = RupioColors.Outline)
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = { c.auth.useLocalMode(name.ifBlank { email.substringBefore('@') }) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Icon(Icons.Rounded.PhoneAndroid, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Probar sin cuenta")
        }
        Text("Sin cuenta, tus datos se guardan solo en este teléfono y no se respaldan.",
            style = MaterialTheme.typography.bodySmall, color = RupioColors.TextFaint, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}
