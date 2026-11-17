package com.rupio.app

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
    val sync = SyncEngine(app, db, dataApi, auth, settings, pendingDeletes, onRemoteSetDeletes = { workouts.rebuildRecords(it) })
    val restTimer = RestTimerController { SystemClock.elapsedRealtime() }.also {
        // Si Android cerró la app a media pausa, el descanso sigue y suena a su hora.
        it.store = com.rupio.app.timer.RestTimerPrefsStore(app) { SystemClock.elapsedRealtime() }
    }
    val support = SupportRepository(app, scope)
    val signOut = SignOutController(this)

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
            a.adoptRecords(uid, t); a.adoptBodyWeights(uid, t)
        }
        // Una cuenta que ya existía puede traer datos de otro teléfono: se bajan completos.
        sync.resetCursors()
        app.getSharedPreferences("rupio_owner", Context.MODE_PRIVATE).edit().putString("uid", uid).putBoolean("local", false).commit()
    }

    /**
     * Eliminar la cuenta: confirma la contraseña, borra en el servidor (función SQL rupio_delete_my_account: todas las
     * filas y el usuario de Neon Auth, en una sola transacción) y luego todo lo del teléfono.
     */
    suspend fun deleteAccount(password: String) {
        auth.reauthenticate(password)
        val jwt = auth.validJwt(force = true) ?: throw com.rupio.app.data.auth.AuthException("No se pudo confirmar tu sesión. Revisa tu internet e inténtalo de nuevo.")
        val base = settings.current().dataApiUrl
        try {
            dataApi.rpc(base, jwt, "rupio_delete_my_account")
        } catch (e: java.io.IOException) {
            throw com.rupio.app.data.auth.AuthException("Sin conexión. Revisa tu internet e inténtalo de nuevo.")
        } catch (e: com.rupio.app.data.remote.DataApiException) {
            throw com.rupio.app.data.auth.AuthException("No se pudo eliminar la cuenta (código ${e.code}). No se borró nada; inténtalo más tarde.")
        }
        SyncEngine.cancel(app)
        restTimer.cancel(); runCatching { com.rupio.app.timer.WorkoutService.stop(app) }
        wipeLocalData()
        app.getSharedPreferences("rupio_owner", Context.MODE_PRIVATE).edit().clear().commit()
        auth.signOut()
    }
}

/** Aviso breve para el snackbar global; [undo] agrega el botón "Deshacer". */
data class Notice(val text: String, val undo: (suspend () -> Unit)? = null)

class RupioApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
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
                        // Una vez por versión: récords consistentes con el historial (p. ej. si borraste entrenos).
                        val prefs = getSharedPreferences("rupio_app", MODE_PRIVATE)
                        // Una vez: re-descarga todo para traer columnas nuevas (planes por serie) y el peso corporal.
                        if (prefs.getInt("pull_reset", 0) < 3) { container.sync.resetPullCursors(); prefs.edit().putInt("pull_reset", 3).apply() }
                        if (prefs.getInt("records_rebuilt", 0) < 2) {
                            runCatching { container.workouts.rebuildAllRecords() }
                            prefs.edit().putInt("records_rebuilt", 2).apply()
                        }
                        if (!s.localOnly) { SyncEngine.schedule(this@RupioApp); SyncEngine.now(this@RupioApp) }
                    }
                }
        }
    }

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        // Canal SILENCIOSO para la notificación en curso (cronómetro y cuenta regresiva).
        nm.createNotificationChannel(NotificationChannel(CHANNEL_WORKOUT, "Entrenamiento activo", NotificationManager.IMPORTANCE_LOW).apply {
            setSound(null, null); enableVibration(false); setShowBadge(false)
            description = "Cronómetro de la sesión y cuenta regresiva del descanso"
        })
        // Canal CON SONIDO para el aviso de fin de descanso (el único sonido de la app).
        nm.createNotificationChannel(NotificationChannel(CHANNEL_REST, "Fin del descanso", NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(com.rupio.app.timer.RestAlert.soundUri(this@RupioApp), AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            enableVibration(true); vibrationPattern = longArrayOf(0, 180, 120, 260)
            setShowBadge(false)
            description = "Suena una vez cuando termina tu descanso entre series"
        })
        // Mismo aviso sin sonido de canal: para "Solo vibración" y "Con el volumen de la música" (ese sonido lo pone la app).
        nm.createNotificationChannel(NotificationChannel(CHANNEL_REST_QUIET, "Fin del descanso (sin sonido)", NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(null, null); enableVibration(true); vibrationPattern = longArrayOf(0, 180, 120, 260)
            setShowBadge(false)
            description = "Vibra cuando termina tu descanso (el sonido lo decide el ajuste de Rupio)"
        })
    }

    companion object {
        const val CHANNEL_WORKOUT = "workout_active"
        const val CHANNEL_REST = "rest_done_v1"
        const val CHANNEL_REST_QUIET = "rest_done_quiet_v1"
    }
}
