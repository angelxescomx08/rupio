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
    @Volatile private var alertMode = com.rupio.app.data.prefs.RestAlertMode.MEDIA

    override fun onCreate() {
        super.onCreate()
        lifecycleScope.launch { container.settings.settings.collect { alertMode = it.restAlert } }
        ServiceCompat.startForeground(this, NOTIF_ID, build("Entrenamiento en curso", "Rupio", null, null),
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0)

        // Notificación en curso: cronómetro de la sesión o cuenta regresiva del descanso (silenciosa).
        // v1.9: lo que FALTA es el título ("Quedan 1:24 de descanso") con una barra que se vacía; el total va chico.
        // Antes el título grande era el total ("Descanso · 1 min 30 s") y con +15 s parecía que era lo que quedaba.
        lifecycleScope.launch {
            combine(container.workouts.active, container.restTimer.state) { w, r -> w to r }.collectLatest { (w, r) ->
                if (w == null) { stopSelf(); return@collectLatest }
                if (r == null) {
                    if (canNotify(this@WorkoutService)) NotificationManagerCompat.from(this@WorkoutService)
                        .notify(NOTIF_ID, build(w.name, "Entrenamiento en curso", w.startedAt, countDown = false))
                    return@collectLatest
                }
                while (true) {
                    val left = r.remainingMs(SystemClock.elapsedRealtime())
                    if (canNotify(this@WorkoutService)) NotificationManagerCompat.from(this@WorkoutService).notify(NOTIF_ID, buildRest(r, left))
                    if (left <= 0) break
                    delay((left % 1000).let { if (it == 0L) 1000L else it })     // justo cuando cambia el segundo
                }
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

    /** Descanso en curso: lo que falta como título, la barra que se vacía y el total y "Siguiente…" en chico. */
    private fun buildRest(r: RestTimerController.State, leftMs: Long): Notification {
        val leftSec = ((leftMs + 999) / 1000).toInt()
        val total = r.totalSec.coerceAtLeast(1)
        return NotificationCompat.Builder(this, RupioApp.CHANNEL_WORKOUT)
            .setSmallIcon(R.drawable.ic_stat_rupio)
            .setContentTitle(restTitle(leftSec)).setContentText(r.label).setSubText("de ${Fmt.restLabel(total)}")
            .setProgress(total, leftSec.coerceIn(0, total), false)
            .setOngoing(true).setOnlyAlertOnce(true).setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openApp(this))
            .setShowWhen(false)
            .addAction(0, "+15 s", action(ACTION_ADD15, 1)).addAction(0, "Saltar", action(ACTION_SKIP, 2))
            .build()
    }

    private fun action(a: String, code: Int) = PendingIntent.getService(this, code, Intent(this, WorkoutService::class.java).setAction(a),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    companion object {
        /** "Quedan 1:24 de descanso" (lo que importa con el teléfono bloqueado). */
        fun restTitle(leftSec: Int): String = "Quedan ${Fmt.duration(leftSec.coerceAtLeast(0).toLong())} de descanso"

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
            // La notificación de MEDIA no trae sonido (canal silencioso): el sonido lo pone la app, con la misma regla.
            if (mode == com.rupio.app.data.prefs.RestAlertMode.MEDIA) {
                val d = decision(ctx, mode, force = false)
                if (d.sound) RestSound.play(ctx, media = true)
            }
        } else {
            // Notificaciones apagadas: al menos el sonido y la vibración.
            feedback(ctx, mode)
        }
    }

    /**
     * v1.8: por qué el aviso NO se va a oír con los ajustes de ahora (o null si sí se oye). La pantalla del entreno lo
     * muestra con un botón "Probar sonido": la causa más común del "no suena" es el volumen en 0 o el modo vibración.
     */
    fun inaudibleReason(ctx: Context, mode: com.rupio.app.data.prefs.RestAlertMode): String? = runCatching {
        val am = ctx.getSystemService(android.media.AudioManager::class.java) ?: return null
        when (mode) {
            com.rupio.app.data.prefs.RestAlertMode.MEDIA ->
                if (decision(ctx, mode, force = false).sound && am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC) == 0)
                    "El volumen multimedia está en 0. Súbelo con los botones del teléfono (el aviso usa el volumen de la música)." else null
            com.rupio.app.data.prefs.RestAlertMode.SOUND -> when {
                am.ringerMode != android.media.AudioManager.RINGER_MODE_NORMAL ->
                    "Tu teléfono está en vibración o silencio y el aviso está «Como notificación». En Perfil → Aviso de fin de descanso elige «Con el volumen de la música»."
                am.getStreamVolume(android.media.AudioManager.STREAM_NOTIFICATION) == 0 -> "El volumen de notificaciones está en 0."
                else -> null
            }
            com.rupio.app.data.prefs.RestAlertMode.VIBRATE -> null
        }
    }.getOrNull()

    /** "Probar" en Perfil: exactamente lo que se oirá y sentirá, sin notificación. */
    fun preview(ctx: Context, mode: com.rupio.app.data.prefs.RestAlertMode) = feedback(ctx, mode, force = true)

    private fun decision(ctx: Context, mode: com.rupio.app.data.prefs.RestAlertMode, force: Boolean): RestAlertPolicy.Decision {
        val am = ctx.getSystemService(android.media.AudioManager::class.java)
        val ringer = am?.ringerMode ?: android.media.AudioManager.RINGER_MODE_NORMAL
        val playing = runCatching { am?.isMusicActive == true }.getOrDefault(false)
        return RestAlertPolicy.decide(mode, ringer, RestAlertPolicy.hasPrivateOutput(am), force, mediaPlaying = playing)
    }

    private fun feedback(ctx: Context, mode: com.rupio.app.data.prefs.RestAlertMode, force: Boolean = false) {
        val d = decision(ctx, mode, force)
        if (d.sound) RestSound.play(ctx, media = d.media)
        if (d.vibrate) vibrate(ctx)
    }

    private fun vibrate(ctx: Context) = runCatching {
        val v = if (Build.VERSION.SDK_INT >= 31) ctx.getSystemService(android.os.VibratorManager::class.java)?.defaultVibrator
        else @Suppress("DEPRECATION") ctx.getSystemService(android.os.Vibrator::class.java)
        v?.vibrate(android.os.VibrationEffect.createWaveform(longArrayOf(0, 180, 120, 260), -1))
    }
}

/**
 * Qué hace el aviso de fin de descanso según el modo elegido, el modo del timbre y si hay audífonos o Bluetooth.
 * Respeta el modo silencio y vibración del teléfono (salvo "Probar", que la persona pidió).
 */
object RestAlertPolicy {
    data class Decision(val sound: Boolean, val media: Boolean, val vibrate: Boolean)

    fun decide(mode: com.rupio.app.data.prefs.RestAlertMode, ringer: Int, privateOutput: Boolean, force: Boolean, mediaPlaying: Boolean = false): Decision {
        val normal = ringer == android.media.AudioManager.RINGER_MODE_NORMAL
        val silent = ringer == android.media.AudioManager.RINGER_MODE_SILENT
        val vib = force || !silent
        return when (mode) {
            com.rupio.app.data.prefs.RestAlertMode.SOUND -> Decision(sound = force || normal, media = false, vibrate = vib)
            // v1.7 (N8): en silencio y sin audífonos ni Bluetooth, el sonido saldría por la bocina (en la junta): solo vibra.
            // v1.8: eso aplicaba también al modo VIBRACIÓN, y en el gimnasio casi todos lo traen así: "no suena nunca".
            // Ahora en vibración suena (con el volumen de la música, que la persona controla) y, aun en silencio total,
            // suena si ya hay algo sonando (un video, música): no es la app la que rompe el silencio.
            com.rupio.app.data.prefs.RestAlertMode.MEDIA ->
                if (force || !silent || privateOutput || mediaPlaying) Decision(sound = true, media = true, vibrate = true)
                else Decision(sound = false, media = true, vibrate = true)
            com.rupio.app.data.prefs.RestAlertMode.VIBRATE -> Decision(sound = false, media = false, vibrate = vib)
        }
    }

    /** Tipos de salida "privada": el sonido no sale por la bocina del teléfono. */
    val PRIVATE_TYPES: Set<Int> = buildSet {
        add(android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES); add(android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET)
        add(android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP); add(android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO)
        add(android.media.AudioDeviceInfo.TYPE_USB_HEADSET)
        if (Build.VERSION.SDK_INT >= 28) add(android.media.AudioDeviceInfo.TYPE_HEARING_AID)
        if (Build.VERSION.SDK_INT >= 31) { add(android.media.AudioDeviceInfo.TYPE_BLE_HEADSET); add(android.media.AudioDeviceInfo.TYPE_BLE_SPEAKER) }
        if (Build.VERSION.SDK_INT >= 33) add(android.media.AudioDeviceInfo.TYPE_BLE_BROADCAST)
    }

    /** ¿Hay audífonos o Bluetooth conectados? (AudioManager.getDevices, salidas). */
    fun hasPrivateOutput(am: android.media.AudioManager?): Boolean =
        runCatching { am?.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS)?.any { it.type in PRIVATE_TYPES } == true }.getOrDefault(false)
}

/**
 * Reproduce rest_done.wav una vez. [media] = por el volumen de la música y con foco de audio "transitorio, puede
 * bajar a los demás": la música se baja un momento y el aviso se oye en los audífonos.
 */
object RestSound {
    /**
     * v1.8: referencias fuertes hasta que termine. Antes el MediaPlayer era una variable local: el recolector de basura
     * podía liberarlo a media reproducción (la pantalla del entreno genera mucha basura) y el aviso no se oía.
     */
    private val playing = java.util.Collections.synchronizedSet(mutableSetOf<android.media.MediaPlayer>())

    /** Cuántos reproductores siguen vivos (pruebas). */
    @VisibleForTesting fun active(): Int = playing.size

    fun play(ctx: Context, media: Boolean) {
        runCatching {
            val attrs = AudioAttributes.Builder()
                .setUsage(if (media) AudioAttributes.USAGE_MEDIA else AudioAttributes.USAGE_NOTIFICATION_EVENT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            val am = ctx.getSystemService(android.media.AudioManager::class.java)
            val focus = if (media && am != null) android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attrs).build().also { am.requestAudioFocus(it) } else null
            val afd = ctx.resources.openRawResourceFd(R.raw.rest_done)
            val mp = android.media.MediaPlayer()
            playing += mp
            fun done(p: android.media.MediaPlayer) { playing -= p; runCatching { p.release() }; focus?.let { am?.abandonAudioFocusRequest(it) } }
            try {
                mp.setAudioAttributes(attrs)
                mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length); afd.close()
                mp.setVolume(1f, 1f)
                mp.setOnCompletionListener { done(it) }
                mp.setOnErrorListener { p, _, _ -> done(p); true }
                mp.prepare(); mp.start()
            } catch (e: Exception) { done(mp); throw e }
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
