package com.rupio.app.domain

import com.rupio.app.core.Dates
import com.rupio.app.data.remote.SyncProblem
import com.rupio.app.data.remote.SyncStatus
import java.time.ZoneId

/**
 * Qué decirle a la persona sobre su respaldo. Antes cinco situaciones distintas se reducían a "Sin conexión por
 * ahora" y solo se veía en Perfil. Ahora se revisa en orden y el primer problema decide el mensaje:
 *   ¿sesión vigente? → ¿hay internet? → ¿responde el servidor? → ¿aceptó todo? → ¿nada pendiente?
 * Rojo = necesita que actúes · ámbar = esperar o revisar · azul = en curso · verde = todo respaldado.
 * Puro Kotlin: tiene pruebas.
 */
data class SyncSummary(val level: Level, val title: String, val body: String, val action: Action?, val pending: Int) {
    enum class Level { ACT, WARN, BUSY, OK, LOCAL }
    enum class Action { SIGN_IN_AGAIN, RETRY, REVIEW, CREATE_ACCOUNT }

    companion object {
        private const val DAY_MS = 24 * 3_600_000L

        fun of(status: SyncStatus, pending: Int, localOnly: Boolean, sessionExpired: Boolean, nowMs: Long, zone: ZoneId): SyncSummary {
            fun cambios(n: Int) = if (n == 1) "1 cambio" else "$n cambios"
            if (localOnly) return SyncSummary(Level.LOCAL, "Solo en este teléfono",
                "Tus datos no se respaldan. Crea una cuenta y se suben sin perder nada.", Action.CREATE_ACCOUNT, pending)
            if (sessionExpired || status.problem == SyncProblem.SESSION_EXPIRED) return SyncSummary(Level.ACT, "Tu sesión venció",
                "Tus entrenos siguen en el teléfono; entra de nuevo para respaldarlos" + (if (pending > 0) " (${cambios(pending)} esperando)." else "."),
                Action.SIGN_IN_AGAIN, pending)
            if (status.running && pending > 0) return SyncSummary(Level.BUSY, "Subiendo ${cambios(pending)}…", "Puedes seguir usando la app mientras tanto.", null, pending)
            if (status.running) return SyncSummary(Level.BUSY, "Sincronizando…", "Puedes seguir usando la app mientras tanto.", null, pending)
            val last = status.lastSuccess
            val since = last?.let { " · " + Dates.relative(it, nowMs, zone) }.orEmpty()
            when (status.problem) {
                SyncProblem.OFFLINE -> return if (pending > 0) SyncSummary(Level.WARN, "Sin internet",
                    "${cambios(pending).replaceFirstChar { it.uppercase() }} ${if (pending == 1) "espera" else "esperan"} y se suben solos al volver la conexión.", null, pending)
                else SyncSummary(Level.OK, "Todo respaldado$since", "Ahora no hay internet, pero no tienes nada pendiente.", null, 0)
                SyncProblem.SERVER -> return SyncSummary(Level.WARN, "Rupio no responde",
                    "No es tu internet. Lo reintentamos solos en unos minutos" + (if (pending > 0) " (${cambios(pending)} pendientes)." else "."), Action.RETRY, pending)
                SyncProblem.REJECTED -> return SyncSummary(Level.WARN,
                    "${cambios(status.rejected).replaceFirstChar { it.uppercase() }} no se ${if (status.rejected == 1) "pudo" else "pudieron"} subir",
                    "El servidor no los aceptó. Revisa cuáles son y decide si reintentarlos o descartarlos.", Action.REVIEW, pending)
                else -> {}
            }
            if (pending > 0) {
                // Más de un día con cambios sin subir: ya no es normal, se avisa en ámbar.
                val stale = last == null || nowMs - last > DAY_MS
                return SyncSummary(if (stale) Level.WARN else Level.BUSY, "${cambios(pending).replaceFirstChar { it.uppercase() }} por subir",
                    if (last == null) "Aún no se ha respaldado nada." else "Último respaldo: " + Dates.relative(last, nowMs, zone) + ".", if (stale) Action.RETRY else null, pending)
            }
            if (last == null) return SyncSummary(Level.BUSY, "Aún no se sincroniza", "Se hará sola en cuanto haya internet.", null, 0)
            return SyncSummary(Level.OK, "Todo respaldado$since", "Nada pendiente de subir.", null, 0)
        }
    }
}
