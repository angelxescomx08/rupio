package com.rupio.app

import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import com.rupio.app.core.Dates
import com.rupio.app.core.Ids
import com.rupio.app.data.auth.AuthException
import com.rupio.app.data.auth.AuthRepository
import com.rupio.app.data.auth.AuthState
import com.rupio.app.data.auth.NeonAuthClient
import com.rupio.app.data.export.DataExport
import com.rupio.app.data.local.BodyWeightEntity
import com.rupio.app.data.local.WorkoutEntity
import com.rupio.app.data.local.WorkoutExerciseEntity
import com.rupio.app.data.local.WorkoutSetEntity
import com.rupio.app.data.prefs.RestAlertMode
import com.rupio.app.data.prefs.SecureStore
import com.rupio.app.data.prefs.SettingsStore
import com.rupio.app.data.remote.DataApiClient
import com.rupio.app.data.remote.PendingDeletes
import com.rupio.app.data.remote.SyncEngine
import com.rupio.app.data.repo.EditedSet
import com.rupio.app.domain.RecordKind
import com.rupio.app.domain.SetType
import com.rupio.app.timer.RestAlertPolicy
import com.rupio.app.ui.auth.cleanOtp
import com.rupio.app.ui.components.FitTextRules
import com.rupio.app.ui.legal.PrivacyPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.AudioDeviceInfoBuilder
import java.io.File
import java.time.ZoneId
import java.util.Base64
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Segunda revisión QA (v1.7): una prueba por cada cambio N1–N9 (ver docs/QA_V17.md). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class V17Test {
    private val app get() = ApplicationProvider.getApplicationContext<RupioApp>()
    private val c get() = app.container
    private val zone = ZoneId.of("America/Mexico_City")
    private val bench = "press_de_banca_con_barra"

    @Before fun setUp() = runBlocking {
        c.auth.useLocalMode("QA")
        repeat(200) { if (c.db.exercises().catalogCount() >= 250) return@repeat; Thread.sleep(50) }
        c.wipeLocalData()
        PendingDeletes(app).clear()
    }

    private fun engine(server: QaDataSyncTest.FakeServer, user: String) = SyncEngine(
        app, c.db, DataApiClient(OkHttpClient.Builder().addInterceptor(server.interceptor).build()), c.auth, c.settings, PendingDeletes(app),
        jwtProvider = { "jwt-de-prueba" }, uidProvider = { user }, localOnly = { false },
        onRebuildRecords = { c.workouts.rebuildAllRecords() }, onConsentWithdrawnElsewhere = { c.db.admin().clearBodyWeights() },
    )

    /** Un entreno terminado de press de banca con estas series (kg × reps). Devuelve el entreno y sus series. */
    private suspend fun workout(user: String, vararg sets: Pair<Double, Int>, daysAgo: Int = 1, dirty: Boolean = true): Pair<WorkoutEntity, List<WorkoutSetEntity>> {
        val start = System.currentTimeMillis() - daysAgo * 86_400_000L
        val day = Dates.localEpochDay(start, zone)
        val w = WorkoutEntity(Ids.uuidV7(start), user, null, "Pecho", start, start + 3_000_000, zone.id, day, 3000,
            totalVolumeKg = sets.sumOf { it.first * it.second }, totalSets = sets.size, totalReps = sets.sumOf { it.second }, updatedAt = start, dirty = dirty)
        val we = WorkoutExerciseEntity(Ids.uuidV7(start), w.id, user, bench, 0, updatedAt = start, dirty = dirty)
        val ss = sets.mapIndexed { i, (kg, r) -> WorkoutSetEntity(Ids.uuidV7(start), we.id, w.id, user, bench, i, weightKg = kg, reps = r, completed = true,
            completedAt = start + i * 120_000L, performedOn = day, updatedAt = start, dirty = dirty) }
        c.db.workouts().insertFull(w, listOf(we), ss)
        return w to ss
    }

    private fun serverRecord(server: QaDataSyncTest.FakeServer, user: String, kind: RecordKind) = server.rows("personal_records")
        .firstOrNull { it["user_id"]?.jsonPrimitive?.content == user && it["exercise_id"]?.jsonPrimitive?.content == bench && it["kind"]?.jsonPrimitive?.content == kind.code.toString() }
        ?.get("value")?.jsonPrimitive?.doubleOrNull

    // ------------------------------------------------------------------ N1

    /** N1: editar un entreno terminado recalcula volumen, series, reps y récords, y lo marca para subir. */
    @Test fun n1_editarEntrenoTerminadoRecalculaTodo() = runBlocking {
        val user = c.auth.currentUserId!!
        // v1.9: una sesión anterior (55 kg): sin ella los 70 kg serían tu primera marca, no un récord con trofeo.
        workout(user, 55.0 to 8, daysAgo = 3, dirty = false)
        val (w, sets) = workout(user, 60.0 to 8, 60.0 to 8, dirty = false)
        c.workouts.rebuildRecords(setOf(bench))
        assertEquals(60.0, c.db.records().forExercise(bench).first { it.kind == RecordKind.MAX_WEIGHT.code }.value, 0.0)
        val we = sets[0].workoutExerciseId
        val res = c.workouts.saveEdits(w.id, listOf(
            EditedSet(sets[0].id, we, bench, SetType.NORMAL.code, 70.0, 8, null),           // 60 → 70 kg (el peso mal escrito)
            EditedSet(null, we, bench, SetType.NORMAL.code, 65.0, 10, null),                // una serie que faltó
        ))                                                                                   // y la 2.ª serie se quita
        val after = c.db.workouts().get(w.id)!!
        assertEquals(70.0 * 8 + 65.0 * 10, after.totalVolumeKg, 1e-9)
        assertEquals(2, after.totalSets); assertEquals(18, after.totalReps)
        assertTrue("el entreno se vuelve a subir", after.dirty)
        assertTrue(c.db.workouts().getSet(sets[1].id)!!.deleted)
        assertEquals(setOf(sets[1].id), res.removed)
        val added = c.db.workouts().sets(w.id).single { it.id !in sets.map { s -> s.id } && !it.deleted }
        assertTrue(added.completed); assertEquals(w.localDate, added.performedOn); assertEquals(2, added.position)
        assertEquals(setOf(sets[0].id, added.id), res.touched)
        assertEquals("el récord se recalcula", 70.0, c.db.records().forExercise(bench).first { it.kind == RecordKind.MAX_WEIGHT.code }.value, 0.0)
        assertTrue("la serie corregida tiene el trofeo", c.db.workouts().getSet(sets[0].id)!!.isPr)
        // Sin cambios no se toca nada (no se marca para subir otra vez).
        c.db.workouts().upsertWorkout(after.copy(dirty = false))
        val same = c.workouts.saveEdits(w.id, c.db.workouts().sets(w.id).filter { it.completed && !it.deleted }.map {
            EditedSet(it.id, it.workoutExerciseId, it.exerciseId, it.setType, it.weightKg, it.reps, it.durationSec, it.rpe) })
        assertTrue(same.touched.isEmpty() && same.removed.isEmpty())
        assertFalse(c.db.workouts().get(w.id)!!.dirty)
    }

    // ------------------------------------------------------------------ N2

    /** N2: el aviso (app y páginas web) trae correo directo y domicilio del responsable, ya no "la ficha de Google Play". */
    @Test fun n2_avisoConCorreoYDomicilio() {
        val app = PrivacyPolicy.sections.joinToString("\n") { it.first + "\n" + it.second }
        val web = File("../docs/privacidad.html").readText()
        val del = File("../docs/eliminar-cuenta.html").readText()
        for ((name, text) in listOf("app" to app, "privacidad.html" to web, "eliminar-cuenta.html" to del)) {
            assertTrue("$name: correo", text.contains("jose.angel.hdz.rda@gmail.com"))
            assertFalse("$name: ya no remite a la ficha de Play", text.contains("ficha de Rupio en Google Play") || text.contains("ficha de Google Play"))
        }
        for ((name, text) in listOf("app" to app, "privacidad.html" to web)) {
            assertTrue("$name: domicilio", text.contains("C.P. 56618") && text.contains("Valle de Chalco Solidaridad"))
            assertTrue("$name: consentimiento expreso del peso", text.contains("lo aceptas expresamente"))
            assertTrue("$name: cómo retirarlo", text.contains("Permiso de peso corporal"))
        }
        assertFalse("el teléfono personal no se publica", app.contains("5512396349") || web.contains("5512396349"))
    }

    /** N2: retirar el consentimiento borra el peso del teléfono y de la cuenta; retirarlo en otro teléfono también lo borra aquí. */
    @Test fun n2_retirarConsentimientoBorraElPeso() = runBlocking {
        val server = QaDataSyncTest.FakeServer(); val user = "u-peso"
        val e = engine(server, user).also { it.resetCursors() }
        val day = Dates.today(zone).toEpochDay().toInt(); val t = System.currentTimeMillis()
        c.giveHealthConsent()
        c.db.bodyWeights().upsertAll((0 until 3).map { BodyWeightEntity(user, day - it, 80.0 + it, t) })
        assertTrue(e.sync())
        assertEquals(3, server.rows("body_weights").size)
        val consent = server.rows("profiles").single()["health_consent_at"]
        assertTrue("la fecha del consentimiento queda en la cuenta: $consent", consent != null && consent !is JsonNull)

        c.withdrawHealthConsent()
        assertTrue("se borra del teléfono al instante", c.db.admin().exportBodyWeights().isEmpty())
        assertTrue(e.sync())
        assertTrue("y de la cuenta", server.rows("body_weights").isEmpty())
        assertTrue(server.rows("profiles").single()["health_consent_at"] is JsonNull)
        c.settings.current().let { assertNull(it.healthConsentAt); assertFalse(it.healthConsentDirty); assertFalse(it.healthPurgePending) }

        // Otro teléfono lo retira: aquí se borra el peso en la siguiente sincronización.
        c.giveHealthConsent(); assertTrue(e.sync())
        c.db.bodyWeights().upsertAll(listOf(BodyWeightEntity(user, day, 81.0, t, dirty = false)))
        val row = server.tables["profiles"]!!.entries.single()
        row.setValue(JsonObject(row.value + ("health_consent_at" to JsonNull)))
        assertTrue(e.sync())
        assertTrue(c.db.admin().exportBodyWeights().isEmpty())
        assertNull(c.settings.current().healthConsentAt)
    }

    // ------------------------------------------------------------------ N3

    /** N3: entrar a una cuenta que ya existe desde el modo sin cuenta: el récord de 100 kg de la cuenta no lo pisa el de 80 kg. */
    @Test fun n3_elRecordMayorDeLaCuentaSeConserva() = runBlocking {
        val server = QaDataSyncTest.FakeServer(); val account = "u-cuenta"
        // La cuenta ya existía (otro teléfono): 100 kg en press de banca.
        workout(account, 100.0 to 3)
        c.workouts.rebuildRecords(setOf(bench))
        assertTrue(engine(server, account).also { it.resetCursors() }.sync())
        assertEquals(100.0, serverRecord(server, account, RecordKind.MAX_WEIGHT)!!, 0.0)

        // Teléfono nuevo, sin cuenta: 80 kg.
        c.wipeLocalData(); PendingDeletes(app).clear()
        val local = c.auth.currentUserId!!
        c.claimLocalData(local)
        workout(local, 80.0 to 5, daysAgo = 0)
        c.workouts.rebuildRecords(setOf(bench))
        assertEquals(80.0, c.db.records().forExercise(bench).first { it.kind == RecordKind.MAX_WEIGHT.code }.value, 0.0)

        c.adoptLocalData(account)
        assertTrue("los récords del teléfono no se suben", c.db.records().dirty(100).isEmpty())
        assertTrue(engine(server, account).sync())               // sin resetCursors: la app ya los reinició al adoptar
        assertEquals("sigue el de la cuenta", 100.0, serverRecord(server, account, RecordKind.MAX_WEIGHT)!!, 0.0)
        assertEquals("y el teléfono lo muestra", 100.0, c.db.records().forExercise(bench).first { it.kind == RecordKind.MAX_WEIGHT.code }.value, 0.0)
        assertEquals("el entreno del teléfono sí se subió", 2, server.rows("workouts").size)
        c.wipeLocalData(); app.getSharedPreferences("rupio_owner", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        c.auth.useLocalMode("QA")
    }

    // ------------------------------------------------------------------ N4

    /** N4: borrar el único entreno de un ejercicio → Deshacer → sincronizar: el récord sigue en el servidor. */
    @Test fun n4_deshacerBorradoConservaElRecordEnElServidor() = runBlocking {
        val server = QaDataSyncTest.FakeServer(); val user = "u-n4"
        val e = engine(server, user).also { it.resetCursors() }
        val (w, _) = workout(user, 80.0 to 5)
        c.workouts.rebuildRecords(setOf(bench))
        assertTrue(e.sync())
        assertEquals(80.0, serverRecord(server, user, RecordKind.MAX_WEIGHT)!!, 0.0)

        val undo = c.workouts.deleteWorkout(w.id)
        assertTrue("al borrar, el récord queda por borrar en el servidor", PendingDeletes(app).records().any { it.first == bench })
        undo()
        assertTrue("al deshacer, ya no se borra", PendingDeletes(app).records().none { it.first == bench })
        assertTrue(e.sync())
        assertEquals("el récord sigue en el servidor", 80.0, serverRecord(server, user, RecordKind.MAX_WEIGHT)!!, 0.0)
        assertFalse(c.db.workouts().get(w.id)!!.deleted)
    }

    // ------------------------------------------------------------------ N5

    /** N5: lo que corre en exclusive (eliminar la cuenta) espera a que termine la sincronización en curso. */
    @Test fun n5_eliminarCuentaEsperaLaSincronizacion() = runBlocking {
        val server = QaDataSyncTest.FakeServer()
        val e = engine(server, "u-n5").also { it.resetCursors() }
        workout("u-n5", 50.0 to 10)
        val started = CountDownLatch(1); val release = CountDownLatch(1)
        server.failWhen = { started.countDown(); release.await(10, TimeUnit.SECONDS); false }
        val order = Collections.synchronizedList(mutableListOf<String>())
        val s = async(Dispatchers.IO) { e.sync(); order += "sync" }
        assertTrue(started.await(10, TimeUnit.SECONDS))
        val rpc = async(Dispatchers.IO) { e.exclusive { order += "eliminar" } }
        Thread.sleep(400)
        assertTrue("no corre mientras se sincroniza: $order", order.isEmpty())
        release.countDown()
        awaitAll(s, rpc)
        assertEquals(listOf("sync", "eliminar"), order)
    }

    // ------------------------------------------------------------------ N6

    private class FakeAuth {
        val paths = mutableListOf<String>(); val bodies = mutableListOf<String>()
        var verified = false
        var otpSendStatus = 200
        private fun jwt() = "h." + Base64.getUrlEncoder().withoutPadding()
            .encodeToString("""{"sub":"u-ana","exp":${System.currentTimeMillis() / 1000 + 900}}""".toByteArray()) + ".s"
        val interceptor = Interceptor { chain ->
            val req = chain.request()
            val path = req.url.encodedPath.substringAfter("/auth"); paths += path
            bodies += req.body?.let { b -> Buffer().also { b.writeTo(it) }.readUtf8() }.orEmpty()
            fun resp(code: Int, body: String, auth: Boolean = false) = Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(code).message("x")
                .apply { if (auth) { header("set-auth-jwt", jwt()); header("Set-Cookie", "better-auth.session_token=tok; Path=/; HttpOnly") } }
                .body(body.toResponseBody("application/json".toMediaType())).build()
            when (path) {
                "/sign-up/email", "/sign-in/email" -> resp(200, """{"token":"t","user":{"id":"u-ana","name":"Ana","email":"ana@ejemplo.com","emailVerified":$verified}}""", auth = true)
                "/get-session" -> resp(200, """{"session":{"token":"t"},"user":{"id":"u-ana"}}""", auth = true)
                "/email-otp/send-verification-otp" -> if (otpSendStatus == 200) resp(200, """{"success":true}""") else resp(otpSendStatus, "{}")
                "/email-otp/verify-email" -> if (bodies.last().contains("\"otp\":\"654321\"")) { verified = true; resp(200, """{"status":true,"user":{"id":"u-ana","emailVerified":true}}""", auth = true) }
                    else resp(400, """{"code":"INVALID_OTP","message":"Invalid OTP"}""")
                else -> resp(404, "{}")
            }
        }
    }

    /** N6: la cuenta recién creada pide el código de 6 dígitos del correo y no se respalda nada hasta confirmarlo. */
    @Test fun n6_errorAlEnviarConservaLaCuentaYPermiteReintentar() = runBlocking {
        val fake = FakeAuth().apply { otpSendStatus = 503 }
        val settings = SettingsStore(app, "https://auth.ejemplo/neondb/auth", "https://api.ejemplo/rest/v1")
        val auth = AuthRepository(SecureStore(app), settings, NeonAuthClient(OkHttpClient.Builder().addInterceptor(fake.interceptor).build()))

        auth.signUp("Ana", "ana@ejemplo.com", "secreta123")

        val session = auth.state.value as AuthState.SignedIn
        assertTrue(session.needsVerification)
        assertTrue(session.newAccount)
        assertTrue(session.verificationSendError.orEmpty().contains("503"))

        fake.otpSendStatus = 200
        auth.sendVerificationCode()
        assertEquals(2, fake.paths.count { it == "/email-otp/send-verification-otp" })
        auth.verifyEmail("654321")
        assertFalse(auth.needsVerification)
    }

    @Test fun n6_cuentaNuevaPideCodigoAntesDeEstarLista() = runBlocking {
        val fake = FakeAuth(); val secure = SecureStore(app)
        val settings = SettingsStore(app, "https://auth.ejemplo/neondb/auth", "https://api.ejemplo/rest/v1")
        val auth = AuthRepository(secure, settings, NeonAuthClient(OkHttpClient.Builder().addInterceptor(fake.interceptor).build()))
        auth.signUp("Ana", "ana@ejemplo.com", "secreta123")
        val s = auth.state.value as AuthState.SignedIn
        assertTrue(s.needsVerification); assertTrue(s.newAccount)
        val sent = fake.paths.indexOf("/email-otp/send-verification-otp")
        assertTrue("se manda el código", sent >= 0)
        assertTrue(fake.bodies[sent].contains("\"type\":\"email-verification\"") && fake.bodies[sent].contains("ana@ejemplo.com"))

        // Mientras no se confirme, la sincronización no sube nada.
        val server = QaDataSyncTest.FakeServer()
        val e = SyncEngine(app, c.db, DataApiClient(OkHttpClient.Builder().addInterceptor(server.interceptor).build()), auth, c.settings, PendingDeletes(app),
            jwtProvider = { "jwt" }, uidProvider = { "u-ana" })
        workout("u-ana", 40.0 to 10)
        assertTrue(e.sync()); assertEquals("nada se sube sin confirmar", 0, server.requests)

        val wrong = assertThrows(AuthException::class.java) { runBlocking { auth.verifyEmail("111111") } }
        assertTrue(wrong.message!!.contains("código"))
        auth.verifyEmail(cleanOtp("Tu código: 654 321"))
        assertFalse((auth.state.value as AuthState.SignedIn).needsVerification)
        // Al reabrir la app sigue confirmada.
        val reopened = AuthRepository(secure, settings, NeonAuthClient(OkHttpClient.Builder().addInterceptor(fake.interceptor).build()))
        repeat(50) { if (reopened.state.value is AuthState.SignedIn) return@repeat; Thread.sleep(20) }
        assertFalse((reopened.state.value as AuthState.SignedIn).needsVerification)
        e.resetCursors()
        assertTrue(e.sync()); assertTrue("ya se respalda", server.rows("workouts").isNotEmpty())

        // Una cuenta que ya estaba confirmada entra directo.
        assertEquals("123456", cleanOtp("12-34-56 789"))
        auth.signOut(); fake.verified = true
        auth.signIn("ana@ejemplo.com", "secreta123")
        assertFalse((auth.state.value as AuthState.SignedIn).needsVerification)
        auth.signOut(); c.auth.useLocalMode("QA")
    }

    // ------------------------------------------------------------------ N7

    /** N7: con letra agrandada caben 3 líneas y nunca se achica por debajo de ~150 % del tamaño normal. */
    @Test fun n7_textoGrandeNoBajaDe150PorCiento() {
        // 16 sp a 200 %: normal = 8 sp → piso 12 sp (150 %), antes 8 sp (100 %).
        assertEquals(12f, FitTextRules.floorSp(16f, 2f), 1e-4f)
        assertEquals("a 130 % el piso sería 115 %: nunca más que el tamaño pedido", 16f, FitTextRules.floorSp(16f, 1.3f), 1e-4f)
        assertEquals(16f, FitTextRules.floorSp(16f, 1f), 1e-4f)
        assertEquals(3, FitTextRules.maxLines(2, 2f))
        assertEquals(2, FitTextRules.maxLines(2, 1f))
        assertEquals(4, FitTextRules.maxLines(4, 1.5f))
    }

    // ------------------------------------------------------------------ N8

    /** N8: "Con el volumen de la música" en silencio y sin audífonos ni Bluetooth: solo vibra. */
    @Test fun n8_musicaEnSilencioSinAudifonosSoloVibra() {
        val m = RestAlertMode.MEDIA
        RestAlertPolicy.decide(m, AudioManager.RINGER_MODE_SILENT, privateOutput = false, force = false).let { assertFalse(it.sound); assertTrue(it.vibrate) }
        // v1.8: en VIBRACIÓN ahora sí suena (con el volumen de la música): así casi nunca sonaba en el gimnasio. Ver V18Test.
        RestAlertPolicy.decide(m, AudioManager.RINGER_MODE_VIBRATE, privateOutput = false, force = false).let { assertTrue(it.sound); assertTrue(it.vibrate) }
        assertTrue("con audífonos sí suena", RestAlertPolicy.decide(m, AudioManager.RINGER_MODE_SILENT, privateOutput = true, force = false).sound)
        assertTrue("timbre normal: suena", RestAlertPolicy.decide(m, AudioManager.RINGER_MODE_NORMAL, privateOutput = false, force = false).sound)
        assertTrue("«Probar» siempre suena", RestAlertPolicy.decide(m, AudioManager.RINGER_MODE_SILENT, privateOutput = false, force = true).sound)
        assertFalse("modo sonido en silencio: no suena (igual que antes)", RestAlertPolicy.decide(RestAlertMode.SOUND, AudioManager.RINGER_MODE_SILENT, false, false).sound)

        val am = app.getSystemService(AudioManager::class.java)
        shadowOf(am).setOutputDevices(listOf(AudioDeviceInfoBuilder.newBuilder().setType(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER).build()))
        assertFalse("solo la bocina del teléfono", RestAlertPolicy.hasPrivateOutput(am))
        shadowOf(am).setOutputDevices(listOf(AudioDeviceInfoBuilder.newBuilder().setType(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER).build(),
            AudioDeviceInfoBuilder.newBuilder().setType(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP).build()))
        assertTrue("audífonos Bluetooth", RestAlertPolicy.hasPrivateOutput(am))
        shadowOf(am).setOutputDevices(listOf(AudioDeviceInfoBuilder.newBuilder().setType(AudioDeviceInfo.TYPE_WIRED_HEADPHONES).build()))
        assertTrue("audífonos de cable", RestAlertPolicy.hasPrivateOutput(am))
    }

    // ------------------------------------------------------------------ N9

    /** N9: "Exportar mis datos" genera 3 CSV (entrenos, series, peso) y los comparte con el menú de Android. */
    @Test fun n9_exportarGeneraTresCsvYLosComparte() = runBlocking {
        val user = c.auth.currentUserId!!
        val (w, _) = workout(user, 102.5 to 5, 100.0 to 6)
        c.db.workouts().upsertWorkout(c.db.workouts().get(w.id)!!.copy(name = "=Pecho, \"pesado\"", notes = "línea 1\nlínea 2"))
        val day = Dates.today(zone).toEpochDay().toInt()
        c.db.bodyWeights().upsertAll(listOf(BodyWeightEntity(user, day, 78.4, System.currentTimeMillis())))

        val files = DataExport.write(app, c.db)
        assertEquals(listOf(DataExport.WORKOUTS, DataExport.SETS, DataExport.BODY), files.map { it.name })
        val (wcsv, scsv, bcsv) = files.map { it.readText() }
        assertTrue("BOM para Excel", wcsv.startsWith("﻿"))
        assertTrue(wcsv.lines()[0].endsWith("id,fecha,nombre,inicio,fin,zona_horaria,duracion_min,volumen_kg,series,repeticiones,records,notas"))
        assertTrue("comillas y fórmula neutralizada: $wcsv", wcsv.contains("\"'=Pecho, \"\"pesado\"\"\""))
        assertTrue("salto de línea entre comillas", wcsv.contains("\"línea 1\nlínea 2\""))
        val setRows = scsv.trim().lines().drop(1)
        assertEquals(2, setRows.size)
        assertTrue(setRows[0], setRows[0].contains(",1,Normal,102.5,5,"))
        assertTrue(setRows[1].contains(",2,Normal,100,6,"))
        assertEquals("﻿fecha,peso_kg\n${java.time.LocalDate.ofEpochDay(day.toLong())},78.4\n", bcsv)
        assertEquals("'-2", DataExport.text("-2")); assertEquals("80", DataExport.num(80.0)); assertEquals("", DataExport.num(null))

        // Se comparten con permiso de lectura temporal (FileProvider), los 3 a la vez.
        // FileProvider solo reconoce rutas con "/" (las de Android); en Windows Robolectric arma la caché con "\".
        org.junit.Assume.assumeTrue("FileProvider necesita rutas con /: corre esta parte en Linux o en CI", java.io.File.separatorChar == '/')
        val uri = FileProvider.getUriForFile(app, DataExport.authority(app), files[0])
        assertEquals("content", uri.scheme)
        DataExport.share(app, files)
        val chooser = shadowOf(app).nextStartedActivity
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        val send = chooser.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)!!
        assertEquals(Intent.ACTION_SEND_MULTIPLE, send.action)
        assertEquals(3, send.getParcelableArrayListExtra(Intent.EXTRA_STREAM, android.net.Uri::class.java)!!.size)
        assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }
}
