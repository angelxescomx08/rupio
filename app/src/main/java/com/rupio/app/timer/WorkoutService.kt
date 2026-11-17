package com.rupio.app.timer

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.SoundPool
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import androidx.annotation.VisibleForTesting
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.rupio.app.MainActivity
import com.rupio.app.R
import com.rupio.app.RupioApp
import com.rupio.app.core.Fmt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Servicio en primer plano mientras hay un entrenamiento activo.
 * - Mantiene el cronómetro de la sesión y la cuenta regresiva del descanso en la notificación.
 * - Con la pantalla apagada, sostiene un wakelock parcial SOLO durante el descanso (para que el aviso llegue a tiempo).
 * - Al terminar el descanso publica UNA notificación con sonido (RestAlert). Es el único lugar que suena,
 *   y solo después de ganar RestTimerController.tryConsumeFinish(generación) → nunca suena doble.
 */
class WorkoutService : LifecycleService() {
    private val container by lazy { (application as RupioApp).container }
    private var wakeLock: PowerManager.WakeLock? = null
    @Volatile private var alertMode = com.rupio.app.data.prefs.RestAlertMode.SOUND

    override fun onCreate() {
        super.onCreate()
        lifecycleScope.launch { container.settings.settings.collect { alertMode = it.restAlert } }
        ServiceCompat.startForeground(this, NOTIF_ID, build("Entrenamiento en curso", "Rupio", null, null),
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0)

        // Notificación en curso: cronómetro de la sesión o cuenta regresiva del descanso (silenciosa).
        lifecycleScope.launch {
            combine(container.workouts.active, container.restTimer.state) { w, r -> w to r }.collect { (w, r) ->
                if (w == null) { stopSelf(); return@collect }
                val n = if (r != null) {
                    val endsWall = System.currentTimeMillis() + r.remainingMs(SystemClock.elapsedRealtime())
                    build("Descanso · ${Fmt.restLabel(r.totalSec)}", r.label, endsWall, countDown = true)
                } else build(w.name, "Entrenamiento en curso", w.startedAt, countDown = false)
                if (canNotify(this@WorkoutService)) NotificationManagerCompat.from(this@WorkoutService).notify(NOTIF_ID, n)
            }
        }
        // Fin de descanso: collectLatest cancela la espera anterior si el temporizador cambia o se cancela.
        lifecycleScope.launch {
            restAlertLoop(container.restTimer, { SystemClock.elapsedRealtime() },
                onWait = { holdWake(it + 15_000) }, onIdle = { releaseWake() },
                onFinish = {
                    // Con la app a la vista no se publica la notificación emergente (tapaba "Terminar" hasta 20 s):
                    // el aviso sale dentro de la pantalla del entreno, con el mismo sonido y vibración.
                    val visible = androidx.lifecycle.ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)
                    container.restTimer.markFinished(it.label)
                    RestAlert.fire(this@WorkoutService, it.label, alertMode, visible)
                })
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_ADD15 -> container.restTimer.adjust(15)
            ACTION_SKIP -> container.restTimer.cancel()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        releaseWake()
        super.onDestroy()
    }

    private fun holdWake(ms: Long) {
        val pm = getSystemService(PowerManager::class.java) ?: return
        val wl = wakeLock ?: pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "rupio:descanso").apply { setReferenceCounted(false) }
        wakeLock = wl
        wl.acquire(ms.coerceIn(1_000, 20 * 60_000))
    }

    private fun releaseWake() { wakeLock?.takeIf { it.isHeld }?.release() }

    private fun build(title: String, text: String, whenMs: Long?, countDown: Boolean?): Notification {
        val b = NotificationCompat.Builder(this, RupioApp.CHANNEL_WORKOUT)
            .setSmallIcon(R.drawable.ic_stat_rupio)
            .setContentTitle(title).setContentText(text)
            .setOngoing(true).setOnlyAlertOnce(true).setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openApp(this))
        if (whenMs != null) {
            b.setWhen(whenMs).setShowWhen(true).setUsesChronometer(true)
            if (countDown == true) {
                b.setChronometerCountDown(true)
                b.addAction(0, "+15 s", action(ACTION_ADD15, 1)).addAction(0, "Saltar", action(ACTION_SKIP, 2))
            }
        }
        return b.build()
    }

    private fun action(a: String, code: Int) = PendingIntent.getService(this, code, Intent(this, WorkoutService::class.java).setAction(a),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    companion object {
        const val NOTIF_ID = 42
        const val ACTION_ADD15 = "rupio.add15"
        const val ACTION_SKIP = "rupio.skip"
        fun start(context: Context) = ContextCompat.startForegroundService(context, Intent(context, WorkoutService::class.java))
        fun stop(context: Context) = context.stopService(Intent(context, WorkoutService::class.java))

        fun openApp(ctx: Context): PendingIntent = PendingIntent.getActivity(ctx, 0,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}

/**
 * Espera el fin de cada descanso y avisa UNA vez. collectLatest cancela la espera si el descanso cambia
 * (±15 s, saltar, marcar otra serie); solo avisa quien gana tryConsumeFinish(generación).
 */
suspend fun restAlertLoop(
    timer: RestTimerController,
    now: () -> Long,
    onWait: (Long) -> Unit = {},
    onIdle: () -> Unit = {},
    onFinish: (RestTimerController.State) -> Unit,
) {
    timer.state.collectLatest { s ->
        if (s == null) { onIdle(); return@collectLatest }
        val left = s.remainingMs(now())
        onWait(left)
        delay(left)
        if (timer.tryConsumeFinish(s.generation)) onFinish(s)
        onIdle()
    }
}

/** ¿La app puede mostrar notificaciones? (permiso en Android 13+ y no bloqueadas por el usuario). */
fun canNotify(ctx: Context): Boolean =
    (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
        NotificationManagerCompat.from(ctx).areNotificationsEnabled()

/**
 * Aviso de fin de descanso. Según el ajuste de la persona (Perfil → Aviso de fin de descanso):
 *  - SOUND: notificación con sonido y vibración (volumen de notificaciones).
 *  - MEDIA: notificación sin sonido + el sonido por el volumen de la música, bajando la música un momento (se oye en audífonos).
 *  - VIBRATE: notificación sin sonido, solo vibra.
 * Con la app a la vista [fire] no publica notificación: suena/vibra y la pantalla del entreno muestra "¡Listo!".
 */
object RestAlert {
    const val NOTIF_ID = 43

    /** Cuántas veces sonó (para las pruebas: debe ser exactamente 1 por descanso). */
    @VisibleForTesting @Volatile var fired = 0

    fun soundUri(ctx: Context): Uri = Uri.parse("android.resource://${ctx.packageName}/${R.raw.rest_done}")

    fun fire(ctx: Context, label: String, mode: com.rupio.app.data.prefs.RestAlertMode, visible: Boolean) {
        fired++
        if (visible) { feedback(ctx, mode); return }
        val channel = if (mode == com.rupio.app.data.prefs.RestAlertMode.SOUND) RupioApp.CHANNEL_REST else RupioApp.CHANNEL_REST_QUIET
        val channelOn = Build.VERSION.SDK_INT < 26 ||
            ctx.getSystemService(NotificationManager::class.java)?.getNotificationChannel(channel)?.importance != NotificationManager.IMPORTANCE_NONE
        if (canNotify(ctx) && channelOn) {
            val n = NotificationCompat.Builder(ctx, channel)
                .setSmallIcon(R.drawable.ic_stat_rupio)
                .setContentTitle("¡Descanso terminado!")
                .setContentText(label)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .apply { if (mode == com.rupio.app.data.prefs.RestAlertMode.SOUND) setSound(soundUri(ctx)) else setSilent(false) }
                .setVibrate(longArrayOf(0, 180, 120, 260))
                .setAutoCancel(true)
                .setTimeoutAfter(20_000)
                .setContentIntent(WorkoutService.openApp(ctx))
                .build()
            NotificationManagerCompat.from(ctx).notify(NOTIF_ID, n)
            if (mode == com.rupio.app.data.prefs.RestAlertMode.MEDIA) RestSound.play(ctx, media = true)
        } else {
            // Notificaciones apagadas: al menos el sonido y la vibración.
            feedback(ctx, mode)
        }
    }

    /** "Probar" en Perfil: exactamente lo que se oirá y sentirá, sin notificación. */
    fun preview(ctx: Context, mode: com.rupio.app.data.prefs.RestAlertMode) = feedback(ctx, mode, force = true)

    private fun feedback(ctx: Context, mode: com.rupio.app.data.prefs.RestAlertMode, force: Boolean = false) {
        val am = ctx.getSystemService(android.media.AudioManager::class.java)
        val ringer = am?.ringerMode ?: android.media.AudioManager.RINGER_MODE_NORMAL
        // Respeta el modo silencio y vibración del teléfono (salvo "Probar", que la persona pidió).
        when (mode) {
            com.rupio.app.data.prefs.RestAlertMode.SOUND -> if (force || ringer == android.media.AudioManager.RINGER_MODE_NORMAL) RestSound.play(ctx, media = false)
            com.rupio.app.data.prefs.RestAlertMode.MEDIA -> RestSound.play(ctx, media = true)
            com.rupio.app.data.prefs.RestAlertMode.VIBRATE -> {}
        }
        if (force || ringer != android.media.AudioManager.RINGER_MODE_SILENT) vibrate(ctx)
    }

    private fun vibrate(ctx: Context) = runCatching {
        val v = if (Build.VERSION.SDK_INT >= 31) ctx.getSystemService(android.os.VibratorManager::class.java)?.defaultVibrator
        else @Suppress("DEPRECATION") ctx.getSystemService(android.os.Vibrator::class.java)
        v?.vibrate(android.os.VibrationEffect.createWaveform(longArrayOf(0, 180, 120, 260), -1))
    }
}

/**
 * Reproduce rest_done.wav una vez. [media] = por el volumen de la música y con foco de audio "transitorio, puede
 * bajar a los demás": la música se baja un momento y el aviso se oye en los audífonos.
 */
object RestSound {
    fun play(ctx: Context, media: Boolean) {
        runCatching {
            val attrs = AudioAttributes.Builder()
                .setUsage(if (media) AudioAttributes.USAGE_MEDIA else AudioAttributes.USAGE_NOTIFICATION_EVENT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            val am = ctx.getSystemService(android.media.AudioManager::class.java)
            val focus = if (media && am != null) android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attrs).build().also { am.requestAudioFocus(it) } else null
            val afd = ctx.resources.openRawResourceFd(R.raw.rest_done)
            android.media.MediaPlayer().apply {
                setAudioAttributes(attrs)
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length); afd.close()
                setOnCompletionListener { mp -> mp.release(); focus?.let { am?.abandonAudioFocusRequest(it) } }
                setOnErrorListener { mp, _, _ -> mp.release(); focus?.let { am?.abandonAudioFocusRequest(it) }; true }
                prepare(); start()
            }
        }
    }
}

/** Guarda el descanso en curso (con hora de reloj de pared) para restaurarlo si Android reinicia la app. */
class RestTimerPrefsStore(context: Context, private val clock: () -> Long) : RestTimerController.Store {
    private val prefs = context.getSharedPreferences("rupio_rest_timer", Context.MODE_PRIVATE)
    override fun save(s: RestTimerController.State?) {
        if (s == null) { prefs.edit().clear().apply(); return }
        prefs.edit().putLong("wall_end", System.currentTimeMillis() + s.remainingMs(clock())).putInt("total", s.totalSec)
            .putString("source", s.sourceSetId).putString("label", s.label).putLong("gen", s.generation).apply()
    }
    override fun load(): RestTimerController.State? {
        val wallEnd = prefs.getLong("wall_end", 0)
        val left = wallEnd - System.currentTimeMillis()
        if (wallEnd == 0L || left <= 0 || left > 60 * 60_000) return null
        return RestTimerController.State(prefs.getLong("gen", 0), clock() + left, prefs.getInt("total", 60), prefs.getString("source", null), prefs.getString("label", "") ?: "")
    }
}
