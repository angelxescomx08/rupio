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
    const val UPDATED = "18 de noviembre de 2026"
    const val WEB_URL = "https://angelxescomx08.github.io/rupio/privacidad.html"
    const val DELETE_URL = "https://angelxescomx08.github.io/rupio/eliminar-cuenta.html"
    private const val DELETE_SHORT = "angelxescomx08.github.io/rupio/eliminar-cuenta.html"
    /** v1.7 (N2): contacto directo del responsable (antes decía "el correo de la ficha de Google Play", que aún no existe). */
    const val CONTACT_EMAIL = "jose.angel.hdz.rda@gmail.com"
    const val ADDRESS = "Calle Nuevo León, manzana 88, lote 11, colonia San Juan Tlalpizahuac, C.P. 56618, Valle de Chalco Solidaridad, Estado de México, México"

    /** v2.0: en inglés se muestra la traducción; la versión que vale es la española (ver [EN_NOTE]). */
    val sections: List<Pair<String, String>> get() = if (com.rupio.app.core.I18n.isEnglish) sectionsEn else sectionsEs
    const val EN_NOTE = "This is an English translation for your convenience. The Spanish version is the official one."

    private val sectionsEs: List<Pair<String, String>> = listOf(
        "Quién es responsable" to
            "Rupio es una app hecha por José Ángel Hernández Radilla, quien es responsable de tus datos personales.\n" +
            "Domicilio: $ADDRESS.\n" +
            "Correo de contacto: $CONTACT_EMAIL. Escribe ahí para cualquier duda o solicitud.",
        "Qué datos guarda Rupio" to
            "• Tu cuenta: nombre y correo. La contraseña la guarda cifrada el servicio de inicio de sesión (Neon Auth); la app nunca la guarda en el teléfono.\n" +
            "• Tus entrenamientos: rutinas, ejercicios, series, pesos, repeticiones, tiempos, notas y récords.\n" +
            "• Tu peso corporal, si decides registrarlo. Es un dato de salud (dato personal sensible): solo se guarda si lo aceptas expresamente con la casilla que aparece la primera vez que lo registras, y se guarda la fecha en que lo aceptaste. Solo se usa para mostrarte tu tendencia.\n" +
            "• Preferencias: unidades, descanso por defecto, zona horaria e inicio de semana.\n" +
            "• Compras de apoyo: las procesa Google Play. Rupio solo recibe qué nivel compraste, nunca tus datos de pago.\n" +
            "• Muro de agradecimientos (nivel Mecenas, solo si lo compras y lo aceptas): el nombre que elijas para el muro, tu lugar, la fecha en que llegaste, la fecha en que aceptaste publicarlo y tus puntos de aporte. " +
            "Para verificar cada compra se guarda el comprobante que firma Google Play (número de orden y token de la compra, sin datos de pago).\n" +
            "• Ligas semanales (opcional, solo si aceptas participar): el apodo que elijas, tu liga, tu grupo de la semana, tu volumen y número de entrenos de cada semana (los calcula el servidor con tus entrenos terminados), la fecha en que aceptaste y, si tienes un nivel de apoyo, su insignia.\n" +
            "• Datos técnicos mínimos: al conectarse con el servidor, este recibe la dirección IP de tu teléfono, como cualquier conexión a internet.",
        "Para qué se usan" to
            "Para guardar y respaldar tu progreso, sincronizarlo entre tus teléfonos y calcular cuándo te conviene subir de peso. " +
            "Rupio no tiene anuncios, no vende ni comparte tus datos y no los usa para hacer perfiles de publicidad. " +
            "Única excepción, porque tú lo pides: si eres Mecenas, el nombre que elegiste para el muro, tu lugar y la fecha en que llegaste los ven las demás personas que usan Rupio con cuenta. " +
            "Nunca se muestran tu correo ni cuánto pagaste; el orden del muro sí deja ver quién aportó más. " +
            "Si participas en las ligas semanales, tu apodo, tu liga, tu insignia de apoyo y tu volumen y entrenos de la semana los ven las demás personas que participan; nunca tu correo ni tu nombre de cuenta (a menos que lo uses como apodo).",
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
            "Puedes ver y corregir tus datos en la app en cualquier momento, incluso un entrenamiento ya terminado. " +
            "Puedes descargar una copia de tus entrenamientos, series y peso corporal desde Perfil → Exportar mis datos. " +
            "Puedes retirar tu consentimiento para guardar tu peso corporal desde Perfil → Permiso de peso corporal: se borra todo tu historial de peso del teléfono y de tu cuenta. " +
            "Puedes cambiar tu nombre del muro de agradecimientos o quitarlo (deja de verse y conservas tu nivel) desde Perfil → Muro de agradecimientos. Al eliminar tu cuenta se borra tu lugar; los comprobantes de compra se quedan sin ligarse a ti, para que no se puedan volver a usar. " +
            "Puedes cambiar tu apodo de las ligas o salirte (deja de verse y sales del grupo de la semana) desde Perfil → Ligas semanales. Al eliminar tu cuenta se borra también todo lo de las ligas. " +
            "Puedes eliminar tu cuenta y todos tus datos desde Perfil → Eliminar mi cuenta, o desde la página $DELETE_SHORT si ya no tienes la app. " +
            "Para cambiar el correo de tu cuenta, escríbenos desde tu correo actual. " +
            "También puedes pedir acceso, rectificación, cancelación u oposición (derechos ARCO) o revocar tu consentimiento escribiendo a $CONTACT_EMAIL; " +
            "te respondemos en un máximo de 20 días hábiles.",
        "Menores de edad" to
            "Rupio no está dirigida a menores de 13 años y no recopila a sabiendas datos de ellos.",
        "Cambios a este aviso" to
            "Si este aviso cambia, lo verás en la app y en la página web, con la fecha de actualización. Última actualización: $UPDATED.",
    )

    private const val UPDATED_EN = "November 18, 2026"

    private val sectionsEn: List<Pair<String, String>> = listOf(
        "Who is responsible" to
            "Rupio is an app made by José Ángel Hernández Radilla, who is responsible for your personal data.\n" +
            "Address: $ADDRESS.\n" +
            "Contact email: $CONTACT_EMAIL. Write there with any question or request.",
        "What data Rupio keeps" to
            "• Your account: name and email. Your password is stored encrypted by the sign-in service (Neon Auth); the app never stores it on your phone.\n" +
            "• Your workouts: routines, exercises, sets, weights, reps, times, notes and personal records.\n" +
            "• Your body weight, if you choose to log it. It's health data (sensitive personal data): it's only stored if you expressly accept it with the checkbox shown the first time you log it, and the date you accepted is stored. It's only used to show you your trend.\n" +
            "• Preferences: units, default rest, time zone and start of the week.\n" +
            "• Supporter purchases: processed by Google Play. Rupio only receives which level you bought, never your payment details.\n" +
            "• Thank-you wall (Patron level, only if you buy it and accept): the name you choose for the wall, your place, the date you joined, the date you agreed to publish it and your contribution points. " +
            "To verify each purchase, the receipt signed by Google Play is stored (order number and purchase token, with no payment details).\n" +
            "• Weekly leagues (optional, only if you agree to take part): the nickname you choose, your league, your group for the week, your volume and number of workouts each week (calculated by the server from your finished workouts), the date you agreed and, if you have a supporter level, its badge.\n" +
            "• Minimal technical data: when connecting to the server, it receives your phone's IP address, like any internet connection.",
        "What it's used for" to
            "To save and back up your progress, sync it between your phones and work out when you should add weight. " +
            "Rupio has no ads, doesn't sell or share your data and doesn't use it to build advertising profiles. " +
            "The only exception, because you ask for it: if you're a Patron, the name you chose for the wall, your place and the date you joined are seen by other people who use Rupio with an account. " +
            "Your email and how much you paid are never shown; the order of the wall does show who contributed more. " +
            "If you take part in the weekly leagues, your nickname, your league, your supporter badge and your weekly volume and workouts are seen by the other participants; never your email or your account name (unless you use it as your nickname).",
        "Where it's stored" to
            "On your phone and, if you have an account, in the cloud with Neon (Amazon Web Services servers in the United States). " +
            "By using the cloud you agree that your data is stored outside Mexico with that provider, which only stores it on Rupio's behalf. " +
            "If Android backup is turned on, Google keeps a copy of the workouts on your phone (not your session). " +
            "If you use the app without an account, your data only lives on your phone.",
        "How it's protected" to
            "All communication is encrypted (HTTPS). Your session is stored encrypted with a key from your phone that can't be taken out of it. " +
            "On the server, each account can only read and change its own data.",
        "How long it's kept" to
            "As long as you have your account. When you delete it, all your data is erased from the server right away; the provider's automatic backups " +
            "are deleted on their own within 30 days at most.",
        "Your rights" to
            "You can see and correct your data in the app at any time, even a finished workout. " +
            "You can download a copy of your workouts, sets and body weight from Profile → Export my data. " +
            "You can withdraw your consent to store your body weight from Profile → Body weight permission: all your weight history is erased from the phone and your account. " +
            "You can change your name on the thank-you wall or remove it (it stops showing and you keep your level) from Profile → Thank-you wall. When you delete your account your place is erased; purchase receipts are kept unlinked from you so they can't be reused. " +
            "You can change your league nickname or leave (it stops showing and you leave the week's group) from Profile → Weekly leagues. Deleting your account also erases everything related to the leagues. " +
            "You can delete your account and all your data from Profile → Delete my account, or from the page $DELETE_SHORT if you no longer have the app. " +
            "To change your account's email, write to us from your current email. " +
            "You can also request access, correction, cancellation or objection (ARCO rights) or revoke your consent by writing to $CONTACT_EMAIL; " +
            "we'll reply within 20 business days at most.",
        "Minors" to
            "Rupio isn't aimed at children under 13 and doesn't knowingly collect their data.",
        "Changes to this notice" to
            "If this notice changes, you'll see it in the app and on the website, with the update date. Last updated: $UPDATED_EN.\n\n$EN_NOTE",
    )
}

/** El aviso a pantalla completa, para leerlo sin salir de la app (también funciona sin internet). */
@Composable
fun PrivacyPolicyDialog(onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = RupioColors.Background) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, com.rupio.app.core.tr("Cerrar", "Close")) }
                    Text(com.rupio.app.core.tr("Aviso de privacidad", "Privacy notice"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
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
