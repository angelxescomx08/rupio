package com.rupio.app

import kotlinx.coroutines.flow.map

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.rupio.app.data.auth.AuthRepository
import com.rupio.app.data.auth.AuthState
import com.rupio.app.data.auth.NeonAuthClient
import com.rupio.app.data.billing.SupportRepository
import com.rupio.app.core.tr
import com.rupio.app.data.prefs.AppearancePrefs
import com.rupio.app.data.local.CatalogSeeder
import com.rupio.app.data.local.RupioDatabase
import com.rupio.app.data.prefs.SecureStore
import com.rupio.app.data.prefs.SettingsStore
import com.rupio.app.data.remote.DataApiClient
import com.rupio.app.data.remote.PendingDeletes
import com.rupio.app.data.remote.SyncEngine
import com.rupio.app.data.repo.BodyWeightRepository
import com.rupio.app.data.repo.ExerciseRepository
import com.rupio.app.data.repo.RoutineRepository
import com.rupio.app.data.repo.StatsRepository
import com.rupio.app.data.repo.WorkoutRepository
import com.rupio.app.timer.RestTimerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import androidx.room.withTransaction
import okhttp3.OkHttpClient
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/** Inyección de dependencias manual: un solo contenedor para toda la app. */
class AppContainer(val app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).callTimeout(45, TimeUnit.SECONDS)
        .build()
    val db = RupioDatabase.build(app)
    val settings = SettingsStore(app, BuildConfig.NEON_AUTH_URL, BuildConfig.NEON_DATA_API_URL)
    val secure = SecureStore(app)
    val auth = AuthRepository(secure, settings, NeonAuthClient(http), scope)
    private val uid: () -> String? = { auth.currentUserId }
    private val pendingDeletes = PendingDeletes(app)
    val exercises = ExerciseRepository(db, uid)
    val routines = RoutineRepository(db, uid)
    val workouts = WorkoutRepository(db, settings, uid, pendingDeletes)
    val stats = StatsRepository(db, settings)
    val bodyWeight = BodyWeightRepository(db, uid)
    private val dataApi = DataApiClient(http)
    val sync = SyncEngine(app, db, dataApi, auth, settings, pendingDeletes, onRemoteSetDeletes = { workouts.rebuildRecords(it) },
        onRebuildRecords = { workouts.rebuildAllRecords(); workouts.recomputeTrophies() },
        onConsentWithdrawnElsewhere = { db.admin().clearBodyWeights() })
    val restTimer = RestTimerController { SystemClock.elapsedRealtime() }.also {
        // Si Android cerró la app a media pausa, el descanso sigue y suena a su hora.
        it.store = com.rupio.app.timer.RestTimerPrefsStore(app) { SystemClock.elapsedRealtime() }
    }
    /** v1.8: cuenta lista para el muro de agradecimientos (no el modo sin cuenta ni una cuenta sin confirmar). */
    private fun wallAccount(): String? = (auth.state.value as? com.rupio.app.data.auth.AuthState.SignedIn)
        ?.takeIf { !it.localOnly && !it.needsVerification }?.userId
    val wall = com.rupio.app.data.billing.WallRepository(app, dataApi, { settings.current().dataApiUrl },
        jwt = { if (wallAccount() == null) null else auth.validJwt() }, userId = ::wallAccount,
        // v2.1: sin cuenta la lista se ve con un JWT anónimo (db/migrations/012_muro_publico.sql).
        anonJwt = { auth.anonymousJwt() })
    val support = SupportRepository(app, scope, wall, ::wallAccount)
    /** v1.9: ligas semanales por volumen (misma cuenta lista que el muro). No se piden al abrir la app, solo al verlas. */
    val league = com.rupio.app.data.league.LeagueRepository(app, dataApi, { settings.current().dataApiUrl },
        jwt = { if (wallAccount() == null) null else auth.validJwt() }, userId = ::wallAccount)
    val signOut = SignOutController(this)

    /** Pedidos de navegación desde fuera del NavHost (p. ej. "Ver el muro" en la pantalla de gracias). */
    val navRequests = kotlinx.coroutines.flow.MutableSharedFlow<String>(extraBufferCapacity = 2)

    init {
        // v2.1: los cambios se suben solos, en lote, unos minutos después (SyncEngine.soon). Durante un entreno no: cada
        // serie marcada sería una subida; el entreno se sube completo al terminarlo (SyncEngine.now). Sin cuenta, nunca.
        scope.launch {
            kotlinx.coroutines.flow.combine(db.workouts().observePending(), workouts.active, auth.state) { n, w, a ->
                val account = (a as? AuthState.SignedIn)?.let { !it.localOnly && !it.needsVerification } == true
                if (account && w == null) n else 0
            }.distinctUntilChanged().collect { pending -> if (pending > 0) SyncEngine.soon(app) }
        }
        // Al entrar, salir o confirmar el correo: se muestra el lugar en el muro de ESA cuenta y se actualiza.
        scope.launch {
            auth.state.map { wallAccount() }.distinctUntilChanged().collect { uid ->
                wall.onAccountChanged()
                league.onAccountChanged()
                if (uid != null) runCatching { wall.load() }
            }
        }
    }

    /**
     * v2.0: se cambió el idioma (en Perfil o en Ajustes de Android). El catálogo de ejercicios se vuelve a escribir en
     * ese idioma y los canales de notificación cambian de nombre (Android lo permite sin perder los ajustes del usuario).
     */
    fun onLanguageChanged() {
        scope.launch { CatalogSeeder.seedIfNeeded(app, db) }
        (app as? RupioApp)?.createChannels()
        localizeDefaultName()
    }

    /**
     * v2.1: el nombre por defecto sigue al idioma. Quien entró sin cuenta con el teléfono en inglés y luego eligió
     * español seguía viendo "Buenas noches, Athlete". Un nombre que la persona escribió no se toca.
     */
    fun localizeDefaultName() {
        val s = auth.state.value as? AuthState.SignedIn ?: return
        val default = tr("Atleta", "Athlete")
        if (s.name in DEFAULT_NAMES && s.name != default) auth.updateName(default)
    }

    /** La cuenta se acaba de eliminar: MainActivity muestra la confirmación encima de la pantalla de inicio de sesión. */
    val accountDeleted = MutableStateFlow(false)

    /** Avisos con "Deshacer" que deben verse aunque cambies de pantalla (p. ej. "Entrenamiento eliminado"). */
    val notices = kotlinx.coroutines.flow.MutableSharedFlow<Notice>(extraBufferCapacity = 4)

    /** ¿Hay red ahora? (para decir "Sin internet" en vez de tocar "Ahora" sin que pase nada). */
    fun isOnline(): Boolean = runCatching {
        val cm = app.getSystemService(android.net.ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }.getOrDefault(true)

    /** Zona horaria actual del teléfono; se actualiza al instante si el usuario la cambia o viaja. */
    private val _zone = MutableStateFlow(ZoneId.systemDefault())
    val zone: StateFlow<ZoneId> = _zone

    fun refreshZone() { _zone.value = ZoneId.systemDefault() }

    suspend fun wipeLocalData() {
        db.admin().clearWorkouts(); db.admin().clearRoutines(); db.admin().clearRecords(); db.admin().clearCustomExercises()
        db.admin().clearBodyWeights()
        settings.clearHealthConsent()
        sync.resetCursors()
    }

    /**
     * Los datos del teléfono pertenecen a UNA cuenta. Si entra otra (p. ej. la app se cerró a medio "Cerrar sesión"),
     * se borran antes de sincronizar, para no mostrar ni subir datos ajenos a la cuenta nueva.
     */
    suspend fun claimLocalData(uid: String) {
        val prefs = app.getSharedPreferences("rupio_owner", Context.MODE_PRIVATE)
        val owner = prefs.getString("uid", null)
        if (owner != null && owner != uid) wipeLocalData()
        prefs.edit().putString("uid", uid).putBoolean("local", auth.isLocalOnly).commit()
    }

    /**
     * Dueño de los datos restaurados de una copia de Google si era un usuario SIN cuenta (su id no viene en la
     * sesión cifrada, que no viaja). Así "Probar sin cuenta" en el teléfono nuevo recupera sus entrenos.
     */
    fun restoredLocalOwner(): String? = app.getSharedPreferences("rupio_owner", Context.MODE_PRIVATE)
        .takeIf { it.getBoolean("local", false) }?.getString("uid", null)

    /**
     * "Crear cuenta y conservar mis datos": todo lo guardado sin cuenta pasa a la cuenta [uid] y queda pendiente de
     * subir. Se marca como dueño ANTES de cambiar de sesión, para que claimLocalData no lo borre.
     */
    suspend fun adoptLocalData(uid: String) {
        val t = System.currentTimeMillis()
        val a = db.admin()
        db.withTransaction {
            a.adoptCustomExercises(uid, t); a.adoptRoutines(uid, t); a.adoptRoutineExercises(uid, t)
            a.adoptWorkouts(uid, t); a.adoptWorkoutExercises(uid, t); a.adoptSets(uid, t)
            a.adoptBodyWeights(uid, t)
            // v1.7 (N3): los récords del teléfono NO se suben: si la cuenta ya existía con un récord mayor (100 kg contra
            // 80 kg), el del teléfono lo pisaba. Se borran aquí y se recalculan con TODO el historial (el del teléfono y
            // el de la cuenta) después de la primera sincronización completa.
            a.clearRecords()
        }
        // Una cuenta que ya existía puede traer datos de otro teléfono: se bajan completos.
        sync.resetCursors()
        sync.requestRecordRebuild()
        settings.adoptHealthConsent()
        // v1.9: el nombre que tenías sin cuenta, por si cancelas la cuenta nueva ("¿Correo equivocado?") y regresas.
        val localName = (auth.state.value as? AuthState.SignedIn)?.takeIf { it.localOnly }?.name
        app.getSharedPreferences("rupio_owner", Context.MODE_PRIVATE).edit().putString("uid", uid).putBoolean("local", false)
            .putBoolean("adopted", true).putString("local_name", localName).commit()
    }

    /**
     * Correo confirmado: la cuenta queda lista y empieza a respaldarse (antes de esto no se sube nada).
     */
    suspend fun verifyEmail(code: String) {
        auth.verifyEmail(code)
        app.getSharedPreferences("rupio_owner", Context.MODE_PRIVATE).edit().remove("adopted").commit()
        SyncEngine.schedule(app); SyncEngine.now(app)
    }

    /**
     * Pantalla del código → "Usar otro correo" o "Salir". Si la cuenta se acaba de crear aquí (está vacía), se elimina en
     * el servidor para que el correo mal escrito no quede ocupado. Si los datos venían del modo sin cuenta, regresan a
     * él tal cual (nada se pierde); si no, se cierra la sesión.
     */
    suspend fun leaveUnverifiedAccount() {
        val s = auth.state.value as? AuthState.SignedIn ?: return
        if (s.newAccount) {
            val jwt = auth.validJwt(force = true) ?: throw com.rupio.app.data.auth.AuthException(OFFLINE)
            try { rpcRetryingFreshSession(jwt, "rupio_delete_my_account") }
            catch (e: java.io.IOException) { throw com.rupio.app.data.auth.AuthException(OFFLINE) }
            catch (e: com.rupio.app.data.remote.DataApiException) { throw com.rupio.app.data.auth.AuthException(tr("No se pudo cancelar la cuenta (código ${e.code}). Inténtalo de nuevo.", "The account couldn't be canceled (code ${e.code}). Please try again.")) }
        }
        val owner = app.getSharedPreferences("rupio_owner", Context.MODE_PRIVATE)
        if (owner.getBoolean("adopted", false)) {
            // De vuelta al modo sin cuenta con el MISMO id: claimLocalData ve al mismo dueño y no borra nada.
            auth.signOut(scope)
            auth.keepLocalId(s.userId)
            // v1.9: vuelve el nombre de antes del registro (antes se quedaba el de la cuenta cancelada: "QA", avatar "QT").
            val localName = owner.getString("local_name", null)?.takeIf { it.isNotBlank() } ?: s.name
            owner.edit().remove("adopted").remove("local_name").putString("uid", s.userId).putBoolean("local", true).commit()
            auth.useLocalMode(localName)
            sync.resetCursors()
            workouts.rebuildAllRecords()                    // los récords se habían quitado para recalcularse con la cuenta
        } else {
            wipeLocalData()
            owner.edit().clear().commit()
            auth.signOut(scope)
        }
    }

    /**
     * La Data API a veces responde 403 «Se necesita una sesión válida» a la PRIMERA llamada justo después de entrar
     * (se vio dos veces con la función de eliminar la cuenta; la segunda llamada funciona). Se reintenta hasta 2 veces.
     */
    private suspend fun rpcRetryingFreshSession(jwt: String, function: String) {
        val base = settings.current().dataApiUrl
        var attempt = 0
        while (true) {
            try { dataApi.rpc(base, jwt, function); return }
            catch (e: com.rupio.app.data.remote.DataApiException) {
                if (e.code != 403 || !e.message.orEmpty().contains("28000") || attempt >= 2) throw e
                attempt++; kotlinx.coroutines.delay(1_500L * attempt)
            }
        }
    }

    /** La persona marcó la casilla al registrar su peso: se guarda la fecha y se avisa a la cuenta. */
    suspend fun giveHealthConsent() {
        settings.setHealthConsent(System.currentTimeMillis())
        if (!auth.isLocalOnly) SyncEngine.now(app)
    }

    /**
     * Perfil → retirar el consentimiento: se borra TODO el peso corporal de este teléfono y, en la siguiente
     * sincronización, también de la cuenta (y de tus otros teléfonos).
     */
    suspend fun withdrawHealthConsent() {
        db.admin().clearBodyWeights()
        settings.setHealthConsent(null)
        if (!auth.isLocalOnly) SyncEngine.now(app)
    }

    /**
     * Eliminar la cuenta: confirma la contraseña, borra en el servidor (función SQL rupio_delete_my_account: todas las
     * filas y el usuario de Neon Auth, en una sola transacción) y luego todo lo del teléfono.
     */
    suspend fun deleteAccount(password: String) {
        auth.reauthenticate(password)
        val jwt = auth.validJwt(force = true) ?: throw com.rupio.app.data.auth.AuthException(tr("No se pudo confirmar tu sesión. Revisa tu internet e inténtalo de nuevo.", "We couldn't confirm your session. Check your internet and try again."))
        // v1.7 (N5): ninguna sincronización a la vez. Se cancelan las programadas y se espera la que esté en curso;
        // si no, una subida a medias podía volver a crear filas mientras el servidor borraba la cuenta.
        SyncEngine.cancel(app)
        sync.exclusive {
            try {
                rpcRetryingFreshSession(jwt, "rupio_delete_my_account")
            } catch (e: java.io.IOException) {
                SyncEngine.schedule(app)
                throw com.rupio.app.data.auth.AuthException(OFFLINE)
            } catch (e: com.rupio.app.data.remote.DataApiException) {
                SyncEngine.schedule(app)
                throw com.rupio.app.data.auth.AuthException(tr("No se pudo eliminar la cuenta (código ${e.code}). No se borró nada; inténtalo más tarde.", "The account couldn't be deleted (code ${e.code}). Nothing was erased; try again later."))
            }
            restTimer.cancel(); runCatching { com.rupio.app.timer.WorkoutService.stop(app) }
            wipeLocalData()
            app.getSharedPreferences("rupio_owner", Context.MODE_PRIVATE).edit().clear().commit()
        }
        auth.signOut()
    }
}

/** El nombre que se pone si no escribes uno, en cada idioma. */
internal val DEFAULT_NAMES = setOf("Atleta", "Athlete")

private val OFFLINE: String get() = tr("Sin conexión. Revisa tu internet e inténtalo de nuevo.", "No connection. Check your internet and try again.")

/** Aviso breve para el snackbar global; [undo] agrega el botón "Deshacer". */
data class Notice(val text: String, val undo: (suspend () -> Unit)? = null)

class RupioApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // v2.0: idioma y tema antes de todo (quien ya usaba Rupio se queda en español y oscuro; ver AppearancePrefs).
        AppearancePrefs.migrateLegacy(this)
        AppearancePrefs.apply(this)
        container = AppContainer(this)
        createChannels()
        container.scope.launch { CatalogSeeder.seedIfNeeded(this@RupioApp, container.db) }
        // Confirma con Google Play las compras de apoyo (reembolsos, reinstalaciones, pagos pendientes en OXXO).
        container.support.connect()
        // Cambios de zona horaria / hora del sistema → la UI se repinta con la hora correcta.
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                java.util.TimeZone.setDefault(null)
                container.refreshZone()
            }
        }
        ContextCompat.registerReceiver(this, receiver, IntentFilter().apply {
            addAction(Intent.ACTION_TIMEZONE_CHANGED); addAction(Intent.ACTION_TIME_CHANGED)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)

        container.scope.launch {
            container.auth.state.distinctUntilChanged { a, b -> (a as? AuthState.SignedIn)?.userId == (b as? AuthState.SignedIn)?.userId }
                .collect { s ->
                    if (s is AuthState.SignedIn) {
                        container.claimLocalData(s.userId)
                        container.localizeDefaultName()      // por si el idioma del teléfono cambió con la app cerrada
                        // Una vez por versión: récords consistentes con el historial (p. ej. si borraste entrenos).
                        val prefs = getSharedPreferences("rupio_app", MODE_PRIVATE)
                        // Una vez: re-descarga todo para traer columnas nuevas (planes por serie) y el peso corporal.
                        if (prefs.getInt("pull_reset", 0) < 3) { container.sync.resetPullCursors(); prefs.edit().putInt("pull_reset", 3).apply() }
                        // v1.9 (N5): y los trofeos de los entrenos guardados antes (la primera vez ya no es récord).
                        if (prefs.getInt("records_rebuilt", 0) < 3) {
                            runCatching { container.workouts.rebuildAllRecords(); container.workouts.recomputeTrophies() }
                            prefs.edit().putInt("records_rebuilt", 3).apply()
                        }
                        if (!s.localOnly) { SyncEngine.schedule(this@RupioApp); SyncEngine.now(this@RupioApp) }
                    }
                }
        }
    }

    /** Los canales se vuelven a crear al cambiar de idioma: Android solo actualiza el nombre y la descripción. */
    fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        // Canal SILENCIOSO para la notificación en curso (cronómetro y cuenta regresiva).
        nm.createNotificationChannel(NotificationChannel(CHANNEL_WORKOUT, tr("Entrenamiento activo", "Active workout"), NotificationManager.IMPORTANCE_LOW).apply {
            setSound(null, null); enableVibration(false); setShowBadge(false)
            description = tr("Cronómetro de la sesión y cuenta regresiva del descanso", "Session timer and rest countdown")
        })
        // Canal CON SONIDO para el aviso de fin de descanso (el único sonido de la app).
        nm.createNotificationChannel(NotificationChannel(CHANNEL_REST, tr("Fin del descanso", "Rest finished"), NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(com.rupio.app.timer.RestAlert.soundUri(this@RupioApp), AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            enableVibration(true); vibrationPattern = longArrayOf(0, 180, 120, 260)
            setShowBadge(false)
            description = tr("Suena una vez cuando termina tu descanso entre series", "Plays once when your rest between sets is over")
        })
        // Mismo aviso sin sonido de canal: para "Solo vibración" y "Con el volumen de la música" (ese sonido lo pone la app).
        nm.createNotificationChannel(NotificationChannel(CHANNEL_REST_QUIET, tr("Fin del descanso (sin sonido)", "Rest finished (silent)"), NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(null, null); enableVibration(true); vibrationPattern = longArrayOf(0, 180, 120, 260)
            setShowBadge(false)
            description = tr("Vibra cuando termina tu descanso (el sonido lo decide el ajuste de Rupio)", "Vibrates when your rest is over (Rupio's setting decides the sound)")
        })
    }

    companion object {
        const val CHANNEL_WORKOUT = "workout_active"
        const val CHANNEL_REST = "rest_done_v1"
        const val CHANNEL_REST_QUIET = "rest_done_quiet_v1"
    }
}
