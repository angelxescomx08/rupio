package com.rupio.app.data.billing

import android.content.Context
import com.rupio.app.data.remote.DataApiClient
import com.rupio.app.data.remote.DataApiException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.security.MessageDigest

/** Una persona en el muro. Sin puntos ni ids: el servidor no los manda (solo lugar, nombre y desde cuándo). */
data class WallEntry(val rank: Int, val name: String, val sinceMs: Long, val me: Boolean)

/** Tu lugar. [toNext] = puntos que te faltan para pasar al de arriba (null si vas primero u ocultaste tu nombre). */
data class WallMe(
    val name: String, val points: Int, val rank: Int?, val toNext: Int?, val hidden: Boolean, val blocked: Boolean,
    val sinceMs: Long, val total: Int,
) {
    val isPatron: Boolean get() = points > 0
}

data class WallState(
    val entries: List<WallEntry> = emptyList(),
    val me: WallMe? = null,
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val error: String? = null,
    /** v2.1: cuántas personas hay en el muro (el servidor manda los primeros 300). Null si no se sabe. */
    val total: Int? = null,
)

/** Por qué el servidor no aceptó algo. [userText] es lo que ve la persona. */
enum class WallError(val code: String, private val es: String, private val en: String) {
    NOT_CONFIGURED("rupio:not_configured", "Tu aporte quedó guardado. Aparecerás en el muro en cuanto el servidor lo confirme.",
        "Your contribution was saved. You'll appear on the wall as soon as the server confirms it."),
    BAD_SIGNATURE("rupio:bad_signature", "Google Play no confirmó esta compra. Si se te cobró, escríbenos y lo revisamos.",
        "Google Play didn't confirm this purchase. If you were charged, write to us and we'll look into it."),
    BAD_PURCHASE("rupio:bad_purchase", "No se pudo leer la compra. Si se te cobró, escríbenos y lo revisamos.",
        "The purchase couldn't be read. If you were charged, write to us and we'll look into it."),
    NOT_COMPLETED("rupio:not_completed", "El pago sigue pendiente. Cuando se complete, aparecerás en el muro.",
        "The payment is still pending. Once it completes, you'll appear on the wall."),
    OTHER_ACCOUNT("rupio:other_account", "Esta compra se hizo con otra cuenta de Rupio. Entra con esa cuenta para registrarla.",
        "This purchase was made with another Rupio account. Sign in with that account to register it."),
    TOKEN_USED("rupio:token_used", "Esta compra ya está registrada en otra cuenta.", "This purchase is already registered to another account."),
    UNKNOWN_PRODUCT("rupio:unknown_product", "Ese producto no es del muro.", "That product isn't part of the wall."),
    NAME_REQUIRED("rupio:name_required", "Escribe cómo quieres aparecer en el muro.", "Enter how you want to appear on the wall."),
    NAME_LENGTH("rupio:name_length", "El nombre debe tener de 2 a 30 caracteres.", "The name must be 2 to 30 characters."),
    NAME_INVALID("rupio:name_invalid", "El nombre no puede llevar enlaces ni correos.", "The name can't include links or emails."),
    NO_ENTRY("rupio:no_entry", "Todavía no estás en el muro.", "You're not on the wall yet."),
    NO_ACCOUNT("", "Para el muro necesitas entrar con tu cuenta de Rupio (y confirmar tu correo).", "For the wall you need to sign in with your Rupio account (and confirm your email)."),
    OFFLINE("", "Sin conexión. Revisa tu internet e inténtalo de nuevo.", "No connection. Check your internet and try again."),
    SERVER("", "El servidor no respondió. Inténtalo en un rato.", "The server didn't respond. Try again in a bit.");

    /** Lo que ve la persona, en el idioma de la app. */
    val userText: String get() = com.rupio.app.core.tr(es, en)

    /** ¿Vale la pena reintentar más tarde con la misma compra? */
    val retryLater: Boolean get() = this == NOT_CONFIGURED || this == OFFLINE || this == SERVER || this == NO_ACCOUNT || this == NOT_COMPLETED

    companion object {
        fun from(e: Throwable): WallError = when (e) {
            is WallException -> e.error
            is IOException -> OFFLINE
            is DataApiException -> entries.firstOrNull { it.code.isNotEmpty() && e.message.orEmpty().contains(it.code) }
                ?: if (e.code == 401 || e.code == 403) NO_ACCOUNT else SERVER
            else -> SERVER
        }
    }
}

class WallException(val error: WallError) : Exception(error.userText)

/**
 * Muro de agradecimientos (v1.8). Habla con las funciones SQL de db/migrations/007_supporters_wall.sql.
 *
 *  - Tu lugar y los aportes, solo con cuenta. v2.1: la lista (lugar, nombre y fecha) se ve también sin cuenta con un
 *    JWT anónimo de Neon Auth y la función rupio_wall_public (db/migrations/012_muro_publico.sql).
 *  - Tu lugar se guarda en el teléfono (por cuenta) para que el nivel Mecenas siga activo sin internet.
 *  - El servidor verifica la firma de Google Play: el teléfono solo manda el JSON de la compra y su firma.
 */
class WallRepository(
    context: Context,
    private val api: DataApiClient,
    private val baseUrl: suspend () -> String,
    /** JWT de la persona, o null si no hay cuenta lista (sin cuenta, sesión vencida o correo sin confirmar). */
    private val jwt: suspend () -> String?,
    private val userId: () -> String?,
    /** v2.1: JWT de rol anonymous para el muro sin cuenta (null sin internet). */
    private val anonJwt: suspend () -> String? = { null },
) {
    private val prefs = context.getSharedPreferences("rupio_wall", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(initial(userId()))
    val state: StateFlow<WallState> = _state
    /** Robolectric (pruebas) no debe llamar al servidor real. */
    private val networkAllowed = !android.os.Build.FINGERPRINT.orEmpty().contains("robolectric", ignoreCase = true)
    @Volatile private var publicAt = prefs.getLong("public_at", 0L)
    /** Una sola carga pública a la vez (la pantalla la pide desde la tarjeta y la pestaña casi juntas). */
    private val publicMutex = kotlinx.coroutines.sync.Mutex()

    /** ¿Tiene el nivel Mecenas? (lo dice el servidor; sin internet, lo último que dijo). */
    val isPatron: Boolean get() = _state.value.me?.isPatron == true

    /** La cuenta cambió (entrar, salir, otra cuenta): se muestra lo guardado de esa cuenta. */
    fun onAccountChanged() {
        _state.value = initial(userId())
    }

    /** Con cuenta: tu lugar guardado. Sin cuenta: la última lista pública guardada (se ve al instante y sin internet). */
    private fun initial(uid: String?): WallState = if (uid != null) WallState(me = cachedMe(uid)) else cachedPublic()

    private fun cachedPublic(): WallState {
        val raw = prefs.getString("public_wall", null) ?: return WallState()
        return runCatching {
            val o = api.json.parseToJsonElement(raw).jsonObject
            val entries = parseEntries(o["entries"])
            WallState(entries, null, loaded = entries.isNotEmpty(), total = o.int("total") ?: entries.size)
        }.getOrDefault(WallState())
    }

    /**
     * v2.1: el muro sin cuenta (o con la cuenta sin confirmar). Lugar, nombre y fecha de los primeros 300, sin "tú" ni
     * puntos. No se vuelve a pedir si lo último tiene menos de 30 min, salvo [force] (abrir la pestaña Muro).
     */
    suspend fun loadPublic(force: Boolean = false) {
        if (userId() != null || !networkAllowed) return
        // Si ya hay una carga pública en curso, se espera a que termine (no se sale dejando "Sin conexión"); y si lo
        // último es reciente y no es forzado, no se vuelve a pedir.
        if (!force && publicMutex.isLocked) return
        publicMutex.withLock { loadPublicLocked(force) }
    }

    private suspend fun loadPublicLocked(force: Boolean) {
        if (userId() != null) return
        val s = _state.value
        if (!force && s.entries.isNotEmpty() && System.currentTimeMillis() - publicAt < PUBLIC_TTL_MS) return
        _state.update { it.copy(loading = true, error = null) }
        try {
            // El primer token anónimo a veces tarda tras abrir la app: un reintento corto antes de rendirse.
            val token = anonJwt() ?: run { kotlinx.coroutines.delay(600); anonJwt() } ?: throw IOException("sin token anónimo")
            val r = api.rpcJson(baseUrl(), token, "rupio_wall_public").jsonObject
            val entries = parseEntries(r["entries"])
            if (userId() != null) return                     // entró a su cuenta mientras tanto: manda load()
            publicAt = System.currentTimeMillis()
            prefs.edit().putString("public_wall", r.toString()).putLong("public_at", publicAt).apply()
            _state.value = WallState(entries, null, loading = false, loaded = true, total = r.int("total") ?: entries.size)
        } catch (e: Exception) {
            // Si ya había una lista guardada, se queda a la vista sin error.
            _state.update { it.copy(loading = false, loaded = true, error = if (it.entries.isEmpty()) WallError.from(e).userText else null) }
        }
    }

    suspend fun load() {
        val token = jwt() ?: run { _state.update { it.copy(loaded = true, error = WallError.NO_ACCOUNT.userText) }; return }
        _state.update { it.copy(loading = true, error = null) }
        try {
            val r = api.rpcJson(baseUrl(), token, "rupio_wall").jsonObject
            val entries = parseEntries(r["entries"])
            val me = parseMe(r["me"])
            saveMe(me)
            _state.value = WallState(entries, me, loading = false, loaded = true, total = me?.total)
        } catch (e: Exception) {
            _state.update { it.copy(loading = false, loaded = true, error = WallError.from(e).userText) }
        }
    }

    /** Registra una compra del muro. Devuelve tu lugar actualizado. Lanza [WallException]. */
    suspend fun register(purchaseJson: String, signature: String, displayName: String?): WallMe {
        val token = jwt() ?: throw WallException(WallError.NO_ACCOUNT)
        val body = JsonObject(mapOf(
            "purchase_json" to JsonPrimitive(purchaseJson),
            "signature" to JsonPrimitive(signature),
            "display_name" to (displayName?.let { JsonPrimitive(it) } ?: JsonNull),
        ))
        val me = call(token, "rupio_wall_register", body) ?: throw WallException(WallError.SERVER)
        return me
    }

    suspend fun setName(name: String): WallMe? = call(jwt() ?: throw WallException(WallError.NO_ACCOUNT), "rupio_wall_set_name",
        JsonObject(mapOf("display_name" to JsonPrimitive(name))))

    suspend fun setHidden(hidden: Boolean): WallMe? = call(jwt() ?: throw WallException(WallError.NO_ACCOUNT), "rupio_wall_set_hidden",
        JsonObject(mapOf("hidden" to JsonPrimitive(hidden))))

    private suspend fun call(token: String, fn: String, body: JsonObject): WallMe? {
        val r = try { api.rpcJson(baseUrl(), token, fn, body) } catch (e: Exception) { throw WallException(WallError.from(e)) }
        val me = parseMe(r)
        saveMe(me)
        _state.update { it.copy(me = me) }
        return me
    }

    // ---------- caché por cuenta ----------

    private fun key(uid: String) = "me_$uid"

    private fun cachedMe(uid: String?): WallMe? {
        uid ?: return null
        val raw = prefs.getString(key(uid), null) ?: return null
        return runCatching { parseMe(api.json.parseToJsonElement(raw)) }.getOrNull()
    }

    private fun saveMe(me: WallMe?) {
        val uid = userId() ?: return
        if (me == null) { prefs.edit().remove(key(uid)).apply(); return }
        val o = JsonObject(mapOf(
            "name" to JsonPrimitive(me.name), "points" to JsonPrimitive(me.points), "rank" to (me.rank?.let(::JsonPrimitive) ?: JsonNull),
            "to_next" to (me.toNext?.let(::JsonPrimitive) ?: JsonNull), "hidden" to JsonPrimitive(me.hidden), "blocked" to JsonPrimitive(me.blocked),
            "since_ms" to JsonPrimitive(me.sinceMs), "total" to JsonPrimitive(me.total),
        ))
        prefs.edit().putString(key(uid), o.toString()).apply()
    }

    /** Solo pruebas / versión debug: poner un estado a mano. */
    internal fun setStateForTest(s: WallState) { _state.value = s }

    companion object {
        /** Lo que va en obfuscatedAccountId: sha256 del id de la cuenta (el servidor hace la misma cuenta). */
        fun accountHash(userId: String): String =
            MessageDigest.getInstance("SHA-256").digest(userId.lowercase().toByteArray()).joinToString("") { "%02x".format(it) }

        fun parseMe(e: JsonElement?): WallMe? {
            if (e == null || e is JsonNull) return null
            val o = e.jsonObject
            return WallMe(
                name = o.str("name").orEmpty(), points = o.int("points") ?: 0, rank = o.int("rank"), toNext = o.int("to_next"),
                hidden = o.bool("hidden") == true, blocked = o.bool("blocked") == true,
                sinceMs = o["since_ms"]?.jsonPrimitive?.content?.toLongOrNull() ?: parseTime(o.str("since")), total = o.int("total") ?: 0,
            )
        }

        /** Filas del muro (las dos funciones mandan lo mismo; la pública no trae "me"). */
        fun parseEntries(e: JsonElement?): List<WallEntry> = e?.takeIf { it !is JsonNull }?.jsonArray?.map { x ->
            val o = x.jsonObject
            WallEntry(o.int("rank") ?: 0, o.str("name").orEmpty(), parseTime(o.str("since")), o.bool("me") == true)
        }.orEmpty()

        /** Lo público se vuelve a pedir cada 30 min como mucho (el muro cambia poco). */
        const val PUBLIC_TTL_MS = 30 * 60_000L

        fun parseTime(s: String?): Long = s?.let { runCatching { java.time.OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() } ?: 0L

        private fun JsonObject.str(k: String) = this[k]?.takeIf { it !is JsonNull }?.jsonPrimitive?.content
        private fun JsonObject.int(k: String) = this[k]?.takeIf { it !is JsonNull }?.jsonPrimitive?.intOrNull
        private fun JsonObject.bool(k: String) = this[k]?.takeIf { it !is JsonNull }?.jsonPrimitive?.booleanOrNull
    }
}
