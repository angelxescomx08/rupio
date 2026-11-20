package com.rupio.app.data.league


import com.rupio.app.core.tr
import android.content.Context
import androidx.compose.runtime.Immutable
import com.rupio.app.data.billing.WallError
import com.rupio.app.data.remote.DataApiClient
import com.rupio.app.domain.LeagueRules
import com.rupio.app.domain.LeagueZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.LocalDate

// ---------- lo que manda el servidor (db/migrations/008_ligas.sql) ----------

@Serializable
internal data class LeagueRowDto(val rank: Int = 0, val name: String = "", val badge: Int = 0, val at: String = "",
                                 val kg: Double = 0.0, val workouts: Int = 0, val me: Boolean = false)

@Serializable
internal data class LeagueDto(val member: Boolean = false, val week: String? = null, val tier: Int? = null, val hidden: Boolean = false,
                              val size: Int = 0, val up: Int = 0, val down: Int = 0, val rows: List<LeagueRowDto>? = null)

/** [k] y [at] van de vuelta tal cual para la página siguiente (keyset): sin redondear ni perder microsegundos. */
@Serializable
internal data class TopRowDto(val name: String = "", val badge: Int = 0, val tier: Int = 1, val kg: Double = 0.0, val k: Double = 0.0, val at: String = "")

@Serializable
internal data class MeGlobalDto(val rank: Long = 0, val total: Long? = null, @SerialName("top_pct") val topPct: Double? = null)

// ---------- lo que usa la pantalla (inmutable: Compose se salta las filas que no cambian) ----------

/** Una persona de tu grupo. [key] = apodo + fecha de inicio (estable entre recargas, como pide el servidor). */
@Immutable
data class LeagueRow(val key: String, val rank: Int, val name: String, val badge: Int, val kg: Double, val workouts: Int, val me: Boolean, val zone: LeagueZone)

@Immutable
data class League(
    val member: Boolean, val week: LocalDate?, val tier: Int, val hidden: Boolean,
    val size: Int, val up: Int, val down: Int, val rows: List<LeagueRow>,
) {
    /** Ya entrenó esta semana: tiene grupo. */
    val inGroup: Boolean get() = rows.isNotEmpty()
    val me: LeagueRow? = rows.firstOrNull { it.me }
    val tierName: String get() = LeagueRules.tierName(tier)
}

@Immutable
data class TopRow(val key: String, val rank: Int, val name: String, val badge: Int, val tier: Int, val kg: Double, val k: Double, val at: String)

@Immutable
data class LeagueGlobalMe(val rank: Long, val total: Long, val topPct: Int)

@Immutable
data class LeagueState(
    val league: League? = null,
    val global: LeagueGlobalMe? = null,
    val top: List<TopRow> = emptyList(),
    val topEnd: Boolean = false,
    val loading: Boolean = false,
    val loadingTop: Boolean = false,
    val loaded: Boolean = false,
    val error: String? = null,
    val topError: String? = null,
    /** Último apodo usado en este teléfono (el servidor no lo devuelve): para volver a participar sin escribirlo. */
    val nickname: String? = null,
)

class LeagueException(message: String) : Exception(message)

/**
 * Ligas semanales (v1.9). Habla con las funciones SQL de db/migrations/008_ligas.sql.
 *
 * Rendimiento (medido en LeaguePerfTest y en el emulador, ver docs/RENDIMIENTO_LIGAS.md):
 *  - La respuesta se decodifica directo a clases @Serializable (2.2× menos memoria que el árbol JsonElement del muro)
 *    y fuera del hilo principal.
 *  - "Tu liga" y "tu lugar global" se piden en paralelo (una ida y vuelta, no dos).
 *  - No se vuelve a pedir si lo último tiene menos de [MAX_AGE_MS] (cambiar de pestaña no gasta red).
 *  - Lo último se guarda por cuenta: la tarjeta de Progreso y la pestaña se ven al instante, aun sin internet.
 *  - El top global va en páginas de [PAGE] con keyset (k + at del último), nunca OFFSET; se quitan repetidos
 *    (si alguien baja de volumen entre páginas, aparecería dos veces y LazyColumn falla con keys repetidas).
 */
class LeagueRepository(
    context: Context,
    private val api: DataApiClient,
    private val baseUrl: suspend () -> String,
    private val jwt: suspend () -> String?,
    private val userId: () -> String?,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val app = context.applicationContext
    private val prefs by lazy { app.getSharedPreferences("rupio_league", Context.MODE_PRIVATE) }
    private val _state = MutableStateFlow(LeagueState())
    val state: StateFlow<LeagueState> = _state
    private val loadLock = Mutex()
    private val topLock = Mutex()
    @Volatile private var fetchedAt = 0L
    @Volatile private var warmFor: String? = null
    private val topKeys = HashSet<String>()

    /** La cuenta cambió: se olvida lo de la cuenta anterior (lo de la nueva se lee de su caché al abrir). */
    fun onAccountChanged() {
        fetchedAt = 0L; warmFor = null
        synchronized(topKeys) { topKeys.clear() }
        _state.value = LeagueState()
    }

    /** Muestra lo guardado de esta cuenta (sin red). Lee el disco fuera del hilo principal. */
    suspend fun warmUp() {
        val uid = userId() ?: return
        if (warmFor == uid) return
        val (raw, nick) = withContext(Dispatchers.IO) { prefs.getString("league_$uid", null) to prefs.getString("nick_$uid", null) }
        val cached = raw?.let { runCatching { withContext(Dispatchers.Default) { parseLeague(it) } }.getOrNull() }
        warmFor = uid
        _state.update { s -> if (s.league != null) s.copy(nickname = s.nickname ?: nick) else s.copy(league = cached, nickname = nick) }
    }

    /** ¿Lo último que se pidió tiene menos de [maxAgeMs]? */
    fun isFresh(maxAgeMs: Long = MAX_AGE_MS): Boolean = _state.value.league != null && clock() - fetchedAt < maxAgeMs

    suspend fun load(force: Boolean = false, maxAgeMs: Long = MAX_AGE_MS) {
        if (!force && isFresh(maxAgeMs)) return
        loadLock.withLock {
            if (!force && isFresh(maxAgeMs)) return            // otra llamada ya lo trajo mientras esperaba
            warmUp()
            val token = jwt() ?: run { _state.update { it.copy(loaded = true, loading = false, error = NO_ACCOUNT) }; return }
            _state.update { it.copy(loading = true, error = null) }
            try {
                val base = baseUrl()
                val known = _state.value.league
                val (league, global) = coroutineScope {
                    val l = async { api.rpcText(base, token, "rupio_league") }
                    // En paralelo solo si es probable que tenga grupo (si no, sería una llamada de más).
                    val g = if (known == null || known.inGroup) async { runCatching { api.rpcText(base, token, "rupio_league_me_global") }.getOrNull() } else null
                    val text = l.await()
                    val parsed = withContext(Dispatchers.Default) { parseLeague(text) }
                    val gText = g?.await() ?: if (parsed.inGroup) runCatching { api.rpcText(base, token, "rupio_league_me_global") }.getOrNull() else null
                    saveCache(text)
                    parsed to gText?.takeIf { parsed.inGroup }?.let(::parseGlobal)
                }
                fetchedAt = clock()
                _state.update { it.copy(league = league, global = global, loading = false, loaded = true, error = null) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, loaded = true, error = errorText(e)) }
            }
        }
    }

    /** Siguiente página del top global ([reset] = desde el primero). */
    suspend fun loadTop(reset: Boolean = false) {
        topLock.withLock {
            val s = _state.value
            if (!reset && s.topEnd) return
            val token = jwt() ?: return
            val last = if (reset) null else s.top.lastOrNull()
            _state.update { it.copy(loadingTop = true, topError = null) }
            try {
                val body = JsonObject(buildMap {
                    if (last != null) { put("after_kg", JsonPrimitive(last.k)); put("after_at", JsonPrimitive(last.at)) }
                    put("page_size", JsonPrimitive(PAGE))
                })
                val text = api.rpcText(baseUrl(), token, "rupio_league_top", body)
                val current = if (reset) emptyList() else _state.value.top
                val (rows, end) = withContext(Dispatchers.Default) { appendTop(current, text, reset) }
                _state.update { it.copy(top = rows, topEnd = end, loadingTop = false) }
            } catch (e: Exception) {
                _state.update { it.copy(loadingTop = false, topError = errorText(e)) }
            }
        }
    }

    /** Entrar (o cambiar el apodo, o volver tras salirse). Lanza [LeagueException]. */
    suspend fun join(nickname: String) {
        val league = mutate("rupio_league_join", JsonObject(mapOf("nickname" to JsonPrimitive(nickname))))
        userId()?.let { uid -> withContext(Dispatchers.IO) { prefs.edit().putString("nick_$uid", nickname).apply() } }
        _state.update { it.copy(nickname = nickname) }
        if (league.inGroup) runCatching { refreshGlobal() }
    }

    /** Salirse (oculta tu apodo y te saca de la semana en curso) o volver. Lanza [LeagueException]. */
    suspend fun setHidden(hidden: Boolean) {
        mutate("rupio_league_set_hidden", JsonObject(mapOf("hidden" to JsonPrimitive(hidden))))
        if (hidden) { synchronized(topKeys) { topKeys.clear() }; _state.update { it.copy(global = null, top = emptyList(), topEnd = false) } }
    }

    private suspend fun mutate(fn: String, body: JsonObject): League {
        val token = jwt() ?: throw LeagueException(NO_ACCOUNT)
        val text = try { api.rpcText(baseUrl(), token, fn, body) } catch (e: Exception) { throw LeagueException(errorText(e)) }
        val league = withContext(Dispatchers.Default) { parseLeague(text) }
        saveCache(text)
        fetchedAt = clock()
        _state.update { it.copy(league = league, loaded = true, error = null) }
        return league
    }

    private suspend fun refreshGlobal() {
        val token = jwt() ?: return
        val g = parseGlobal(api.rpcText(baseUrl(), token, "rupio_league_me_global"))
        _state.update { it.copy(global = g) }
    }

    private suspend fun saveCache(text: String) {
        val uid = userId() ?: return
        withContext(Dispatchers.IO) { prefs.edit().putString("league_$uid", text).apply() }
    }

    internal fun appendTop(current: List<TopRow>, text: String, reset: Boolean): Pair<List<TopRow>, Boolean> {
        val page = json.decodeFromString(topSerializer, text)
        synchronized(topKeys) {
            if (reset) topKeys.clear()
            val out = ArrayList<TopRow>(current.size + page.size); out.addAll(current)
            for (d in page) {
                val key = d.name + "|" + d.at
                if (topKeys.add(key)) out += TopRow(key, out.size + 1, d.name, d.badge, d.tier, d.kg, d.k, d.at)
            }
            // Solo se acaba con una página vacía: el servidor quita a los bloqueados, así que una página corta no es el final.
            return out to page.isEmpty()
        }
    }

    /** Solo pruebas / capturas: poner un estado a mano. */
    internal fun setStateForTest(s: LeagueState) { _state.value = s; fetchedAt = clock() }

    companion object {
        const val PAGE = 50
        val NO_ACCOUNT: String get() = tr("Para las ligas necesitas entrar con tu cuenta de Rupio (y confirmar tu correo).", "For leagues you need to sign in with your Rupio account (and confirm your email).")
        const val MAX_AGE_MS = 30_000L
        internal val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }
        private val topSerializer = ListSerializer(TopRowDto.serializer())

        /** "null" (sin puntaje esta semana) → null. */
        internal fun parseGlobal(text: String): LeagueGlobalMe? {
            if (text.isBlank() || text.trim() == "null") return null
            val d = json.decodeFromString(MeGlobalDto.serializer(), text)
            return LeagueGlobalMe(d.rank, d.total ?: d.rank, (d.topPct ?: 100.0).toInt().coerceIn(1, 100))
        }

        internal fun parseLeague(text: String): League {
            val d = json.decodeFromString(LeagueDto.serializer(), text)
            val rows = d.rows.orEmpty()
            val size = if (d.size > 0) d.size else rows.size
            val seen = HashSet<String>(rows.size * 2)
            return League(
                member = d.member, week = d.week?.let { runCatching { LocalDate.parse(it) }.getOrNull() }, tier = d.tier ?: 1, hidden = d.hidden,
                size = size, up = d.up, down = d.down,
                rows = rows.map { r ->
                    var key = r.name + "|" + r.at
                    if (!seen.add(key)) key += "#" + r.rank                  // nunca keys repetidas en LazyColumn
                    LeagueRow(key, r.rank, r.name, r.badge, r.kg, r.workouts, r.me, LeagueRules.zone(r.rank, size, d.up, d.down))
                },
            )
        }

        fun errorText(e: Throwable): String = when (val w = WallError.from(e)) {
            WallError.NO_ENTRY -> tr("Todavía no participas en las ligas.", "You're not in the leagues yet.")
            WallError.NO_ACCOUNT -> NO_ACCOUNT
            else -> if (e is LeagueException) e.message.orEmpty() else w.userText
        }
    }
}
