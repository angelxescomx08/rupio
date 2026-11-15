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
    val defaultRestSec: Int = 90,
    val weekStart: Int = 1,
    val onboardingDone: Boolean = false,
    val tutorialHome: Boolean = false,
    val tutorialWorkout: Boolean = false,
    val keepSignedIn: Boolean = true,
    val authUrl: String = "",
    val dataApiUrl: String = "",
)

/** Preferencias del usuario (unidad lb/kg, descanso por defecto, inicio de semana, tutorial). */
class SettingsStore(private val context: Context, private val defaultAuth: String, private val defaultData: String) {
    private object K {
        val unit = stringPreferencesKey("unit")
        val rest = intPreferencesKey("default_rest")
        val week = intPreferencesKey("week_start")
        val onboarding = booleanPreferencesKey("onboarding_done")
        val tHome = booleanPreferencesKey("tutorial_home")
        val tWorkout = booleanPreferencesKey("tutorial_workout")
        val keep = booleanPreferencesKey("keep_signed_in")
        val authUrl = stringPreferencesKey("auth_url")
        val dataUrl = stringPreferencesKey("data_api_url")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p -> p.toSettings() }

    private fun Preferences.toSettings() = Settings(
        unit = WeightUnit.of(this[K.unit]),
        defaultRestSec = this[K.rest] ?: 90,
        weekStart = this[K.week] ?: 1,
        onboardingDone = this[K.onboarding] ?: false,
        tutorialHome = this[K.tHome] ?: false,
        tutorialWorkout = this[K.tWorkout] ?: false,
        keepSignedIn = this[K.keep] ?: true,
        authUrl = (this[K.authUrl] ?: defaultAuth).trim().trimEnd('/'),
        dataApiUrl = (this[K.dataUrl] ?: defaultData).trim().trimEnd('/'),
    )

    suspend fun current(): Settings = settings.first()
    suspend fun setUnit(u: WeightUnit) = context.dataStore.edit { it[K.unit] = u.code }
    suspend fun setDefaultRest(sec: Int) = context.dataStore.edit { it[K.rest] = sec }
    suspend fun setWeekStart(iso: Int) = context.dataStore.edit { it[K.week] = iso }
    suspend fun setOnboardingDone(v: Boolean) = context.dataStore.edit { it[K.onboarding] = v }
    suspend fun setTutorialHome(v: Boolean) = context.dataStore.edit { it[K.tHome] = v }
    suspend fun setTutorialWorkout(v: Boolean) = context.dataStore.edit { it[K.tWorkout] = v }
    suspend fun setKeepSignedIn(v: Boolean) = context.dataStore.edit { it[K.keep] = v }
    suspend fun setServer(auth: String, data: String) = context.dataStore.edit { it[K.authUrl] = auth.trim(); it[K.dataUrl] = data.trim() }
    /** Repite solo el recorrido guiado de botones (sin la bienvenida). */
    suspend fun resetCoach() = context.dataStore.edit { it[K.tHome] = false; it[K.tWorkout] = false }
    suspend fun resetTutorial() = context.dataStore.edit { it[K.onboarding] = false; it[K.tHome] = false; it[K.tWorkout] = false }
}

/**
 * Almacén cifrado con una llave AES-256-GCM del Android Keystore (no extraíble del dispositivo).
 * Guarda la cookie de sesión de Neon Auth, el JWT y —si el usuario lo permite— las credenciales
 * para re-autenticar en silencio: así la sesión dura "toda la vida" hasta que cierres sesión.
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

    private fun key(): SecretKey {
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
        if (value == null) { memory.remove(name); prefs.edit().remove(name).apply(); return }
        if (!keystoreOk) { memory[name] = value; return }
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val enc = c.doFinal(value.toByteArray(Charsets.UTF_8))
        val packed = Base64.encodeToString(c.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(enc, Base64.NO_WRAP)
        prefs.edit().putString(name, packed).apply()
    }

    fun get(name: String): String? = memory[name] ?: runCatching {
        if (!keystoreOk) return null
        val packed = prefs.getString(name, null) ?: return null
        val (iv, data) = packed.split(":", limit = 2)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
        String(c.doFinal(Base64.decode(data, Base64.NO_WRAP)), Charsets.UTF_8)
    }.getOrNull()

    fun clear() { memory.clear(); prefs.edit().clear().apply() }
}
