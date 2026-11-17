package com.rupio.app

import androidx.test.core.app.ApplicationProvider
import com.rupio.app.core.Dates
import com.rupio.app.core.Ids
import com.rupio.app.data.auth.AuthException
import com.rupio.app.data.auth.AuthRepository
import com.rupio.app.data.auth.AuthState
import com.rupio.app.data.auth.JwtFailure
import com.rupio.app.data.auth.NeonAuthClient
import com.rupio.app.data.local.BodyWeightEntity
import com.rupio.app.data.local.WorkoutEntity
import com.rupio.app.data.local.WorkoutExerciseEntity
import com.rupio.app.data.local.WorkoutSetEntity
import com.rupio.app.data.prefs.SecureStore
import com.rupio.app.data.prefs.SettingsStore
import com.rupio.app.data.remote.SyncProblem
import com.rupio.app.data.remote.SyncStatus
import com.rupio.app.domain.SupportPolicy
import com.rupio.app.domain.SyncSummary
import com.rupio.app.timer.RestTimerController
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.time.ZoneId
import java.util.Base64

/** Arreglos de la auditoría QA de la v1.5 (ver docs/QA_V16.md). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class V16FixesTest {
    private val app get() = ApplicationProvider.getApplicationContext<RupioApp>()
    private val c get() = app.container
    private val zone = ZoneId.of("America/Mexico_City")

    // ------------------------------------------------------------------ Neon Auth falso
    private class FakeAuth {
        var session = true            // false → get-session responde 200 "null" (sesión vencida)
        var offline = false
        var userId = "u-ana"
        val paths = mutableListOf<String>()
        val bodies = mutableListOf<String>()
        private fun jwt(): String {
            val p = Base64.getUrlEncoder().withoutPadding().encodeToString("""{"sub":"$userId","exp":${System.currentTimeMillis() / 1000 + 900}}""".toByteArray())
            return "h.$p.s"
        }
        val interceptor = Interceptor { chain ->
            val req = chain.request()
            if (offline) throw IOException("sin red")
            val path = req.url.encodedPath.substringAfter("/auth")
            paths += path
            bodies += req.body?.let { b -> Buffer().also { b.writeTo(it) }.readUtf8() }.orEmpty()
            fun resp(code: Int, body: String, jwt: Boolean = false, cookie: Boolean = false) = Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(code).message("x")
                .apply { if (jwt) header("set-auth-jwt", jwt()); if (cookie) header("Set-Cookie", "better-auth.session_token=tok-$userId; Path=/; HttpOnly") }
                .body(body.toResponseBody("application/json".toMediaType())).build()
            when (path) {
                "/sign-in/email", "/sign-up/email" -> resp(200, """{"token":"t","user":{"id":"$userId","name":"Ana","email":"ana@ejemplo.com"}}""", jwt = true, cookie = true)
                "/get-session" -> if (session) resp(200, """{"session":{"token":"t"},"user":{"id":"$userId"}}""", jwt = true) else resp(200, "null")
                "/email-otp/request-password-reset" -> resp(200, """{"success":true}""")
                "/email-otp/reset-password" -> if (bodies.last().contains("\"otp\":\"123456\"")) resp(200, """{"success":true}""") else resp(400, """{"code":"INVALID_OTP"}""")
                else -> resp(404, "{}")
            }
        }
    }

    private fun repo(fake: FakeAuth, secure: SecureStore = SecureStore(app)) =
        AuthRepository(secure, SettingsStore(app, "https://auth.ejemplo/neondb/auth", "https://api.ejemplo/rest/v1"),
            NeonAuthClient(OkHttpClient.Builder().addInterceptor(fake.interceptor).build()))

    /** S1: la contraseña nunca se guarda; lo que guardaban las versiones anteriores se borra al abrir. */
    @Test fun laContrasenaNoSeGuardaYLaViejaSeBorra() = runBlocking {
        val secure = SecureStore(app)
        secure.put("cred_email", "ana@ejemplo.com"); secure.put("cred_pass", "secreta123")
        val auth = repo(FakeAuth(), secure)
        assertNull("la contraseña de la v1.5 se borró", secure.get("cred_pass"))
        assertNull(secure.get("cred_email"))
        auth.signIn("ana@ejemplo.com", "secreta123")
        assertTrue(auth.state.value is AuthState.SignedIn)
        assertNull("iniciar sesión no guarda la contraseña", secure.get("cred_pass"))
    }

    /** Y1: si la sesión del servidor vence, la cuenta queda en "sesión vencida" (no "sin conexión") y se puede volver a entrar. */
    @Test fun sesionVencidaSeDetectaYSeRecupera() = runBlocking {
        val fake = FakeAuth()
        val auth = repo(fake)
        auth.signIn("ana@ejemplo.com", "secreta123")
        // Sin red: NO es sesión vencida.
        fake.offline = true
        assertNull(auth.validJwt(force = true))
        assertEquals(JwtFailure.OFFLINE, auth.lastFailure)
        assertFalse((auth.state.value as AuthState.SignedIn).sessionExpired)
        // El servidor dice que ya no hay sesión → "sesión vencida".
        fake.offline = false; fake.session = false
        assertNull(auth.validJwt(force = true))
        assertEquals(JwtFailure.EXPIRED, auth.lastFailure)
        assertTrue((auth.state.value as AuthState.SignedIn).sessionExpired)
        // No se reintenta solo con nada guardado: sigue vencida hasta que la persona escriba su contraseña.
        assertNull(auth.validJwt())
        // Otra cuenta no sirve para "entrar de nuevo" (no se mezclan datos).
        fake.userId = "u-otra"; fake.session = true
        assertThrows(AuthException::class.java) { runBlocking { auth.reauthenticate("secreta123") } }
        fake.userId = "u-ana"
        auth.reauthenticate("secreta123")
        val s = auth.state.value as AuthState.SignedIn
        assertFalse(s.sessionExpired); assertEquals("u-ana", s.userId)
        assertNotNull(auth.validJwt(force = true))
    }

    /** U2: recuperar contraseña con el código de 6 dígitos del correo. */
    @Test fun recuperarContrasenaConCodigo() = runBlocking {
        val fake = FakeAuth()
        val auth = repo(fake)
        auth.requestPasswordReset("ana@ejemplo.com ")
        assertEquals("/email-otp/request-password-reset", fake.paths.last())
        assertTrue(fake.bodies.last().contains("\"email\":\"ana@ejemplo.com\""))
        val e = assertThrows(AuthException::class.java) { runBlocking { auth.resetPassword("ana@ejemplo.com", "000000", "nueva12345") } }
        assertTrue(e.message!!.contains("código"))
        auth.resetPassword("ana@ejemplo.com", "123456", "nueva12345")
        assertEquals("/email-otp/reset-password", fake.paths.last())
    }

    /** U1: del modo sin cuenta a una cuenta, sin perder nada; todo queda pendiente de subir. */
    @Test fun crearCuentaConservaLosDatosDelTelefono() = runBlocking {
        c.auth.useLocalMode("Local")
        val local = c.auth.currentUserId!!
        c.claimLocalData(local)
        val t = System.currentTimeMillis(); val day = Dates.localEpochDay(t, zone)
        val w = WorkoutEntity(Ids.uuidV7(t), local, null, "Pierna", t - 3_600_000, t, zone.id, day, 3600, updatedAt = t, dirty = false)
        val we = WorkoutExerciseEntity(Ids.uuidV7(t), w.id, local, "sentadilla_con_barra", 0, updatedAt = t, dirty = false)
        val st = WorkoutSetEntity(Ids.uuidV7(t), we.id, w.id, local, we.exerciseId, 0, weightKg = 60.0, reps = 8, completed = true, performedOn = day, updatedAt = t, dirty = false)
        c.db.workouts().insertFull(w, listOf(we), listOf(st))
        c.db.bodyWeights().upsertAll(listOf(BodyWeightEntity(local, day, 80.0, t, dirty = false)))
        val cuenta = "u-cuenta-nueva"
        c.adoptLocalData(cuenta)
        c.claimLocalData(cuenta)                                    // lo que hace la app al cambiar de usuario
        assertEquals("el entreno sigue ahí", "Pierna", c.db.workouts().get(w.id)?.name)
        assertEquals(cuenta, c.db.workouts().get(w.id)?.userId)
        assertEquals(cuenta, c.db.workouts().getSet(st.id)?.userId)
        assertTrue("queda pendiente de subir", c.db.workouts().dirtyWorkouts(10).any { it.id == w.id })
        assertTrue(c.db.workouts().dirtySets(10).any { it.id == st.id })
        assertEquals(80.0, c.db.bodyWeights().dirty(cuenta, 10).single().weightKg, 0.0)
        c.wipeLocalData(); c.auth.useLocalMode("QA")
    }

    /** U3: el mensaje de apoyo solo en hitos, máximo cada 30 días y nunca después de 3 "Ahora no". */
    @Test fun mensajeDeApoyoSoloEnHitos() {
        val day = 86_400_000L; val now = 1_000L * day
        assertEquals(0, SupportPolicy.milestone(2)); assertEquals(3, SupportPolicy.milestone(9)); assertEquals(10, SupportPolicy.milestone(24))
        assertEquals(100, SupportPolicy.milestone(150)); assertEquals(1_700, SupportPolicy.milestone(1_722))
        assertTrue(SupportPolicy.shouldPrompt(false, 3, "w3", null, 0, 0, 0, now))
        assertFalse("el 4.º entreno ya no lo muestra", SupportPolicy.shouldPrompt(false, 4, "w4", "w3", 3, now - day, 0, now))
        assertFalse("hito 10 pero hace menos de 30 días", SupportPolicy.shouldPrompt(false, 10, "w10", "w3", 3, now - 5 * day, 0, now))
        assertTrue("hito 10 y ya pasaron 30 días", SupportPolicy.shouldPrompt(false, 12, "w12", "w3", 3, now - 31 * day, 0, now))
        assertFalse("tras 3 «Ahora no» nunca más", SupportPolicy.shouldPrompt(false, 100, "w100", "w50", 50, now - 90 * day, 3, now))
        // Con la historia de la cuenta demo (1 722 entrenos en 8 años) lo vería ~20 veces como máximo, no 1 720.
        var shown = 0; var lastM = 0; var lastAt = 0L; var t = 0L
        for (n in 1..1_722) { t += 1_700_000_000L; if (SupportPolicy.shouldPrompt(false, n, "w$n", "w${n - 1}", lastM, lastAt, 0, t)) { shown++; lastM = SupportPolicy.milestone(n); lastAt = t } }
        assertTrue("lo vio $shown veces", shown in 1..25)
    }

    /** Y2/Y3: un mensaje por causa, en el orden del diagrama, con fecha relativa. */
    @Test fun estadoDeSincronizacionUnMensajePorCausa() {
        val now = java.time.ZonedDateTime.of(2026, 11, 17, 12, 0, 0, 0, zone).toInstant().toEpochMilli()
        fun s(st: SyncStatus, pending: Int = 0, local: Boolean = false, expired: Boolean = false) = SyncSummary.of(st, pending, local, expired, now, zone)
        assertEquals(SyncSummary.Level.ACT, s(SyncStatus(problem = SyncProblem.OFFLINE), 3, expired = true).level)
        assertEquals(SyncSummary.Action.SIGN_IN_AGAIN, s(SyncStatus(problem = SyncProblem.SESSION_EXPIRED), 2).action)
        s(SyncStatus(problem = SyncProblem.OFFLINE), 3).let { assertEquals("Sin internet", it.title); assertTrue(it.body.startsWith("3 cambios esperan")) }
        assertEquals("Rupio no responde", s(SyncStatus(problem = SyncProblem.SERVER)).title)
        assertEquals("2 cambios no se pudieron subir", s(SyncStatus(problem = SyncProblem.REJECTED, rejected = 2)).title)
        assertEquals("Subiendo 4 cambios…", s(SyncStatus(running = true), 4).title)
        assertEquals("Todo respaldado · hace 5 min", s(SyncStatus(lastSuccess = now - 5 * 60_000)).title)
        assertEquals("Todo respaldado · ayer 14:05", s(SyncStatus(lastSuccess = java.time.ZonedDateTime.of(2026, 11, 16, 14, 5, 0, 0, zone).toInstant().toEpochMilli())).title)
        assertTrue("3 días = fecha, no solo la hora", s(SyncStatus(lastSuccess = now - 3 * 86_400_000L)).title.contains("14 nov"))
        assertEquals("más de 24 h con pendientes es advertencia", SyncSummary.Level.WARN, s(SyncStatus(lastSuccess = now - 2 * 86_400_000L), 1).level)
        assertEquals(SyncSummary.Level.LOCAL, s(SyncStatus(), 0, local = true).level)
    }

    /** Q3: si Android cierra la app a media pausa, el descanso se restaura y suena a su hora. */
    @Test fun descansoSobreviveAQueSeCierreLaApp() {
        var now = 1_000_000L
        val saved = object : RestTimerController.Store {
            var s: RestTimerController.State? = null
            override fun save(s: RestTimerController.State?) { this.s = s }
            override fun load() = s
        }
        val a = RestTimerController { now }.also { it.store = saved }
        a.start(90, "s1", "Serie 2")
        now += 30_000
        val b = RestTimerController { now }.also { it.store = saved }       // "la app se reinició"
        val st = b.state.value!!
        assertEquals(60_000, st.remainingMs(now))
        now += 60_000
        assertTrue(b.tryConsumeFinish(st.generation))
        assertNull(saved.s)
    }
}
