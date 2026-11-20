package com.rupio.app.data.auth


import com.rupio.app.core.tr
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
import kotlinx.serialization.json.booleanOrNull
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
     * [needsVerification]: falta escribir el código que llegó al correo; la cuenta no está lista (no se sincroniza).
     * [newAccount]: la cuenta se acaba de crear en este teléfono (si el correo estaba mal, se puede descartar).
     */
    data class SignedIn(val userId: String, val email: String, val name: String, val localOnly: Boolean, val sessionExpired: Boolean = false,
                        val needsVerification: Boolean = false, val newAccount: Boolean = false) : AuthState
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
            sessionExpired = secure.get("session_expired") == "1", needsVerification = secure.get("verify_pending") == "1", newAccount = secure.get("verify_new") == "1")
        else AuthState.SignedOut
        // Si mientras tanto alguien ya inició sesión o entró sin cuenta, no se pisa.
        if (_state.compareAndSet(AuthState.Loading, loaded) && jwt == null) {
            jwt = secure.get("jwt"); jwtExp = secure.get("jwt_exp")?.toLongOrNull() ?: 0
        }
    }

    val currentUserId: String? get() = (state.value as? AuthState.SignedIn)?.userId
    val isLocalOnly: Boolean get() = (state.value as? AuthState.SignedIn)?.localOnly == true
    val needsVerification: Boolean get() = (state.value as? AuthState.SignedIn)?.needsVerification == true

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
        val user = r.body?.get("user")?.jsonObject ?: throw AuthException(tr("Respuesta inesperada del servidor (código R1).", "Unexpected server response (code R1)."))
        val uid = user["id"]?.jsonPrimitive?.content ?: throw AuthException(tr("Respuesta inesperada del servidor (código R2).", "Unexpected server response (code R2)."))
        val displayName = user["name"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: name ?: email.substringBefore('@')
        // v1.7 (N6): la cuenta no está lista hasta confirmar el correo con el código de 6 dígitos.
        val verified = (user["emailVerified"] as? kotlinx.serialization.json.JsonPrimitive)?.booleanOrNull ?: true
        val isNew = !verified && path == "/sign-up/email"
        adopt?.invoke(uid)
        saveCookies(r.cookies, merge = false)
        secure.put("user_id", uid); secure.put("email", email.trim()); secure.put("name", displayName); secure.put("local_only", "0")
        secure.put("session_expired", null)
        secure.put("verify_pending", if (verified) null else "1"); secure.put("verify_new", if (isNew) "1" else null)
        jwt = null; jwtExp = 0; secure.put("jwt", null)
        r.jwt?.let { storeJwt(it) }
        lastFailure = JwtFailure.NONE
        _state.value = AuthState.SignedIn(uid, email.trim(), displayName, localOnly = false, needsVerification = !verified, newAccount = isNew)
        runCatching { validJwt() }
        if (!verified) runCatching { sendVerificationCode() }
    }

    /** Manda (otra vez) el código de 6 dígitos al correo de la cuenta. */
    suspend fun sendVerificationCode() {
        val s = state.value as? AuthState.SignedIn ?: throw AuthException(tr("No hay una cuenta abierta.", "No account is signed in."))
        postAuth("/email-otp/send-verification-otp", buildJsonObject { put("email", s.email); put("type", "email-verification") })
    }

    /** Confirma el correo con el código. Desde aquí la cuenta queda lista y empieza a respaldarse. */
    suspend fun verifyEmail(code: String) {
        val s = state.value as? AuthState.SignedIn ?: throw AuthException(tr("No hay una cuenta abierta.", "No account is signed in."))
        val r = postAuth("/email-otp/verify-email", buildJsonObject { put("email", s.email); put("otp", code.filter { it.isDigit() }) })
        saveCookies(r.cookies, merge = true)
        r.jwt?.let { storeJwt(it) }
        secure.put("verify_pending", null); secure.put("verify_new", null)
        _state.value = (state.value as? AuthState.SignedIn ?: s).copy(needsVerification = false, newAccount = false)
    }

    /**
     * Sesión vencida → la persona vuelve a escribir su contraseña. Se queda en la MISMA cuenta: nada del teléfono se
     * borra y lo pendiente se sube en la siguiente sincronización. También sirve para confirmar la identidad
     * antes de una acción delicada (eliminar la cuenta).
     */
    suspend fun reauthenticate(password: String) {
        val s = state.value as? AuthState.SignedIn ?: throw AuthException(tr("No hay una cuenta abierta.", "No account is signed in."))
        if (s.localOnly) throw AuthException(tr("Estás usando la app sin cuenta.", "You're using the app without an account."))
        val r = postAuth("/sign-in/email", buildJsonObject { put("email", s.email); put("password", password); put("rememberMe", true) })
        val uid = r.body?.get("user")?.jsonObject?.get("id")?.jsonPrimitive?.content ?: throw AuthException(tr("Respuesta inesperada del servidor (código R2).", "Unexpected server response (code R2)."))
        if (uid != s.userId) throw AuthException(tr("Esa no es la cuenta de este teléfono.", "That isn't the account on this phone."))
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
        if (base.isBlank()) throw AuthException(tr("No se pudo conectar con el servidor. Inténtalo más tarde.", "Couldn't connect to the server. Try again later."))
        val r = try { client.post(base, path, body) } catch (e: IOException) { throw AuthException(tr("Sin conexión. Revisa tu internet e inténtalo de nuevo.", "No connection. Check your internet and try again.")) }
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
        secure.put("user_id", uid); secure.put("email", ""); secure.put("name", name.trim().ifBlank { tr("Atleta", "Athlete") }); secure.put("local_only", "1")
        _state.value = AuthState.SignedIn(uid, "", name.trim().ifBlank { tr("Atleta", "Athlete") }, localOnly = true)
    }

    /** El siguiente "modo sin cuenta" usa este id (para regresar a él sin perder los datos del teléfono). */
    fun keepLocalId(uid: String) = secure.put("local_uid", uid)

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
            code.contains("INVALID_OTP") -> tr("El código no es correcto. Revisa el correo o pide otro.", "That code isn't right. Check the email or request another one.")
            code.contains("OTP_EXPIRED") -> tr("El código ya venció. Pide uno nuevo.", "The code expired. Request a new one.")
            code.contains("TOO_MANY_ATTEMPTS") -> tr("Demasiados intentos con ese código. Pide uno nuevo.", "Too many attempts with that code. Request a new one.")
            code.contains("INVALID_EMAIL_OR_PASSWORD") || r.code == 401 -> tr("Correo o contraseña incorrectos.", "Wrong email or password.")
            code.contains("USER_ALREADY_EXISTS") || r.code == 422 -> tr("Ya existe una cuenta con ese correo. Inicia sesión.", "There's already an account with that email. Sign in.")
            code.contains("PASSWORD_TOO_SHORT") -> tr("La contraseña debe tener al menos 8 caracteres.", "The password must be at least 8 characters.")
            code.contains("PASSWORD_TOO_LONG") -> tr("La contraseña es demasiado larga.", "The password is too long.")
            code.contains("INVALID_EMAIL") -> tr("Ese correo no parece válido.", "That email doesn't look valid.")
            code.contains("EMAIL_NOT_VERIFIED") -> tr("Confirma tu correo con el código que te enviamos y vuelve a intentar.", "Confirm your email with the code we sent you and try again.")
            code.contains("USER_NOT_FOUND") -> tr("No encontramos esa cuenta.", "We couldn't find that account.")
            r.code == 403 -> tr("No se pudo conectar con el servidor. Inténtalo más tarde.", "Couldn't connect to the server. Try again later.")
            r.code == 429 -> tr("Demasiados intentos. Espera un momento.", "Too many attempts. Wait a moment.")
            r.code >= 500 -> tr("El servidor no responde (código ${r.code}). Inténtalo en unos minutos.", "The server isn't responding (code ${r.code}). Try again in a few minutes.")
            else -> tr("No se pudo completar (código ${r.code}). Inténtalo de nuevo.", "It couldn't be completed (code ${r.code}). Please try again.")
        }
    }
}
