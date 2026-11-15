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
    private var fallback: FallbackBeep? = null

    override fun onCreate() {
        super.onCreate()
        fallback = FallbackBeep(this)
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
                onFinish = { RestAlert.fire(this@WorkoutService, it.label, fallback) })
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
        releaseWake(); fallback?.release(); fallback = null
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

/** Aviso de fin de descanso: una notificación con sonido y vibración (o un beep si las notificaciones están apagadas). */
object RestAlert {
    const val NOTIF_ID = 43

    /** Cuántas veces sonó (para las pruebas: debe ser exactamente 1 por descanso). */
    @VisibleForTesting @Volatile var fired = 0

    fun soundUri(ctx: Context): Uri = Uri.parse("android.resource://${ctx.packageName}/${R.raw.rest_done}")

    fun fire(ctx: Context, label: String, fallback: FallbackBeep?) {
        fired++
        val channelOn = Build.VERSION.SDK_INT < 26 ||
            ctx.getSystemService(NotificationManager::class.java)?.getNotificationChannel(RupioApp.CHANNEL_REST)?.importance != NotificationManager.IMPORTANCE_NONE
        if (canNotify(ctx) && channelOn) {
            val n = NotificationCompat.Builder(ctx, RupioApp.CHANNEL_REST)
                .setSmallIcon(R.drawable.ic_stat_rupio)
                .setContentTitle("¡Descanso terminado!")
                .setContentText(label)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setSound(soundUri(ctx))                       // Android 7 y anteriores (en 8+ lo pone el canal)
                .setVibrate(longArrayOf(0, 180, 120, 260))
                .setAutoCancel(true)
                .setTimeoutAfter(20_000)
                .setContentIntent(WorkoutService.openApp(ctx))
                .build()
            NotificationManagerCompat.from(ctx).notify(NOTIF_ID, n)
        } else {
            fallback?.play()
        }
    }
}

/** Solo si las notificaciones están apagadas: el mismo sonido por el volumen de notificaciones. */
class FallbackBeep(context: Context) {
    private val pool = SoundPool.Builder().setMaxStreams(1).setAudioAttributes(
        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
    ).build()
    private val soundId = pool.load(context, R.raw.rest_done, 1)
    fun play() { pool.play(soundId, 1f, 1f, 1, 0, 1f) }
    fun release() = pool.release()
}
