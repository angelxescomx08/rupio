package com.rupio.app.ui.auth

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
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rupio.app.container
import com.rupio.app.data.auth.AuthException
import com.rupio.app.ui.components.ConfirmDialog
import com.rupio.app.ui.components.LoadingButton
import com.rupio.app.ui.theme.RupioColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Segundos de espera entre un "Reenviar código" y el siguiente (Neon Auth limita los envíos). */
const val RESEND_COOLDOWN_SEC = 60

/** Solo dígitos y máximo 6: lo que se pega desde el correo ("Tu código: 123 456") también sirve. */
fun cleanOtp(raw: String): String = raw.filter { it.isDigit() }.take(6)

/**
 * v1.7 (N6): la cuenta recién creada no está lista hasta escribir el código de 6 dígitos que llega al correo.
 * Así un correo mal escrito no se queda con tus datos, y "¿Olvidaste tu contraseña?" sí te va a llegar.
 * Mientras tanto no se sube nada a la nube. "Usar otro correo" cancela la cuenta nueva (vacía) y regresa.
 */
@Composable
fun VerifyEmailScreen(email: String, newAccount: Boolean = true) {
    val c = container()
    val scope = rememberCoroutineScope()
    var code by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    var wait by rememberSaveable { mutableIntStateOf(RESEND_COOLDOWN_SEC) }
    var leave by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    LaunchedEffect(wait > 0) { while (wait > 0) { delay(1000); wait-- } }

    fun confirm() {
        if (busy) return
        if (code.length != 6) { error = "Escribe los 6 dígitos del código."; return }
        error = null; info = null; busy = true
        scope.launch {
            try { c.verifyEmail(code) }
            catch (e: AuthException) { error = e.message }
            catch (e: Exception) { error = "Algo salió mal (código V1). Inténtalo de nuevo." }
            busy = false
        }
    }

    Column(
        Modifier.fillMaxSize().background(RupioColors.Background).systemBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
    ) {
        Spacer(Modifier.height(32.dp))
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)).background(RupioColors.Primary.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.MarkEmailUnread, null, tint = RupioColors.Primary, modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text("Confirma tu correo", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(8.dp))
        Text("Te enviamos un código de 6 dígitos a $email. Escríbelo para terminar de crear tu cuenta.",
            style = MaterialTheme.typography.bodyLarge, color = RupioColors.TextDim)
        Spacer(Modifier.height(4.dp))
        Text("Si no lo ves en unos minutos, revisa la carpeta de spam o de promociones.", style = MaterialTheme.typography.bodyMedium, color = RupioColors.TextDim)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            code, { code = cleanOtp(it); error = null; if (code.length == 6) confirm() },
            label = { Text("Código de 6 dígitos") }, singleLine = true,
            textStyle = TextStyle(fontSize = 24.sp, letterSpacing = 6.sp, textAlign = TextAlign.Center),
            modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.SmsOtpCode },
            leadingIcon = { Icon(Icons.Rounded.Pin, null) }, shape = RoundedCornerShape(14.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { confirm() }),
        )
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
        LoadingButton("Confirmar", loading = busy, onClick = { confirm() }, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp), loadingText = "Confirmando…")
        TextButton(enabled = wait == 0 && !busy, onClick = {
            error = null; info = null
            scope.launch {
                try { c.auth.sendVerificationCode(); info = "Te enviamos un código nuevo. El anterior ya no sirve."; wait = RESEND_COOLDOWN_SEC }
                catch (e: AuthException) { error = e.message }
                catch (e: Exception) { error = "No se pudo enviar el código (código V2). Inténtalo de nuevo." }
            }
        }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(if (wait > 0) "Reenviar código en $wait s" else "Reenviar código")
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = RupioColors.Outline)
        Spacer(Modifier.height(8.dp))
        TextButton(enabled = !busy && !leaving, onClick = { leave = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("¿Correo equivocado? Usar otro correo")
        }
        Text("Mientras no confirmes tu correo, nada se sube a la nube.", style = MaterialTheme.typography.bodySmall, color = RupioColors.TextDim,
            modifier = Modifier.align(Alignment.CenterHorizontally), textAlign = TextAlign.Center)
    }

    if (leave) ConfirmDialog("¿Usar otro correo?",
        if (newAccount) "Se cancela la cuenta de $email (todavía está vacía) y puedes crearla otra vez con el correo correcto. Lo que tenías en este teléfono se queda aquí."
        else "Se cierra la sesión de $email. Lo que tenías en este teléfono antes de entrar se queda aquí.",
        "Usar otro correo", { leave = false }) {
        leave = false; leaving = true
        scope.launch {
            try { c.leaveUnverifiedAccount() }
            catch (e: AuthException) { error = e.message }
            catch (e: Exception) { error = "Algo salió mal (código V3). Inténtalo de nuevo." }
            leaving = false
        }
    }
}
