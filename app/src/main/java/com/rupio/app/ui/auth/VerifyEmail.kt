package com.rupio.app.ui.auth


import com.rupio.app.core.tr
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Segundos de espera entre un "Reenviar código" y el siguiente (Neon Auth limita los envíos). */
const val RESEND_COOLDOWN_SEC = 60

/** Solo dígitos y máximo 6: lo que se pega desde el correo ("Tu código: 123 456") también sirve. */
fun cleanOtp(raw: String): String = raw.filter { it.isDigit() }.take(6)

/**
 * v1.7 (N6): la cuenta recién creada no está lista hasta escribir el código de 6 dígitos que llega al correo.
 * Así un correo mal escrito no se queda con tus datos, y "¿Olvidaste tu contraseña?" sí te va a llegar.
 * Mientras tanto no se sube nada a la nube. tr("Usar otro correo", "Use another email") cancela la cuenta nueva (vacía) y regresa.
 */
/** v2.2: al volver a abrir la app después de un envío fallido (el mensaje exacto no se guarda). */
fun earlierSendFailedMessage(): String =
    tr("No pudimos enviarte el código. Toca «Reenviar código» para intentarlo de nuevo.", "We couldn't send you the code. Tap \"Resend code\" to try again.")

/**
 * v2.2: segundos que faltan para poder tocar «Reenviar código». Se cuenta desde el último envío que sí salió
 * ([sentAt], ms); si falló o no se sabe cuándo fue, se puede reenviar ya.
 */
fun resendWaitSec(sendFailed: Boolean, sentAt: Long, now: Long = System.currentTimeMillis()): Int {
    if (sendFailed || sentAt <= 0) return 0
    val elapsed = ((now - sentAt) / 1000).coerceAtLeast(0)
    return (RESEND_COOLDOWN_SEC - elapsed).coerceIn(0, RESEND_COOLDOWN_SEC.toLong()).toInt()
}

/**
 * [initialSendError]: el último envío del código falló (se muestra y se puede reenviar ya). [sentAt]: hora del último
 * envío que sí salió (ms), para calcular la espera de «Reenviar código».
 */
@Composable
fun VerifyEmailScreen(email: String, newAccount: Boolean = true, initialSendError: String? = null, sentAt: Long = 0) {
    val c = container()
    val scope = rememberCoroutineScope()
    var code by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    // v2.2: los mensajes sobreviven a girar el teléfono (antes «Te enviamos un código nuevo» desaparecía).
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var sendError by rememberSaveable { mutableStateOf(initialSendError) }
    var info by rememberSaveable { mutableStateOf<String?>(null) }
    var wait by rememberSaveable { mutableIntStateOf(resendWaitSec(initialSendError != null, sentAt)) }
    var leave by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    LaunchedEffect(wait > 0) { while (wait > 0) { delay(1000); wait-- } }

    fun confirm() {
        if (busy || sending || leaving) return
        if (code.length != 6) { error = tr("Escribe los 6 dígitos del código.", "Enter the 6 digits of the code."); return }
        error = null; info = null; busy = true
        scope.launch {
            try { c.verifyEmail(code) }
            catch (e: AuthException) { error = e.message }
            catch (e: Exception) { error = tr("Algo salió mal (código V1). Inténtalo de nuevo.", "Something went wrong (code V1). Please try again.") }
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
        Text(tr("Confirma tu correo", "Confirm your email"), style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(8.dp))
        Text(tr("Escribe el código de 6 dígitos para confirmar $email.", "Enter the 6-digit code to confirm $email."),
            style = MaterialTheme.typography.bodyLarge, color = RupioColors.TextDim)
        Spacer(Modifier.height(4.dp))
        Text(tr("Si no lo ves en unos minutos, revisa la carpeta de spam o de promociones.", "If you don't see it in a few minutes, check your spam or promotions folder."), style = MaterialTheme.typography.bodyMedium, color = RupioColors.TextDim)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            code, { code = cleanOtp(it); error = null; if (code.length == 6) confirm() },
            label = { Text(tr("Código de 6 dígitos", "6-digit code")) }, singleLine = true,
            textStyle = TextStyle(fontSize = 24.sp, letterSpacing = 6.sp, textAlign = TextAlign.Center),
            modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.SmsOtpCode },
            leadingIcon = { Icon(Icons.Rounded.Pin, null) }, shape = RoundedCornerShape(14.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { confirm() }),
        )
        (error ?: sendError ?: info)?.let {
            Spacer(Modifier.height(8.dp))
            val isError = error != null || sendError != null
            Surface(color = (if (isError) RupioColors.Danger else RupioColors.Success).copy(alpha = 0.12f), shape = RoundedCornerShape(12.dp)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (isError) Icons.Rounded.ErrorOutline else Icons.Rounded.CheckCircle, null, tint = if (isError) RupioColors.Danger else RupioColors.Success); Spacer(Modifier.width(8.dp))
                    Text(it, color = RupioColors.Text, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        LoadingButton(tr("Confirmar", "Confirm"), loading = busy, onClick = { confirm() }, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp), loadingText = tr("Confirmando…", "Confirming…"))
        TextButton(enabled = wait == 0 && !busy && !sending && !leaving, onClick = {
            error = null; sendError = null; info = null; sending = true
            scope.launch {
                try {
                    c.auth.sendVerificationCode()
                    info = tr("Te enviamos un código nuevo. El anterior ya no sirve.", "We sent you a new code. The previous one no longer works.")
                    wait = RESEND_COOLDOWN_SEC
                } catch (e: AuthException) {
                    sendError = e.message
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    sendError = tr("No se pudo enviar el código (código V2). Inténtalo de nuevo.", "The code couldn't be sent (code V2). Please try again.")
                } finally {
                    sending = false
                }
            }
        }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(if (wait > 0) tr("Reenviar código en $wait s", "Resend code in $wait s") else tr("Reenviar código", "Resend code"))
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = RupioColors.Outline)
        Spacer(Modifier.height(8.dp))
        TextButton(enabled = !busy && !sending && !leaving, onClick = { leave = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(tr("¿Correo equivocado? Usar otro correo", "Wrong email? Use another one"))
        }
        Text(tr("Mientras no confirmes tu correo, nada se sube a la nube.", "Until you confirm your email, nothing is uploaded to the cloud."), style = MaterialTheme.typography.bodySmall, color = RupioColors.TextDim,
            modifier = Modifier.align(Alignment.CenterHorizontally), textAlign = TextAlign.Center)
    }

    if (leave) ConfirmDialog(tr("¿Usar otro correo?", "Use another email?"),
        if (newAccount) tr("Se cancela la cuenta de $email (todavía está vacía) y puedes crearla otra vez con el correo correcto. Lo que tenías en este teléfono se queda aquí.",
                    "The account for $email is canceled (it's still empty) and you can create it again with the right email. What you had on this phone stays here.")
                else tr("Se cierra la sesión de $email. Lo que tenías en este teléfono antes de entrar se queda aquí.", "You're signed out of $email. What you had on this phone before signing in stays here."),
        tr("Usar otro correo", "Use another email"), { leave = false }) {
        leave = false; leaving = true
        scope.launch {
            try { c.leaveUnverifiedAccount() }
            catch (e: AuthException) { error = e.message }
            catch (e: Exception) { error = tr("Algo salió mal (código V3). Inténtalo de nuevo.", "Something went wrong (code V3). Please try again.") }
            leaving = false
        }
    }
}
