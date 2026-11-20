package com.rupio.app.domain

import com.rupio.app.core.Dates
import com.rupio.app.core.tr
import com.rupio.app.data.remote.SyncProblem
import com.rupio.app.data.remote.SyncStatus
import java.time.ZoneId

/**
 * Qué decirle a la persona sobre su respaldo. Antes cinco situaciones distintas se reducían a "Sin conexión por
 * ahora" y solo se veía en Perfil. Ahora se revisa en orden y el primer problema decide el mensaje:
 *   ¿sesión vigente? → ¿hay internet? → ¿responde el servidor? → ¿aceptó todo? → ¿nada pendiente?
 * Rojo = necesita que actúes · ámbar = esperar o revisar · azul = en curso · verde = todo respaldado.
 * Puro Kotlin: tiene pruebas.
 *
 * [uploading] (v2.0): se están subiendo cambios. Antes la pantalla lo deducía de que el título empezara con
 * "Subiendo", que en inglés ya no sirve.
 */
data class SyncSummary(val level: Level, val title: String, val body: String, val action: Action?, val pending: Int, val uploading: Boolean = false) {
    enum class Level { ACT, WARN, BUSY, OK, LOCAL }
    enum class Action { SIGN_IN_AGAIN, RETRY, REVIEW, CREATE_ACCOUNT }

    companion object {
        private const val DAY_MS = 24 * 3_600_000L

        fun of(status: SyncStatus, pending: Int, localOnly: Boolean, sessionExpired: Boolean, nowMs: Long, zone: ZoneId): SyncSummary {
            fun cambios(n: Int) = if (n == 1) tr("1 cambio", "1 change") else tr("$n cambios", "$n changes")
            fun Cambios(n: Int) = cambios(n).replaceFirstChar { it.uppercase() }
            if (localOnly) return SyncSummary(Level.LOCAL, tr("Solo en este teléfono", "Only on this phone"),
                tr("Tus datos no se respaldan. Crea una cuenta y se suben sin perder nada.", "Your data isn't backed up. Create an account and it's uploaded without losing anything."),
                Action.CREATE_ACCOUNT, pending)
            if (sessionExpired || status.problem == SyncProblem.SESSION_EXPIRED) return SyncSummary(Level.ACT, tr("Tu sesión venció", "Your session expired"),
                tr("Tus entrenos siguen en el teléfono; entra de nuevo para respaldarlos", "Your workouts are still on the phone; sign in again to back them up") +
                    (if (pending > 0) tr(" (${cambios(pending)} esperando).", " (${cambios(pending)} waiting).") else "."),
                Action.SIGN_IN_AGAIN, pending)
            val keepUsing = tr("Puedes seguir usando la app mientras tanto.", "You can keep using the app in the meantime.")
            if (status.running && pending > 0) return SyncSummary(Level.BUSY, tr("Subiendo ${cambios(pending)}…", "Uploading ${cambios(pending)}…"), keepUsing, null, pending, uploading = true)
            if (status.running) return SyncSummary(Level.BUSY, tr("Sincronizando…", "Syncing…"), keepUsing, null, pending)
            val last = status.lastSuccess
            val since = last?.let { " · " + Dates.relative(it, nowMs, zone) }.orEmpty()
            val allBacked = tr("Todo respaldado", "All backed up") + since
            when (status.problem) {
                SyncProblem.OFFLINE -> return if (pending > 0) SyncSummary(Level.WARN, tr("Sin internet", "No internet"),
                    tr("${Cambios(pending)} ${if (pending == 1) "espera" else "esperan"} y se suben solos al volver la conexión.",
                        "${Cambios(pending)} waiting; they upload on their own when the connection is back."), null, pending)
                else SyncSummary(Level.OK, allBacked, tr("Ahora no hay internet, pero no tienes nada pendiente.", "There's no internet right now, but you have nothing pending."), null, 0)
                SyncProblem.SERVER -> return SyncSummary(Level.WARN, tr("Rupio no responde", "Rupio isn't responding"),
                    tr("No es tu internet. Lo reintentamos solos en unos minutos", "It's not your internet. We'll retry on our own in a few minutes") +
                        (if (pending > 0) tr(" (${cambios(pending)} pendientes).", " (${cambios(pending)} pending).") else "."), Action.RETRY, pending)
                SyncProblem.REJECTED -> return SyncSummary(Level.WARN,
                    tr("${Cambios(status.rejected)} no se ${if (status.rejected == 1) "pudo" else "pudieron"} subir", "${Cambios(status.rejected)} couldn't be uploaded"),
                    tr("El servidor no los aceptó. Revisa cuáles son y decide si reintentarlos o descartarlos.", "The server didn't accept them. Check which ones and decide whether to retry or discard them."),
                    Action.REVIEW, pending)
                else -> {}
            }
            if (pending > 0) {
                // Más de un día con cambios sin subir: ya no es normal, se avisa en ámbar.
                val stale = last == null || nowMs - last > DAY_MS
                return SyncSummary(if (stale) Level.WARN else Level.BUSY, tr("${Cambios(pending)} por subir", "${Cambios(pending)} to upload"),
                    if (last == null) tr("Aún no se ha respaldado nada.", "Nothing has been backed up yet.") else tr("Último respaldo: ", "Last backup: ") + Dates.relative(last, nowMs, zone) + ".",
                    if (stale) Action.RETRY else null, pending)
            }
            if (last == null) return SyncSummary(Level.BUSY, tr("Aún no se sincroniza", "Not synced yet"), tr("Se hará sola en cuanto haya internet.", "It will happen on its own as soon as there's internet."), null, 0)
            return SyncSummary(Level.OK, allBacked, tr("Nada pendiente de subir.", "Nothing pending upload."), null, 0)
        }
    }
}
