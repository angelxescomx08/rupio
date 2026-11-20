package com.rupio.app.data.prefs

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.rupio.app.core.WeightUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.dataStore by preferencesDataStore("rupio_settings")

data class Settings(
    val unit: WeightUnit = WeightUnit.LB,
    /** Unidad del PESO CORPORAL, aparte de la de las pesas: casi todos se pesan en kilos aunque carguen en libras. */
    val bodyUnit: WeightUnit = WeightUnit.KG,
    val defaultRestSec: Int = 90,
    val weekStart: Int = 1,
    val onboardingDone: Boolean = false,
    val tutorialHome: Boolean = false,
    val tutorialWorkout: Boolean = false,
    /** Pantalla encendida mientras entrenas (se puede apagar en Perfil). */
    val keepScreenOn: Boolean = true,
    /** Cómo avisa el fin del descanso. v1.8: por defecto con el volumen de la música (se oye en vibración y sobre un video). */
    val restAlert: RestAlertMode = RestAlertMode.MEDIA,
    /** Ya se explicó y pidió el permiso de notificaciones (no se vuelve a interrumpir al empezar cada entreno). */
    val notifAsked: Boolean = false,
    /**
     * Consentimiento expreso para guardar el peso corporal (dato de salud, LFPDPPP art. 8): cuándo lo dio, o null.
     * Sin él, al registrar el primer peso se pide con una casilla.
     */
    val healthConsentAt: Long? = null,
    /** El consentimiento cambió en este teléfono y falta avisarle al servidor (columna profiles.health_consent_at). */
    val healthConsentDirty: Boolean = false,
    /** Sube con cada cambio del consentimiento (para no marcar como enviado un cambio hecho a media sincronización). */
    val healthConsentRev: Int = 0,
    /** Se retiró el consentimiento y falta borrar el peso corporal de la cuenta (aunque luego se vuelva a dar). */
    val healthPurgePending: Boolean = false,
    val authUrl: String = "",
    val dataApiUrl: String = "",
)

/** Cómo suena el fin del descanso. */
enum class RestAlertMode(val code: String) {
    MEDIA("media"), SOUND("sound"), VIBRATE("vibrate");

    val label: String get() = when (this) {
        MEDIA -> com.rupio.app.core.tr("Con el volumen de la música (recomendado)", "With the music volume (recommended)")
        SOUND -> com.rupio.app.core.tr("Como notificación", "As a notification")
        VIBRATE -> com.rupio.app.core.tr("Solo vibración", "Vibrate only")
    }
    val detail: String get() = when (this) {
        MEDIA -> com.rupio.app.core.tr("Se oye aunque el teléfono esté en vibración, por la bocina o tus audífonos, y baja un momento la música o el video. En silencio total, solo vibra (salvo con audífonos o si algo está sonando)",
            "You'll hear it even with your phone on vibrate, through the speaker or your headphones, and it briefly lowers your music or video. On full silent it only vibrates (unless you have headphones or something is playing)")
        SOUND -> com.rupio.app.core.tr("Usa el volumen de notificaciones: en vibración o silencio no suena", "Uses the notification volume: on vibrate or silent it won't play")
        VIBRATE -> com.rupio.app.core.tr("Sin sonido", "No sound")
    }

    /**
     * v1.8: si nunca elegiste, ahora es MEDIA. Antes era SOUND, que en vibración no suena: así llegó el reporte de que
     * "no suena la notificación ni dentro ni fuera de la app, ni viendo un video".
     */
    companion object { fun of(code: String?) = entries.firstOrNull { it.code == code } ?: MEDIA }
}

/** Preferencias del usuario (unidad lb/kg, descanso por defecto, inicio de semana, tutorial). */
class SettingsStore(private val context: Context, private val defaultAuth: String, private val defaultData: String) {
    private object K {
        val unit = stringPreferencesKey("unit")
        val bodyUnit = stringPreferencesKey("body_unit")
        val rest = intPreferencesKey("default_rest")
        val week = intPreferencesKey("week_start")
        val onboarding = booleanPreferencesKey("onboarding_done")
        val tHome = booleanPreferencesKey("tutorial_home")
        val tWorkout = booleanPreferencesKey("tutorial_workout")
        val screenOn = booleanPreferencesKey("keep_screen_on")
        val restAlert = stringPreferencesKey("rest_alert")
        val notifAsked = booleanPreferencesKey("notif_asked")
        val consentAt = longPreferencesKey("health_consent_at")
        val consentDirty = booleanPreferencesKey("health_consent_dirty")
        val consentRev = intPreferencesKey("health_consent_rev")
        val purge = booleanPreferencesKey("health_purge_pending")
        val authUrl = stringPreferencesKey("auth_url")
        val dataUrl = stringPreferencesKey("data_api_url")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p -> p.toSettings() }

    private fun Preferences.toSettings() = Settings(
        unit = WeightUnit.of(this[K.unit]),
        bodyUnit = this[K.bodyUnit]?.let { WeightUnit.of(it) } ?: WeightUnit.KG,
        defaultRestSec = this[K.rest] ?: 90,
        weekStart = this[K.week] ?: 1,
        onboardingDone = this[K.onboarding] ?: false,
        tutorialHome = this[K.tHome] ?: false,
        tutorialWorkout = this[K.tWorkout] ?: false,
        keepScreenOn = this[K.screenOn] ?: true,
        restAlert = RestAlertMode.of(this[K.restAlert]),
        notifAsked = this[K.notifAsked] ?: false,
        healthConsentAt = this[K.consentAt],
        healthConsentDirty = this[K.consentDirty] ?: false,
        healthConsentRev = this[K.consentRev] ?: 0,
        healthPurgePending = this[K.purge] ?: false,
        authUrl = (this[K.authUrl] ?: defaultAuth).trim().trimEnd('/'),
        dataApiUrl = (this[K.dataUrl] ?: defaultData).trim().trimEnd('/'),
    )

    suspend fun current(): Settings = settings.first()
    suspend fun setUnit(u: WeightUnit) = context.dataStore.edit { it[K.unit] = u.code }
    suspend fun setBodyUnit(u: WeightUnit) = context.dataStore.edit { it[K.bodyUnit] = u.code }
    suspend fun setDefaultRest(sec: Int) = context.dataStore.edit { it[K.rest] = sec }
    suspend fun setWeekStart(iso: Int) = context.dataStore.edit { it[K.week] = iso }
    suspend fun setOnboardingDone(v: Boolean) = context.dataStore.edit { it[K.onboarding] = v }
    suspend fun setTutorialHome(v: Boolean) = context.dataStore.edit { it[K.tHome] = v }
    suspend fun setTutorialWorkout(v: Boolean) = context.dataStore.edit { it[K.tWorkout] = v }
    suspend fun setKeepScreenOn(v: Boolean) = context.dataStore.edit { it[K.screenOn] = v }
    suspend fun setRestAlert(m: RestAlertMode) = context.dataStore.edit { it[K.restAlert] = m.code }
    suspend fun setNotifAsked(v: Boolean) = context.dataStore.edit { it[K.notifAsked] = v }
    /** La persona marcó la casilla (o la retiró en Perfil, con null). Queda pendiente de avisar al servidor. */
    suspend fun setHealthConsent(at: Long?) = context.dataStore.edit {
        if (at == null) { it.remove(K.consentAt); it[K.purge] = true } else it[K.consentAt] = at
        it[K.consentDirty] = true; it[K.consentRev] = (it[K.consentRev] ?: 0) + 1
    }
    /**
     * Al pasar los datos del modo sin cuenta a una cuenta: un consentimiento dado en este teléfono se sube; si no hay,
     * manda lo que diga la cuenta. Un retiro hecho sin cuenta nunca borra el peso de la cuenta.
     */
    suspend fun adoptHealthConsent() = context.dataStore.edit {
        it.remove(K.purge)
        if (it[K.consentAt] == null) it.remove(K.consentDirty) else { it[K.consentDirty] = true; it[K.consentRev] = (it[K.consentRev] ?: 0) + 1 }
    }
    /** La cuenta ya no tiene peso corporal (se borró tras retirar el consentimiento). */
    suspend fun markHealthPurged() = context.dataStore.edit { it.remove(K.purge) }
    /** Lo que dice la cuenta (otro teléfono pudo darlo o retirarlo). Nunca pisa un cambio local pendiente. */
    suspend fun setHealthConsentFromServer(at: Long?) = context.dataStore.edit {
        if (it[K.consentDirty] == true) return@edit
        if (at == null) it.remove(K.consentAt) else it[K.consentAt] = at
    }
    /** El servidor ya tiene el cambio [rev]; si hubo otro cambio mientras tanto, sigue pendiente. */
    suspend fun markHealthConsentSynced(rev: Int) = context.dataStore.edit { if ((it[K.consentRev] ?: 0) == rev) it[K.consentDirty] = false }
    /** Al borrar los datos del teléfono (otra cuenta, cuenta eliminada): el consentimiento era de esa cuenta. */
    suspend fun clearHealthConsent() = context.dataStore.edit { it.remove(K.consentAt); it.remove(K.consentDirty); it.remove(K.consentRev); it.remove(K.purge) }
    suspend fun setServer(auth: String, data: String) = context.dataStore.edit { it[K.authUrl] = auth.trim(); it[K.dataUrl] = data.trim() }
    /** Repite solo el recorrido guiado de botones (sin la bienvenida). */
    suspend fun resetCoach() = context.dataStore.edit { it[K.tHome] = false; it[K.tWorkout] = false }
    suspend fun resetTutorial() = context.dataStore.edit { it[K.onboarding] = false; it[K.tHome] = false; it[K.tWorkout] = false }
}

/**
 * Almacén cifrado con una llave AES-256-GCM del Android Keystore (no extraíble del dispositivo).
 * Guarda la cookie de sesión de Neon Auth y el JWT. NUNCA la contraseña (OWASP MASWE-0036): si la sesión del
 * servidor vence, la app pide entrar de nuevo (AuthState.SignedIn.sessionExpired).
 *
 * La llave se abre una sola vez y los valores descifrados se guardan en memoria: antes cada lectura volvía a abrir
 * el Keystore y al arrancar se hacían 6 lecturas en el hilo principal.
 */
class SecureStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("rupio_secure", Context.MODE_PRIVATE)
    private val alias = "rupio_master_key"
    /**
     * Si el Keystore falla (pasa en algunos teléfonos), NUNCA se guarda en texto plano:
     * los valores viven solo en memoria durante esta sesión de la app.
     */
    private val memory = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val keystoreOk: Boolean by lazy { runCatching { key(); true }.getOrDefault(false) }
    /** Valores ya descifrados (o null si no existen), para no volver a descifrar en cada lectura. */
    private val cache = java.util.concurrent.ConcurrentHashMap<String, java.util.Optional<String>>()
    @Volatile private var cachedKey: SecretKey? = null

    private fun key(): SecretKey = cachedKey ?: synchronized(this) { cachedKey ?: openKey().also { cachedKey = it } }

    private fun openKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    fun put(name: String, value: String?) {
        if (value == null) { memory.remove(name); cache[name] = java.util.Optional.empty(); prefs.edit().remove(name).apply(); return }
        cache[name] = java.util.Optional.of(value)
        if (!keystoreOk) { memory[name] = value; return }
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val enc = c.doFinal(value.toByteArray(Charsets.UTF_8))
        val packed = Base64.encodeToString(c.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(enc, Base64.NO_WRAP)
        prefs.edit().putString(name, packed).apply()
    }

    fun get(name: String): String? = memory[name] ?: cache[name]?.orElse(null) ?: if (cache[name] != null) null else decrypt(name).also { cache[name] = java.util.Optional.ofNullable(it) }

    private fun decrypt(name: String): String? = runCatching {
        if (!keystoreOk) return null
        val packed = prefs.getString(name, null) ?: return null
        val (iv, data) = packed.split(":", limit = 2)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
        String(c.doFinal(Base64.decode(data, Base64.NO_WRAP)), Charsets.UTF_8)
    }.getOrNull()

    fun clear() { memory.clear(); cache.clear(); prefs.edit().clear().apply() }
}
