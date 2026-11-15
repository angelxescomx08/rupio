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
    val auth = AuthRepository(secure, settings, NeonAuthClient(http))
    private val uid: () -> String? = { auth.currentUserId }
    private val pendingDeletes = PendingDeletes(app)
    val exercises = ExerciseRepository(db, uid)
    val routines = RoutineRepository(db, uid)
    val workouts = WorkoutRepository(db, settings, uid, pendingDeletes)
    val stats = StatsRepository(db, settings)
    val bodyWeight = BodyWeightRepository(db, uid)
    val sync = SyncEngine(app, db, DataApiClient(http), auth, settings, pendingDeletes, onRemoteSetDeletes = { workouts.rebuildRecords(it) })
    val restTimer = RestTimerController { SystemClock.elapsedRealtime() }

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
        prefs.edit().putString("uid", uid).commit()
    }
}

class RupioApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        createChannels()
        container.scope.launch { CatalogSeeder.seedIfNeeded(this@RupioApp, container.db) }
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
    }

    companion object {
        const val CHANNEL_WORKOUT = "workout_active"
        const val CHANNEL_REST = "rest_done_v1"
    }
}
