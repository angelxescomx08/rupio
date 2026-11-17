package com.rupio.app.ui.components

import android.content.Context
import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * Compartir Rupio: abre el menú de Android (WhatsApp, Instagram, mensajes…) con un texto corto y el enlace de
 * Google Play. El enlace funciona en cuanto la app esté publicada (antes da "no encontrado").
 */
object ShareApp {
    fun playUrl(packageName: String) = "https://play.google.com/store/apps/details?id=$packageName"

    /** Mensaje natural, en primera persona, como lo mandaría un amigo del gym (sin tono de anuncio). */
    fun message(packageName: String) =
        "Estoy usando Rupio para registrar mis entrenos 💪 Te dice cuándo subir el peso y es gratis, sin anuncios. " +
            "Descárgala aquí: ${playUrl(packageName)}"

    fun share(context: Context) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Rupio · registro de entrenamiento")
            putExtra(Intent.EXTRA_TEXT, message(context.packageName))
        }
        val chooser = Intent.createChooser(send, "Compartir Rupio").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(chooser) }
    }
}

/** Botón "Compartir Rupio" listo para usar. */
@Composable
fun ShareAppButton(modifier: Modifier = Modifier, text: String = "Compartir Rupio") {
    val ctx = LocalContext.current
    OutlinedButton(onClick = { ShareApp.share(ctx) }, modifier = modifier.heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp)) {
        Icon(Icons.Rounded.Share, null); Spacer(Modifier.width(8.dp)); Text(text)
    }
}
