package com.rupio.app.ui.update

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rupio.app.Notice
import com.rupio.app.container
import com.rupio.app.core.tr
import com.rupio.app.data.update.AppUpdateController
import com.rupio.app.data.update.UnsavedWork
import com.rupio.app.ui.theme.RupioColors
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * v2.2: avisos de la actualización flexible de Google Play, encima de cualquier pantalla. Ninguno bloquea la app:
 * los dos se cierran con «Ahora no» / «Más tarde», con Atrás o tocando fuera.
 * [onUpdate]: abre el diálogo oficial de Play (MainActivity tiene el launcher de la Activity Result API).
 */
@Composable
fun AppUpdateHost(onUpdate: () -> Unit, controller: AppUpdateController = container().updates) {
    val c = container()
    val ui by controller.ui.collectAsStateWithLifecycle()
    // null hasta leer la base: al recrearse la pantalla (girar, modo oscuro, idioma), el primer cuadro no debe contar
    // como «terminó el entreno» (antes volvía el aviso a media serie después de «Después del entreno»).
    val trainingFlow = remember(c) { c.workouts.active.map { it != null }.distinctUntilChanged() }
    val training by trainingFlow.collectAsStateWithLifecycle(initialValue = null)
    LaunchedEffect(training) { training?.let(controller::onTrainingChanged) }
    // El reinicio se lanza aquí (vive mientras la app esté abierta), no en el diálogo, que desaparece al empezar.
    val scope = rememberCoroutineScope()

    if (ui.offer) UpdateAvailableDialog(onUpdate = onUpdate, onLater = controller::decline)
    val knownTraining = training
    if (ui.restart && knownTraining != null) UpdateReadyDialog(
        training = knownTraining, saving = ui.saving, error = ui.error,
        onRestart = { scope.launch { controller.restartAndInstall() } },
        onLater = { controller.postponeRestart(untilWorkoutEnds = knownTraining) },
    )

    // Avisos cortos («Descargando la actualización…»). En las pestañas van con los demás avisos de la app (MainShell);
    // en inicio de sesión, «Confirma tu correo» o la bienvenida nadie los recibe ahí, así que se muestran aquí.
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(controller) {
        controller.message.filterNotNull().collect { text ->
            controller.messageShown()
            if (c.notices.subscriptionCount.value > 0) c.notices.emit(Notice(text)) else snackbar.showSnackbar(text)
        }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) { SnackbarHost(snackbar, Modifier.padding(top = 4.dp)) }
}

/**
 * v2.2: mientras esté en pantalla, [save] se llama antes de reiniciar Rupio para instalar una actualización. Para los
 * diálogos del entreno: lo que se escribió sin tocar «Guardar» no se pierde con el reinicio.
 */
@Composable
fun SaveBeforeRestart(save: suspend () -> Unit) {
    val unsaved = container().unsaved
    val latest = rememberUpdatedState(save)
    DisposableEffect(unsaved) {
        val source = object : UnsavedWork.Source {
            override suspend fun save() = latest.value()
            override fun hasUnsaved() = false
        }
        unsaved.register(source)
        onDispose { unsaved.unregister(source) }
    }
}

@Composable
fun UpdateAvailableDialog(onUpdate: () -> Unit, onLater: () -> Unit) {
    AlertDialog(
        onDismissRequest = onLater,
        // Un toque sin querer fuera del aviso no cuenta como «Ahora no»; Atrás sí.
        properties = DialogProperties(dismissOnClickOutside = false),
        icon = { Icon(Icons.Rounded.SystemUpdate, null, tint = RupioColors.Primary) },
        title = { Text(tr("Hay una versión nueva de Rupio", "A new version of Rupio is available"), textAlign = TextAlign.Center) },
        text = {
            Text(tr("Se descarga en segundo plano mientras sigues usando la app. Te avisamos cuando esté lista para instalarse.",
                "It downloads in the background while you keep using the app. We'll let you know when it's ready to install."),
                color = RupioColors.TextDim)
        },
        confirmButton = {
            TextButton(onClick = onUpdate, colors = ButtonDefaults.textButtonColors(contentColor = RupioColors.Primary)) { Text(tr("Actualizar", "Update")) }
        },
        dismissButton = { TextButton(onClick = onLater) { Text(tr("Ahora no", "Not now")) } },
    )
}

/** [training]: hay un entreno en curso; se puede dejar para después de terminarlo. */
@Composable
fun UpdateReadyDialog(training: Boolean, saving: Boolean, error: String?, onRestart: () -> Unit, onLater: () -> Unit) {
    AlertDialog(
        onDismissRequest = { if (!saving) onLater() },
        properties = DialogProperties(dismissOnClickOutside = false),
        icon = { Icon(Icons.Rounded.RestartAlt, null, tint = RupioColors.Primary) },
        title = { Text(tr("La actualización está lista", "The update is ready"), textAlign = TextAlign.Center) },
        text = {
            Column {
                Text(
                    if (training) tr("Tienes un entreno en curso. Si reinicias ahora, primero guardamos tu progreso y al volver sigues donde ibas. También puedes instalarla al terminar.",
                        "You have a workout in progress. If you restart now, we save your progress first and you pick up where you left off. You can also install it when you finish.")
                    else tr("Rupio se cerrará unos segundos para instalarla y volverá a abrirse solo.",
                        "Rupio will close for a few seconds to install it and then reopen on its own."),
                    color = RupioColors.TextDim,
                )
                error?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, color = RupioColors.Danger, style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onRestart, enabled = !saving, colors = ButtonDefaults.textButtonColors(contentColor = RupioColors.Primary)) {
                Text(if (saving) tr("Guardando tu progreso…", "Saving your progress…") else tr("Reiniciar y actualizar", "Restart and update"))
            }
        },
        dismissButton = {
            TextButton(onClick = onLater, enabled = !saving) { Text(if (training) tr("Después del entreno", "After my workout") else tr("Más tarde", "Later")) }
        },
    )
}
