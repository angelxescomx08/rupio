package com.rupio.app.data.auth

import android.util.Base64
import com.rupio.app.core.Ids
import com.rupio.app.data.prefs.SecureStore
import com.rupio.app.data.prefs.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URI

sealed interface AuthState {
    data object Loading : AuthState
    data object SignedOut : AuthState
    /**
     * [sessionExpired]: la sesión del servidor venció (p. ej. semanas sin abrir la app). La app sigue funcionando y
     * guardando en el teléfono, pero no respalda nada hasta que la persona vuelva a escribir su contraseña.
     */
    data class SignedIn(val userId: String, val email: String, val name: String, val localOnly: Boolean, val sessionExpired: Boolean = false) : AuthState
}

/** Por qué no se pudo obtener un JWT: cada causa se explica distinto al usuario (ver SyncProblem). */
enum class JwtFailure { NONE, OFFLINE, EXPIRED, SERVER }

class AuthException(message: String) : Exception(message)

/**
 * Cliente REST de Neon Auth (Better Auth administrado por Neon).
 *  POST {auth}/sign-up/email · POST {auth}/sign-in/email → cookie de sesión (opaca, httpOnly)
 *  GET  {auth}/get-session   → JWT en el header `set-auth-jwt` (dura ~15 min)
 *  GET  {auth}/token         → { token } (respaldo si no viene el header)
 * El JWT es el que entiende la Data API (RLS con auth.uid()).
 */
class NeonAuthClient(private val http: OkHttpClient) {
    private val json = Json { ignoreUnknownKeys = true }
    private val media = "application/json; charset=utf-8".toMediaType()

    data class Result(val code: Int, val body: JsonObject?, val cookies: Map<String, String>, val jwt: String?)

    private fun origin(base: String) = runCatching { URI(base).let { "${it.scheme}://${it.host}" } }.getOrDefault(base)

    private suspend fun call(req: Request): Result = withContext(Dispatchers.IO) {
        http.newCall(req).execute().use { r ->
            val text = r.body?.string().orEmpty()
            val obj = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
            val cookies = r.headers("Set-Cookie").mapNotNull { c ->
                val kv = c.substringBefore(';'); val i = kv.indexOf('=')
                if (i <= 0) null else kv.substring(0, i).trim() to kv.substring(i + 1).trim()
            }.toMap()
            Result(r.code, obj, cookies, r.header("set-auth-jwt"))
        }
    }

    suspend fun post(base: String, path: String, body: JsonObject, cookies: Map<String, String> = emptyMap()): Result =
        call(Request.Builder().url("$base$path").post(body.toString().toRequestBody(media))
            .header("Origin", origin(base)).header("Accept", "application/json")
            .apply { if (cookies.isNotEmpty()) header("Cookie", cookieHeader(cookies)) }.build())

    suspend fun get(base: String, path: String, cookies: Map<String, String>): Result =
        call(Request.Builder().url("$base$path").get().header("Origin", origin(base)).header("Accept", "application/json")
            .header("Cookie", cookieHeader(cookies)).build())

    private fun cookieHeader(c: Map<String, String>) = c.entries.joinToString("; ") { "${it.key}=${it.value}" }
}

/**
 * Sesión persistente:
 *  1. La cookie de sesión (pedida con rememberMe) se guarda cifrada (Keystore) y se renueva sola al pedir get-session.
 *  2. El JWT (15 min) se pide cuando hace falta y se cachea.
 *  3. La contraseña NUNCA se guarda (OWASP MASWE-0036). Si la sesión del servidor vence, la cuenta queda en
 *     "sesión vencida": todo sigue guardándose en el teléfono y la app pide entrar de nuevo para respaldar.
 *  4. Sin internet NUNCA se cierra la sesión: la app sigue funcionando y sincroniza después.
 */
class AuthRepository(
    private val secure: SecureStore,
    private val settings: SettingsStore,
    private val client: NeonAuthClient,
    /** Si se da, la sesión guardada se lee en segundo plano (no retrasa el arranque en el hilo principal). */
    scope: CoroutineScope? = null,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val _state = MutableStateFlow<AuthState>(AuthState.Loading)
    val state: StateFlow<AuthState> = _state.asStateFlow()
    private val mutex = Mutex()

    private var jwt: String? = null
    private var jwtExp: Long = 0

    /** Última razón por la que [validJwt] devolvió null. */
    @Volatile var lastFailure: JwtFailure = JwtFailure.NONE
        private set

    init {
        if (scope == null) load() else scope.launch(Dispatchers.IO) { load() }
    }

    private fun load() {
        // Hasta la v1.5 se guardaban correo y contraseña para volver a entrar solos. Ya no: se borran.
        secure.put("cred_email", null); secure.put("cred_pass", null)
        val uid = secure.get("user_id")
        val loaded = if (uid != null) AuthState.SignedIn(uid, secure.get("email") ?: "", secure.get("name") ?: "", secure.get("local_only") == "1",
            sessionExpired = secure.get("session_expired") == "1")
        else AuthState.SignedOut
        // Si mientras tanto alguien ya inició sesión o entró sin cuenta, no se pisa.
        if (_state.compareAndSet(AuthState.Loading, loaded) && jwt == null) {
            jwt = secure.get("jwt"); jwtExp = secure.get("jwt_exp")?.toLongOrNull() ?: 0
        }
    }

    val currentUserId: String? get() = (state.value as? AuthState.SignedIn)?.userId
    val isLocalOnly: Boolean get() = (state.value as? AuthState.SignedIn)?.localOnly == true

    suspend fun serverConfigured(): Boolean = settings.current().authUrl.isNotBlank()

    /**
     * [adopt]: se llama con el id de la cuenta ANTES de cambiar de usuario. Desde el modo sin cuenta pasa los datos
     * del teléfono a la cuenta nueva (si no, claimLocalData los borraría por ser de "otro dueño").
     */
    suspend fun signIn(email: String, password: String, adopt: (suspend (String) -> Unit)? = null) = authenticate("/sign-in/email", buildJsonObject {
        put("email", email.trim()); put("password", password); put("rememberMe", true)
    }, email, null, adopt)

    suspend fun signUp(name: String, email: String, password: String, adopt: (suspend (String) -> Unit)? = null) = authenticate("/sign-up/email", buildJsonObject {
        put("name", name.trim()); put("email", email.trim()); put("password", password)
    }, email, name, adopt)

    private suspend fun authenticate(path: String, body: JsonObject, email: String, name: String?, adopt: (suspend (String) -> Unit)?) {
        val r = postAuth(path, body)
        val user = r.body?.get("user")?.jsonObject ?: throw AuthException("Respuesta inesperada del servidor (código R1).")
        val uid = user["id"]?.jsonPrimitive?.content ?: throw AuthException("Respuesta inesperada del servidor (código R2).")
        val displayName = user["name"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: name ?: email.substringBefore('@')
        adopt?.invoke(uid)
        saveCookies(r.cookies, merge = false)
        secure.put("user_id", uid); secure.put("email", email.trim()); secure.put("name", displayName); secure.put("local_only", "0")
        secure.put("session_expired", null)
        jwt = null; jwtExp = 0; secure.put("jwt", null)
        r.jwt?.let { storeJwt(it) }
        lastFailure = JwtFailure.NONE
        _state.value = AuthState.SignedIn(uid, email.trim(), displayName, localOnly = false)
        runCatching { validJwt() }
    }

    /**
     * Sesión vencida → la persona vuelve a escribir su contraseña. Se queda en la MISMA cuenta: nada del teléfono se
     * borra y lo pendiente se sube en la siguiente sincronización. También sirve para confirmar la identidad
     * antes de una acción delicada (eliminar la cuenta).
     */
    suspend fun reauthenticate(password: String) {
        val s = state.value as? AuthState.SignedIn ?: throw AuthException("No hay una cuenta abierta.")
        if (s.localOnly) throw AuthException("Estás usando la app sin cuenta.")
        val r = postAuth("/sign-in/email", buildJsonObject { put("email", s.email); put("password", password); put("rememberMe", true) })
        val uid = r.body?.get("user")?.jsonObject?.get("id")?.jsonPrimitive?.content ?: throw AuthException("Respuesta inesperada del servidor (código R2).")
        if (uid != s.userId) throw AuthException("Esa no es la cuenta de este teléfono.")
        saveCookies(r.cookies, merge = false)
        secure.put("session_expired", null)
        r.jwt?.let { storeJwt(it) }
        lastFailure = JwtFailure.NONE
        _state.value = s.copy(sessionExpired = false)
        runCatching { validJwt() }
    }

    /**
     * "¿Olvidaste tu contraseña?": Neon Auth manda un código de 6 dígitos al correo. La respuesta es la misma exista o
     * no la cuenta (no revela qué correos están registrados).
     */
    suspend fun requestPasswordReset(email: String) {
        postAuth("/email-otp/request-password-reset", buildJsonObject { put("email", email.trim()) })
    }

    /** Cambia la contraseña con el código del correo. Después hay que iniciar sesión con la nueva. */
    suspend fun resetPassword(email: String, code: String, newPassword: String) {
        postAuth("/email-otp/reset-password", buildJsonObject { put("email", email.trim()); put("otp", code.trim()); put("password", newPassword) })
    }

    private suspend fun postAuth(path: String, body: JsonObject): NeonAuthClient.Result {
        val base = settings.current().authUrl
        if (base.isBlank()) throw AuthException("No se pudo conectar con el servidor. Inténtalo más tarde.")
        val r = try { client.post(base, path, body) } catch (e: IOException) { throw AuthException("Sin conexión. Revisa tu internet e inténtalo de nuevo.") }
        if (r.code !in 200..299) throw AuthException(errorMessage(r))
        return r
    }

    /**
     * Modo sin cuenta: todo se guarda solo en este teléfono.
     * [restoredOwner]: dueño de los datos que vinieron en una copia de Google (teléfono nuevo). Si era un usuario sin
     * cuenta, se reutiliza su id para no borrar los entrenos restaurados.
     */
    fun useLocalMode(name: String, restoredOwner: String? = null) {
        val uid = secure.get("local_uid") ?: (restoredOwner ?: Ids.uuidV7()).also { secure.put("local_uid", it) }
        secure.put("user_id", uid); secure.put("email", ""); secure.put("name", name.trim().ifBlank { "Atleta" }); secure.put("local_only", "1")
        _state.value = AuthState.SignedIn(uid, "", name.trim().ifBlank { "Atleta" }, localOnly = true)
    }

    fun updateName(name: String) {
        val s = state.value as? AuthState.SignedIn ?: return
        secure.put("name", name); _state.value = s.copy(name = name)
    }

    /**
     * JWT vigente para la Data API, o null si no se puede obtener ahora; [lastFailure] dice por qué.
     * [force]: ignora el JWT guardado (el servidor respondió 401 a media sincronización).
     */
    suspend fun validJwt(force: Boolean = false): String? = mutex.withLock {
        val s = state.value as? AuthState.SignedIn ?: return null
        if (s.localOnly) return null
        if (s.sessionExpired) { lastFailure = JwtFailure.EXPIRED; return null }
        val now = System.currentTimeMillis() / 1000
        if (!force) jwt?.let { if (jwtExp - now > 60) { lastFailure = JwtFailure.NONE; return it } }
        val base = settings.current().authUrl.ifBlank { lastFailure = JwtFailure.SERVER; return null }
        val cookies = loadCookies()
        if (cookies.isEmpty()) { markExpired(); return null }
        try {
            val r = client.get(base, "/get-session", cookies)
            saveCookies(r.cookies, merge = true)
            if (r.code == 200 && r.body?.get("session") != null) {
                val token = r.jwt ?: client.get(base, "/token", loadCookies()).body?.get("token")?.jsonPrimitive?.content
                if (token != null) { storeJwt(token); lastFailure = JwtFailure.NONE; return token }
                lastFailure = JwtFailure.SERVER; return null
            }
            // El servidor respondió y dice que ya no hay sesión (200 con "null" o 401): hay que volver a entrar.
            if (r.code == 200 || r.code == 401) { markExpired(); return null }
            lastFailure = JwtFailure.SERVER
        } catch (_: IOException) { lastFailure = JwtFailure.OFFLINE }
        null
    }

    private fun markExpired() {
        lastFailure = JwtFailure.EXPIRED
        jwt = null; jwtExp = 0; secure.put("jwt", null)
        secure.put("session_expired", "1")
        (state.value as? AuthState.SignedIn)?.let { if (!it.sessionExpired) _state.value = it.copy(sessionExpired = true) }
    }

    /**
     * [background]: si se da, el aviso al servidor ("cierra esta sesión") va en segundo plano y no se espera.
     * Antes se esperaba y, con mala señal, "Cerrar sesión" se quedaba hasta 45 s sin decir nada.
     */
    suspend fun signOut(background: kotlinx.coroutines.CoroutineScope? = null) {
        val base = settings.current().authUrl
        if (!isLocalOnly && base.isNotBlank()) {
            val cookies = loadCookies()
            if (background != null) background.launch { runCatching { client.post(base, "/sign-out", JsonObject(emptyMap()), cookies) } }
            else runCatching { client.post(base, "/sign-out", JsonObject(emptyMap()), cookies) }
        }
        val localUid = secure.get("local_uid")
        secure.clear()
        localUid?.let { secure.put("local_uid", it) }
        jwt = null; jwtExp = 0; lastFailure = JwtFailure.NONE
        _state.value = AuthState.SignedOut
    }

    private fun storeJwt(token: String) {
        jwt = token
        jwtExp = decodeExp(token)
        secure.put("jwt", token); secure.put("jwt_exp", jwtExp.toString())
    }

    private fun decodeExp(token: String): Long = runCatching {
        val payload = String(Base64.decode(token.split(".")[1], Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP))
        json.parseToJsonElement(payload).jsonObject["exp"]?.jsonPrimitive?.longOrNull ?: 0L
    }.getOrDefault(0L)

    private fun loadCookies(): Map<String, String> = runCatching {
        json.parseToJsonElement(secure.get("cookies") ?: "{}").jsonObject.mapValues { it.value.jsonPrimitive.content }
    }.getOrDefault(emptyMap())

    private fun saveCookies(new: Map<String, String>, merge: Boolean) {
        if (new.isEmpty() && merge) return
        val all = (if (merge) loadCookies() else emptyMap()) + new
        val clean = all.filterValues { it.isNotEmpty() }
        secure.put("cookies", JsonObject(clean.mapValues { kotlinx.serialization.json.JsonPrimitive(it.value) }).toString())
    }

    private fun errorMessage(r: NeonAuthClient.Result): String {
        val code = r.body?.get("code")?.jsonPrimitive?.content.orEmpty()
        return when {
            code.contains("INVALID_OTP") -> "El código no es correcto. Revisa el correo o pide otro."
            code.contains("OTP_EXPIRED") -> "El código ya venció. Pide uno nuevo."
            code.contains("TOO_MANY_ATTEMPTS") -> "Demasiados intentos con ese código. Pide uno nuevo."
            code.contains("INVALID_EMAIL_OR_PASSWORD") || r.code == 401 -> "Correo o contraseña incorrectos."
            code.contains("USER_ALREADY_EXISTS") || r.code == 422 -> "Ya existe una cuenta con ese correo. Inicia sesión."
            code.contains("PASSWORD_TOO_SHORT") -> "La contraseña debe tener al menos 8 caracteres."
            code.contains("PASSWORD_TOO_LONG") -> "La contraseña es demasiado larga."
            code.contains("INVALID_EMAIL") -> "Ese correo no parece válido."
            code.contains("EMAIL_NOT_VERIFIED") -> "Confirma tu correo con el enlace que te enviamos y vuelve a intentar."
            r.code == 403 -> "No se pudo conectar con el servidor. Inténtalo más tarde."
            r.code == 429 -> "Demasiados intentos. Espera un momento."
            r.code >= 500 -> "El servidor no responde (código ${r.code}). Inténtalo en unos minutos."
            else -> "No se pudo completar (código ${r.code}). Inténtalo de nuevo."
        }
    }
}
