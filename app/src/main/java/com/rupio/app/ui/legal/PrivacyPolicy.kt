package com.rupio.app.ui.legal

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.rupio.app.ui.theme.RupioColors

/**
 * Aviso de privacidad. El mismo texto está en docs/privacidad.html para publicarlo en la web (Google Play pide la
 * URL). Si cambias uno, cambia el otro.
 */
object PrivacyPolicy {
    const val UPDATED = "17 de noviembre de 2026"
    const val WEB_URL = "https://angelxescomx08.github.io/rupio/privacidad.html"
    const val DELETE_URL = "https://angelxescomx08.github.io/rupio/eliminar-cuenta.html"
    private const val DELETE_SHORT = "angelxescomx08.github.io/rupio/eliminar-cuenta.html"

    val sections: List<Pair<String, String>> = listOf(
        "Quién es responsable" to
            "Rupio es una app hecha por José Ángel Hernández Radilla, en México, quien es responsable de tus datos. " +
            "Para cualquier duda o solicitud escribe al correo de contacto que aparece en la ficha de Rupio en Google Play.",
        "Qué datos guarda Rupio" to
            "• Tu cuenta: nombre y correo. La contraseña la guarda cifrada el servicio de inicio de sesión (Neon Auth); la app nunca la guarda en el teléfono.\n" +
            "• Tus entrenamientos: rutinas, ejercicios, series, pesos, repeticiones, tiempos, notas y récords.\n" +
            "• Tu peso corporal, si decides registrarlo. Es un dato de salud y solo se usa para mostrarte tu tendencia.\n" +
            "• Preferencias: unidades, descanso por defecto, zona horaria e inicio de semana.\n" +
            "• Compras de apoyo: las procesa Google Play. Rupio solo recibe qué nivel compraste, nunca tus datos de pago.\n" +
            "• Datos técnicos mínimos: al conectarse con el servidor, este recibe la dirección IP de tu teléfono, como cualquier conexión a internet.",
        "Para qué se usan" to
            "Para guardar y respaldar tu progreso, sincronizarlo entre tus teléfonos y calcular cuándo te conviene subir de peso. " +
            "Rupio no tiene anuncios, no vende ni comparte tus datos y no los usa para hacer perfiles de publicidad.",
        "Dónde se guardan" to
            "En tu teléfono y, si tienes cuenta, en la nube con Neon (servidores de Amazon Web Services en Estados Unidos). " +
            "Al usar la nube aceptas que tus datos se guarden fuera de México con ese proveedor, que solo los almacena por encargo de Rupio. " +
            "Si tienes activada la copia de seguridad de Android, Google guarda una copia de tus entrenos del teléfono (no tu sesión). " +
            "Si usas la app sin cuenta, tus datos solo viven en tu teléfono.",
        "Cómo se protegen" to
            "Toda la comunicación va cifrada (HTTPS). La sesión se guarda cifrada con una llave del teléfono que no se puede sacar de él. " +
            "En el servidor, cada cuenta solo puede leer y cambiar sus propios datos.",
        "Cuánto tiempo se guardan" to
            "Mientras tengas tu cuenta. Al eliminarla se borran de inmediato todos tus datos del servidor; las copias de seguridad automáticas " +
            "del proveedor se eliminan solas en un máximo de 30 días.",
        "Tus derechos" to
            "Puedes ver y corregir tus datos en la app en cualquier momento. Puedes eliminar tu cuenta y todos tus datos desde Perfil → Eliminar mi cuenta, " +
            "o desde la página $DELETE_SHORT si ya no tienes la app. También puedes pedir acceso, rectificación, " +
            "cancelación u oposición (derechos ARCO) escribiendo al correo de contacto de la ficha de Google Play; te respondemos en un máximo de 20 días hábiles.",
        "Menores de edad" to
            "Rupio no está dirigida a menores de 13 años y no recopila a sabiendas datos de ellos.",
        "Cambios a este aviso" to
            "Si este aviso cambia, lo verás en la app y en la página web, con la fecha de actualización. Última actualización: $UPDATED.",
    )
}

/** El aviso a pantalla completa, para leerlo sin salir de la app (también funciona sin internet). */
@Composable
fun PrivacyPolicyDialog(onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = RupioColors.Black) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Cerrar") }
                    Text("Aviso de privacidad", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    PrivacyPolicy.sections.forEach { (title, body) ->
                        Column {
                            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                            Spacer(Modifier.height(4.dp))
                            Text(body, style = MaterialTheme.typography.bodyMedium, color = RupioColors.TextDim)
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}
