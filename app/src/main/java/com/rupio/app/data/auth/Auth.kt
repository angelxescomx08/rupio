package com.rupio.app.data.auth

import android.util.Base64
import com.rupio.app.core.Ids
import com.rupio.app.data.prefs.SecureStore
import com.rupio.app.data.prefs.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    data class SignedIn(val userId: String, val email: String, val name: String, val localOnly: Boolean) : AuthState
}

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
 * Sesión persistente "de toda la vida":
 *  1. La cookie de sesión se guarda cifrada (Keystore) y se renueva sola al pedir get-session.
 *  2. El JWT (15 min) se pide cuando hace falta y se cachea.
 *  3. Si la sesión del servidor expirara (p. ej. meses sin abrir la app), se re-autentica en
 *     silencio con las credenciales cifradas — solo si "Mantener sesión siempre" está activo.
 *  4. Sin internet NUNCA se cierra la sesión: la app sigue funcionando y sincroniza después.
 * Solo se sale con "Cerrar sesión".
 */
class AuthRepository(
    private val secure: SecureStore,
    private val settings: SettingsStore,
    private val client: NeonAuthClient,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val _state = MutableStateFlow<AuthState>(AuthState.Loading)
    val state: StateFlow<AuthState> = _state.asStateFlow()
    private val mutex = Mutex()

    private var jwt: String? = null
    private var jwtExp: Long = 0

    init {
        val uid = secure.get("user_id")
        _state.value = if (uid != null) AuthState.SignedIn(uid, secure.get("email") ?: "", secure.get("name") ?: "", secure.get("local_only") == "1")
        else AuthState.SignedOut
        jwt = secure.get("jwt"); jwtExp = secure.get("jwt_exp")?.toLongOrNull() ?: 0
    }

    val currentUserId: String? get() = (state.value as? AuthState.SignedIn)?.userId
    val isLocalOnly: Boolean get() = (state.value as? AuthState.SignedIn)?.localOnly == true

    suspend fun serverConfigured(): Boolean = settings.current().authUrl.isNotBlank()

    suspend fun signIn(email: String, password: String) = authenticate("/sign-in/email", buildJsonObject {
        put("email", email.trim()); put("password", password); put("rememberMe", true)
    }, email, password, null)

    suspend fun signUp(name: String, email: String, password: String) = authenticate("/sign-up/email", buildJsonObject {
        put("name", name.trim()); put("email", email.trim()); put("password", password)
    }, email, password, name)

    private suspend fun authenticate(path: String, body: JsonObject, email: String, password: String, name: String?) {
        val base = settings.current().authUrl
        if (base.isBlank()) throw AuthException("No se pudo conectar con el servidor. Inténtalo más tarde.")
        val r = try { client.post(base, path, body) } catch (e: IOException) { throw AuthException("Sin conexión. Revisa tu internet e inténtalo de nuevo.") }
        if (r.code !in 200..299) throw AuthException(errorMessage(r))
        val user = r.body?.get("user")?.jsonObject ?: throw AuthException("Respuesta inesperada del servidor.")
        val uid = user["id"]?.jsonPrimitive?.content ?: throw AuthException("Respuesta inesperada del servidor.")
        val displayName = user["name"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: name ?: email.substringBefore('@')
        saveCookies(r.cookies, merge = false)
        secure.put("user_id", uid); secure.put("email", email.trim()); secure.put("name", displayName); secure.put("local_only", "0")
        if (settings.current().keepSignedIn) { secure.put("cred_email", email.trim()); secure.put("cred_pass", password) }
        r.jwt?.let { storeJwt(it) }
        _state.value = AuthState.SignedIn(uid, email.trim(), displayName, localOnly = false)
        runCatching { validJwt() }
    }

    /** Modo sin cuenta: todo se guarda solo en este teléfono. */
    fun useLocalMode(name: String) {
        val uid = secure.get("local_uid") ?: Ids.uuidV7().also { secure.put("local_uid", it) }
        secure.put("user_id", uid); secure.put("email", ""); secure.put("name", name.trim().ifBlank { "Atleta" }); secure.put("local_only", "1")
        _state.value = AuthState.SignedIn(uid, "", name.trim().ifBlank { "Atleta" }, localOnly = true)
    }

    fun updateName(name: String) {
        val s = state.value as? AuthState.SignedIn ?: return
        secure.put("name", name); _state.value = s.copy(name = name)
    }

    /** JWT vigente para la Data API, o null si no se puede obtener ahora (sin red, etc.). */
    suspend fun validJwt(): String? = mutex.withLock {
        if (isLocalOnly || currentUserId == null) return null
        val now = System.currentTimeMillis() / 1000
        jwt?.let { if (jwtExp - now > 60) return it }
        val base = settings.current().authUrl.ifBlank { return null }
        val cookies = loadCookies()
        try {
            if (cookies.isNotEmpty()) {
                val r = client.get(base, "/get-session", cookies)
                saveCookies(r.cookies, merge = true)
                val hasSession = r.code == 200 && r.body?.get("session") != null
                var token = r.jwt
                if (hasSession && token == null) {
                    val t = client.get(base, "/token", loadCookies())
                    token = t.body?.get("token")?.jsonPrimitive?.content
                }
                if (hasSession && token != null) { storeJwt(token); return token }
            }
            // Sesión del servidor vencida → re-login silencioso (si el usuario lo permitió).
            val e = secure.get("cred_email"); val p = secure.get("cred_pass")
            if (e != null && p != null) {
                val r = client.post(base, "/sign-in/email", buildJsonObject { put("email", e); put("password", p); put("rememberMe", true) })
                if (r.code in 200..299) {
                    saveCookies(r.cookies, merge = false)
                    val token = r.jwt ?: client.get(base, "/get-session", loadCookies()).also { saveCookies(it.cookies, true) }.jwt
                    if (token != null) { storeJwt(token); return token }
                }
            }
        } catch (_: IOException) { /* sin red: se reintenta en la siguiente sincronización */ }
        null
    }

    suspend fun signOut() {
        val base = settings.current().authUrl
        if (!isLocalOnly && base.isNotBlank()) runCatching { client.post(base, "/sign-out", JsonObject(emptyMap()), loadCookies()) }
        val localUid = secure.get("local_uid")
        secure.clear()
        localUid?.let { secure.put("local_uid", it) }
        jwt = null; jwtExp = 0
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
            code.contains("INVALID_EMAIL_OR_PASSWORD") || r.code == 401 -> "Correo o contraseña incorrectos."
            code.contains("USER_ALREADY_EXISTS") || r.code == 422 -> "Ya existe una cuenta con ese correo. Inicia sesión."
            code.contains("PASSWORD_TOO_SHORT") -> "La contraseña debe tener al menos 8 caracteres."
            code.contains("PASSWORD_TOO_LONG") -> "La contraseña es demasiado larga."
            code.contains("INVALID_EMAIL") -> "Ese correo no parece válido."
            code.contains("EMAIL_NOT_VERIFIED") -> "Confirma tu correo con el enlace que te enviamos y vuelve a intentar."
            r.code == 403 -> "No se pudo conectar con el servidor. Inténtalo más tarde."
            r.code == 429 -> "Demasiados intentos. Espera un momento."
            else -> "No se pudo completar (${r.code}). Inténtalo de nuevo."
        }
    }
}
