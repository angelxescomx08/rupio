package com.rupio.app.ui.auth

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rupio.app.SignOutState
import com.rupio.app.SignOutStep
import com.rupio.app.ui.components.RupioSpinner
import com.rupio.app.ui.theme.RupioColors

/**
 * Pantalla de "Cerrando sesión": dice qué está pasando, en qué paso va y por qué tarda.
 * Cubre toda la app (no se puede tocar nada detrás) y "atrás" no la cierra a medias.
 */
@Composable
fun SignOutOverlay(s: SignOutState, onRetry: () -> Unit, onExitAnyway: () -> Unit, onCancel: () -> Unit) {
    if (!s.active) return
    BackHandler { if (s.uploadFailed) onCancel() }
    Box(
        Modifier.fillMaxSize().background(RupioColors.Black.copy(alpha = 0.97f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }   // bloquea toques
            .systemBarsPadding().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(when { s.step == SignOutStep.DONE -> 2; s.uploadFailed -> 1; else -> 0 }, label = "signout",
            transitionSpec = { fadeIn() togetherWith fadeOut() }) { mode ->
            when (mode) {
                2 -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                    Box(Modifier.size(72.dp).clip(CircleShape).background(RupioColors.Success.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Check, null, tint = RupioColors.Success, modifier = Modifier.size(40.dp))
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("Sesión cerrada", style = MaterialTheme.typography.headlineSmall)
                    Text(if (s.localOnly) "Hasta pronto 👋" else "Tus datos te esperan en la nube.", color = RupioColors.TextDim)
                }
                1 -> UploadFailed(s, onRetry, onExitAnyway, onCancel)
                else -> Progress(s)
            }
        }
    }
}

@Composable
private fun Progress(s: SignOutState) {
    Column(Modifier.widthIn(max = 420.dp).fillMaxWidth()) {
        Text("Cerrando sesión…", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(6.dp))
        Text(
            if (s.localOnly) "Estás en modo local: se borrarán los datos de este teléfono."
            else "Tarda unos segundos porque primero guardamos tus entrenos en la nube, para que no pierdas nada.",
            style = MaterialTheme.typography.bodyMedium, color = RupioColors.TextDim,
        )
        Spacer(Modifier.height(22.dp))
        val order = listOf(SignOutStep.UPLOAD, SignOutStep.WIPE, SignOutStep.SIGN_OUT)
        val cur = order.indexOf(s.step).let { if (it < 0) order.size else it }
        if (!s.localOnly) StepRow("Guardando tus cambios en la nube",
            when { cur > 0 -> if (s.pendingAtStart > 0) "${s.pendingAtStart} ${cambios(s.pendingAtStart)} guardados" else "Todo estaba al día"
                   s.pendingAtStart > 0 -> "Subiendo ${s.pendingAtStart} ${cambios(s.pendingAtStart)}…"
                   else -> "Revisando que todo esté al día…" }, state(0, cur))
        StepRow("Borrando los datos de este teléfono",
            if (s.localOnly) "Entrenos, rutinas y peso" else "Tranquilo: siguen guardados en tu cuenta", state(1, cur))
        StepRow("Cerrando tu sesión", "Para que nadie más entre con ella aquí", state(2, cur))
    }
}

private fun cambios(n: Int) = if (n == 1) "cambio" else "cambios"
private enum class St { DONE, NOW, LATER }
private fun state(i: Int, cur: Int) = when { i < cur -> St.DONE; i == cur -> St.NOW; else -> St.LATER }

@Composable
private fun StepRow(title: String, sub: String, st: St) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp).semantics { liveRegion = LiveRegionMode.Polite }, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
            when (st) {
                St.DONE -> Box(Modifier.size(28.dp).clip(CircleShape).background(RupioColors.Success), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Check, "Listo", tint = Color.Black, modifier = Modifier.size(18.dp))
                }
                St.NOW -> RupioSpinner(size = 26.dp, label = "En curso")
                St.LATER -> Box(Modifier.size(24.dp).clip(CircleShape).background(RupioColors.Surface3))
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = if (st == St.LATER) RupioColors.TextFaint else RupioColors.Text)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = if (st == St.LATER) RupioColors.TextFaint else RupioColors.TextDim)
        }
    }
}

@Composable
private fun UploadFailed(s: SignOutState, onRetry: () -> Unit, onExitAnyway: () -> Unit, onCancel: () -> Unit) {
    Column(Modifier.widthIn(max = 420.dp).fillMaxWidth()) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(RupioColors.Warning.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.CloudOff, null, tint = RupioColors.Warning, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text("No pudimos subir ${s.pendingLeft} ${cambios(s.pendingLeft)}", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            if (s.serverRejected) "El servidor no aceptó algunos cambios. Si sales ahora, se perderán."
            else "Parece que no hay internet (o está muy lento). Si sales ahora, esos cambios se perderán. Si esperas a tener señal, se guardan solos.",
            style = MaterialTheme.typography.bodyMedium, color = RupioColors.TextDim,
        )
        Spacer(Modifier.height(22.dp))
        Button(onClick = onRetry, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) {
            Icon(Icons.Rounded.Refresh, null); Spacer(Modifier.width(8.dp)); Text("Intentar otra vez")
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = onExitAnyway, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = RupioColors.Danger)) { Text("Salir y perder ${s.pendingLeft} ${cambios(s.pendingLeft)}") }
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Cancelar y seguir en Rupio") }
        Text("Rupio sube tus cambios solo cada vez que hay internet; no tienes que hacer nada más.",
            style = MaterialTheme.typography.labelMedium, color = RupioColors.TextFaint, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}
