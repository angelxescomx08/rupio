package com.rupio.app.data.remote

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.rupio.app.RupioApp
import com.rupio.app.core.searchKey
import com.rupio.app.data.auth.AuthRepository
import com.rupio.app.domain.Tracking
import com.rupio.app.data.local.*
import com.rupio.app.data.prefs.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

// ------------------------------------------------------------------ DTOs (columnas del servidor)
private fun Long.iso() = Instant.ofEpochMilli(this).toString()
private fun Int.day() = LocalDate.ofEpochDay(this.toLong()).toString()
private fun String.ms() = OffsetDateTime.parse(this).toInstant().toEpochMilli()
private fun String.epochDay() = LocalDate.parse(this).toEpochDay().toInt()
private fun String?.planJson(): JsonElement? = this?.let { runCatching { Json.parseToJsonElement(it) }.getOrNull() }
private fun JsonElement?.planText(): String? = this?.takeIf { it !is JsonNull }?.toString()

@Serializable data class ExerciseDto(
    val id: String, @SerialName("owner_id") val ownerId: String?, val name: String,
    @SerialName("primary_muscle") val primaryMuscle: String, @SerialName("secondary_muscles") val secondaryMuscles: List<String>,
    val equipment: String, val tracking: String, @SerialName("is_lower_body") val isLowerBody: Boolean, val deleted: Boolean,
)
@Serializable data class RoutineDto(
    val id: String, @SerialName("user_id") val userId: String, val name: String, val notes: String?, val position: Int,
    @SerialName("created_at") val createdAt: String, val deleted: Boolean,
)
@Serializable data class RoutineExerciseDto(
    val id: String, @SerialName("routine_id") val routineId: String, @SerialName("user_id") val userId: String,
    @SerialName("exercise_id") val exerciseId: String, val position: Int, @SerialName("superset_id") val supersetId: String?,
    @SerialName("superset_rest_mode") val supersetRestMode: Int, @SerialName("superset_rest_sec") val supersetRestSec: Int?,
    @SerialName("rest_sec") val restSec: Int, @SerialName("target_sets") val targetSets: Int,
    @SerialName("rep_min") val repMin: Int, @SerialName("rep_max") val repMax: Int, val notes: String?, val deleted: Boolean,
    @SerialName("set_plan") val setPlan: JsonElement? = null,
)
@Serializable data class WorkoutDto(
    val id: String, @SerialName("user_id") val userId: String, @SerialName("routine_id") val routineId: String?, val name: String,
    @SerialName("started_at") val startedAt: String, @SerialName("ended_at") val endedAt: String?, @SerialName("time_zone") val timeZone: String,
    @SerialName("local_date") val localDate: String, @SerialName("duration_sec") val durationSec: Int?,
    @SerialName("total_volume_kg") val totalVolumeKg: Double, @SerialName("total_sets") val totalSets: Int,
    @SerialName("total_reps") val totalReps: Int, val notes: String?, val deleted: Boolean,
)
@Serializable data class WorkoutExerciseDto(
    val id: String, @SerialName("workout_id") val workoutId: String, @SerialName("user_id") val userId: String,
    @SerialName("exercise_id") val exerciseId: String, val position: Int, @SerialName("superset_id") val supersetId: String?,
    @SerialName("superset_rest_mode") val supersetRestMode: Int, @SerialName("superset_rest_sec") val supersetRestSec: Int?,
    @SerialName("rest_sec") val restSec: Int, @SerialName("rep_min") val repMin: Int, @SerialName("rep_max") val repMax: Int,
    val notes: String?, val deleted: Boolean,
    @SerialName("set_plan") val setPlan: JsonElement? = null,
)
@Serializable data class WorkoutSetDto(
    val id: String, @SerialName("workout_exercise_id") val workoutExerciseId: String, @SerialName("workout_id") val workoutId: String,
    @SerialName("user_id") val userId: String, @SerialName("exercise_id") val exerciseId: String, val position: Int,
    @SerialName("set_type") val setType: Int, @SerialName("weight_kg") val weightKg: Double?, val reps: Int?,
    @SerialName("duration_sec") val durationSec: Int?, val rpe: Double?, val completed: Boolean,
    @SerialName("completed_at") val completedAt: String?, @SerialName("performed_on") val performedOn: String, val deleted: Boolean,
)
@Serializable data class RecordDto(
    @SerialName("user_id") val userId: String, @SerialName("exercise_id") val exerciseId: String, val kind: Int,
    val value: Double, @SerialName("set_id") val setId: String?, @SerialName("achieved_at") val achievedAt: String,
)
@Serializable data class BodyWeightDto(
    @SerialName("user_id") val userId: String, @SerialName("measured_on") val measuredOn: String,
    @SerialName("weight_kg") val weightKg: Double, val deleted: Boolean,
)
@Serializable data class ProfileDto(
    @SerialName("user_id") val userId: String, @SerialName("display_name") val displayName: String,
    @SerialName("weight_unit") val weightUnit: String, @SerialName("time_zone") val timeZone: String,
    @SerialName("week_start") val weekStart: Int, @SerialName("default_rest_sec") val defaultRestSec: Int,
    @SerialName("tutorial_done") val tutorialDone: Boolean,
)

class DataApiException(val code: Int, message: String) : Exception(message)

/** Récords que dejaron de existir en el teléfono y hay que borrar también en el servidor. */
class PendingDeletes(context: Context) {
    private val prefs = context.getSharedPreferences("rupio_pending_deletes", Context.MODE_PRIVATE)
    @Synchronized fun add(exerciseId: String, kind: Int) {
        prefs.edit().putStringSet("records", (prefs.getStringSet("records", emptySet()).orEmpty() + "$exerciseId|$kind")).apply()
    }
    @Synchronized fun records(): List<Pair<String, Int>> = prefs.getStringSet("records", emptySet()).orEmpty()
        .mapNotNull { it.split('|').takeIf { p -> p.size == 2 }?.let { p -> p[0] to (p[1].toIntOrNull() ?: return@mapNotNull null) } }
    @Synchronized fun done(exerciseId: String, kind: Int) {
        prefs.edit().putStringSet("records", prefs.getStringSet("records", emptySet()).orEmpty() - "$exerciseId|$kind").apply()
    }
    fun clear() = prefs.edit().clear().apply()
}

/** Cliente PostgREST de la Neon Data API. Todas las peticiones llevan el JWT → RLS. */
class DataApiClient(private val http: OkHttpClient) {
    val json = Json { ignoreUnknownKeys = true; explicitNulls = true; encodeDefaults = true }
    private val media = "application/json".toMediaType()

    suspend fun <T> upsert(base: String, jwt: String, table: String, onConflict: String, rows: List<T>, ser: KSerializer<T>) = withContext(Dispatchers.IO) {
        if (rows.isEmpty()) return@withContext
        val url = "$base/$table".toHttpUrl().newBuilder().addQueryParameter("on_conflict", onConflict).build()
        val body = json.encodeToString(ListSerializer(ser), rows).toRequestBody(media)
        val req = Request.Builder().url(url).post(body)
            .header("Authorization", "Bearer $jwt")
            .header("Prefer", "resolution=merge-duplicates,return=minimal")
            .build()
        http.newCall(req).execute().use { r -> if (!r.isSuccessful) throw DataApiException(r.code, r.body?.string().orEmpty().take(300)) }
    }

    suspend fun delete(base: String, jwt: String, table: String, filters: List<Pair<String, String>>) = withContext(Dispatchers.IO) {
        val b = "$base/$table".toHttpUrl().newBuilder()
        filters.forEach { (k, v) -> b.addQueryParameter(k, v) }
        val req = Request.Builder().url(b.build()).delete().header("Authorization", "Bearer $jwt").header("Prefer", "return=minimal").build()
        http.newCall(req).execute().use { r -> if (!r.isSuccessful) throw DataApiException(r.code, r.body?.string().orEmpty().take(300)) }
    }

    suspend fun select(base: String, jwt: String, table: String, params: List<Pair<String, String>>): JsonArray = withContext(Dispatchers.IO) {
        val b = "$base/$table".toHttpUrl().newBuilder()
        params.forEach { (k, v) -> b.addQueryParameter(k, v) }
        val req = Request.Builder().url(b.build()).get().header("Authorization", "Bearer $jwt").header("Accept", "application/json").build()
        http.newCall(req).execute().use { r ->
            val text = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw DataApiException(r.code, text.take(300))
            json.parseToJsonElement(text).jsonArray
        }
    }
}

data class SyncStatus(val running: Boolean = false, val lastSuccess: Long? = null, val lastError: String? = null)

/**
 * Sincronización offline-first:
 *  PUSH: filas con dirty=1 en orden padre→hijo, en lotes (upsert masivo = 1 transacción por lote).
 *        Se limpian solo si no cambiaron mientras se subían (comparando updated_at local).
 *  PULL: por tabla, filas con updated_at (asignado por el SERVIDOR) > cursor, con solapamiento de
 *        5 s para no perder transacciones concurrentes. Nunca pisa cambios locales pendientes.
 */
class SyncEngine(
    private val context: Context,
    private val db: RupioDatabase,
    private val api: DataApiClient,
    private val auth: AuthRepository,
    private val settings: SettingsStore,
    private val pendingDeletes: PendingDeletes,
    /** Se llama con los ejercicios cuyas series se borraron en otro dispositivo (para recalcular récords). */
    private val onRemoteSetDeletes: suspend (Set<String>) -> Unit = {},
    // Inyectables para probar la sincronización contra un servidor falso.
    private val jwtProvider: suspend () -> String? = { auth.validJwt() },
    private val uidProvider: () -> String? = { auth.currentUserId },
    private val localOnly: () -> Boolean = { auth.isLocalOnly },
) {
    private val mutex = Mutex()
    private val prefs = context.getSharedPreferences("rupio_sync", Context.MODE_PRIVATE)
    private val _status = MutableStateFlow(SyncStatus(lastSuccess = prefs.getLong("last_ok", 0).takeIf { it > 0 }))
    val status: StateFlow<SyncStatus> = _status
    private val batch = 400

    suspend fun sync(): Boolean = mutex.withLock {
        if (localOnly()) return true
        val uid = uidProvider() ?: return true
        val base = settings.current().dataApiUrl.ifBlank { return true }
        val jwt = jwtProvider() ?: run { _status.value = _status.value.copy(lastError = "Sin conexión con el servidor"); return false }
        _status.value = _status.value.copy(running = true)
        return try {
            push(base, jwt, uid)
            pull(base, jwt, uid)
            val now = System.currentTimeMillis()
            prefs.edit().putLong("last_ok", now).apply()
            _status.value = SyncStatus(false, now, if (rejected > 0) "$rejected cambios no se pudieron subir; se reintentará" else null); true
        } catch (e: Exception) {
            _status.value = _status.value.copy(running = false, lastError = e.message?.take(160)); false
        }
    }

    /*
     * Datos que el servidor rechazaría (nombre vacío o de 300 letras, reloj del teléfono atrasado, 25 series…) se
     * ajustan a sus reglas ANTES de subir. Si aun así un lote falla por datos (4xx), se sube fila por fila: las buenas
     * pasan y solo las malas se quedan pendientes. Así una fila rara nunca bloquea toda la sincronización.
     */
    private var rejected = 0

    private suspend fun <E> pushTable(table: String, onConflict: String, load: suspend (Int) -> List<E>, send: suspend (List<E>) -> Unit, clean: suspend (E) -> Unit) {
        val skip = HashSet<E>()
        while (true) {
            val rows = load(batch + skip.size).filter { it !in skip }.take(batch)
            if (rows.isEmpty()) break
            try {
                send(rows); rows.forEach { clean(it) }
            } catch (e: DataApiException) {
                if (e.code !in 400..499 || e.code == 401 || e.code == 403) throw e   // sesión o servidor: reintentar después
                for (r in rows) try { send(listOf(r)); clean(r) } catch (e2: DataApiException) {
                    if (e2.code !in 400..499 || e2.code == 401 || e2.code == 403) throw e2
                    skip += r; rejected++
                }
            }
            if (rows.size < batch) break
        }
    }

    private fun name(s: String, fallback: String, max: Int = 120) = s.trim().ifBlank { fallback }.take(max)
    private fun rest(s: Int) = s.coerceIn(0, 1800)

    private suspend fun push(base: String, jwt: String, uid: String) {
        rejected = 0
        val ex = db.exercises(); val ro = db.routines(); val wo = db.workouts(); val rec = db.records(); val bw = db.bodyWeights()
        pushTable("exercises", "id", { ex.dirty(it) }, { rows ->
            api.upsert(base, jwt, "exercises", "id", rows.map {
                ExerciseDto(it.id, uid, name(it.name, "Ejercicio"), it.primaryMuscle, it.secondaryMuscles.split(',').filter { s -> s.isNotBlank() },
                    it.equipment, it.tracking.takeIf { t -> Tracking.entries.any { e -> e.code == t } } ?: "weight_reps", it.isLowerBody, it.deleted)
            }, ExerciseDto.serializer())
        }, { ex.markClean(it.id, it.updatedAt) })
        pushTable("routines", "id", { ro.dirtyRoutines(it) }, { rows ->
            api.upsert(base, jwt, "routines", "id", rows.map { RoutineDto(it.id, uid, name(it.name, "Rutina"), it.notes, it.position, it.createdAt.iso(), it.deleted) }, RoutineDto.serializer())
        }, { ro.cleanRoutine(it.id, it.updatedAt) })
        pushTable("routine_exercises", "id", { ro.dirtyExercises(it) }, { rows ->
            api.upsert(base, jwt, "routine_exercises", "id", rows.map {
                val lo = it.repMin.coerceIn(1, 100)
                RoutineExerciseDto(it.id, it.routineId, uid, it.exerciseId, it.position, it.supersetId, it.supersetRestMode.coerceIn(0, 1), it.supersetRestSec?.let(::rest),
                    rest(it.restSec), it.targetSets.coerceIn(1, 20), lo, it.repMax.coerceIn(lo, 100), it.notes, it.deleted, it.setPlan.planJson())
            }, RoutineExerciseDto.serializer())
        }, { ro.cleanExercise(it.id, it.updatedAt) })
        pushTable("workouts", "id", { wo.dirtyWorkouts(it) }, { rows ->
            api.upsert(base, jwt, "workouts", "id", rows.map {
                // Si el reloj del teléfono se movió hacia atrás a media sesión, el fin no puede quedar antes del inicio.
                WorkoutDto(it.id, uid, it.routineId, name(it.name, "Entrenamiento"), it.startedAt.iso(), it.endedAt?.let { e -> maxOf(e, it.startedAt) }?.iso(), it.timeZone,
                    it.localDate.day(), it.durationSec?.coerceAtLeast(0), it.totalVolumeKg.takeIf { v -> v.isFinite() && v >= 0 } ?: 0.0,
                    it.totalSets.coerceAtLeast(0), it.totalReps.coerceAtLeast(0), it.notes, it.deleted)
            }, WorkoutDto.serializer())
        }, { wo.cleanWorkout(it.id, it.updatedAt) })
        pushTable("workout_exercises", "id", { wo.dirtyExercises(it) }, { rows ->
            api.upsert(base, jwt, "workout_exercises", "id", rows.map {
                val lo = it.repMin.coerceIn(1, 100)
                WorkoutExerciseDto(it.id, it.workoutId, uid, it.exerciseId, it.position, it.supersetId, it.supersetRestMode.coerceIn(0, 1), it.supersetRestSec?.let(::rest),
                    rest(it.restSec), lo, it.repMax.coerceIn(lo, 100), it.notes, it.deleted, it.setPlan.planJson())
            }, WorkoutExerciseDto.serializer())
        }, { wo.cleanExercise(it.id, it.updatedAt) })
        pushTable("workout_sets", "id", { wo.dirtySets(it) }, { rows ->
            api.upsert(base, jwt, "workout_sets", "id", rows.map {
                WorkoutSetDto(it.id, it.workoutExerciseId, it.workoutId, uid, it.exerciseId, it.position, it.setType.coerceIn(0, 3),
                    it.weightKg?.takeIf { w -> w.isFinite() && kotlin.math.abs(w) <= 1500.0 }, it.reps?.takeIf { r -> r in 0..1000 },
                    it.durationSec?.takeIf { d -> d in 0..86_400 }, it.rpe?.takeIf { r -> r.isFinite() && r in 1.0..10.0 },
                    it.completed, it.completedAt?.iso(), it.performedOn.day(), it.deleted)
            }, WorkoutSetDto.serializer())
        }, { wo.cleanSet(it.id, it.updatedAt) })
        pushTable("personal_records", "user_id,exercise_id,kind", { rec.dirty(it) }, { rows ->
            api.upsert(base, jwt, "personal_records", "user_id,exercise_id,kind", rows.filter { it.kind in 1..5 && it.value.isFinite() }.map {
                RecordDto(uid, it.exerciseId, it.kind, it.value, it.setId, it.achievedAt.iso())
            }, RecordDto.serializer())
        }, { rec.clean(it.exerciseId, it.kind, it.updatedAt) })
        pushTable("body_weights", "user_id,measured_on", { bw.dirty(it) }, { rows ->
            api.upsert(base, jwt, "body_weights", "user_id,measured_on", rows.map {
                BodyWeightDto(uid, it.measuredOn.day(), it.weightKg.takeIf { w -> w.isFinite() }?.coerceIn(20.0, 400.0) ?: 20.0, it.deleted)
            }, BodyWeightDto.serializer())
        }, { bw.clean(it.userId, it.measuredOn, it.updatedAt) })
        for ((ex2, kind) in pendingDeletes.records()) {
            api.delete(base, jwt, "personal_records", listOf("user_id" to "eq.$uid", "exercise_id" to "eq.$ex2", "kind" to "eq.$kind"))
            pendingDeletes.done(ex2, kind)
        }
        val s = settings.current()
        val nm = (auth.state.value as? com.rupio.app.data.auth.AuthState.SignedIn)?.name ?: "Atleta"
        api.upsert(base, jwt, "profiles", "user_id", listOf(
            ProfileDto(uid, name(nm, "Atleta", 80), s.unit.code, ZoneId.systemDefault().id, if (s.weekStart == 7) 7 else 1, rest(s.defaultRestSec), s.onboardingDone)
        ), ProfileDto.serializer())
    }


    /*
     * Baja los cambios de una tabla SIN huecos.
     * Antes se paginaba solo por updated_at: si había más de 400 filas con la misma marca de tiempo o dentro de la
     * ventana de 5 s (un lote subido de golpe), la página se repetía y el resto NUNCA se bajaba.
     * Ahora: arranca 5 s antes del cursor (transacciones que confirman tarde) y pagina por llave (updated_at, id…).
     */
    private suspend fun pullTable(base: String, jwt: String, table: String, filters: List<Pair<String, String>>,
                                  tie: List<String> = listOf("id"), apply: suspend (List<JsonObject>) -> Unit) {
        val key = "cursor_$table"
        val cursor = prefs.getString(key, null)
        val since = cursor?.let { OffsetDateTime.parse(it).minusSeconds(5).toString() } ?: "1970-01-01T00:00:00Z"
        val order = (listOf("updated_at") + tie).joinToString(",") { "$it.asc" }
        var last: JsonObject? = null
        var newest: String? = cursor
        while (true) {
            val page = last?.let { listOf("or" to keysetAfter(it, tie)) } ?: listOf("updated_at" to "gt.$since")
            val rows = api.select(base, jwt, table, filters + page + listOf("select" to "*", "order" to order, "limit" to "$batch")).map { it.jsonObject }
            if (rows.isEmpty()) break
            apply(rows)
            last = rows.last()
            val ts = last["updated_at"]!!.jsonPrimitive.content
            if (newest == null || OffsetDateTime.parse(ts).isAfter(OffsetDateTime.parse(newest))) newest = ts
            if (rows.size < batch) break
        }
        if (newest != null && newest != cursor) prefs.edit().putString(key, newest).apply()
    }

    /** Filtro PostgREST "después de esta fila" en orden (updated_at, tie1, tie2…). */
    private fun keysetAfter(row: JsonObject, tie: List<String>): String {
        fun v(c: String) = "\"" + row[c]!!.jsonPrimitive.content.replace("\"", "") + "\""
        val ts = v("updated_at")
        val parts = mutableListOf("updated_at.gt.$ts")
        for (k in tie.indices) {
            val eqs = (listOf("updated_at.eq.$ts") + tie.take(k).map { "$it.eq.${v(it)}" } + "${tie[k]}.gt.${v(tie[k])}").joinToString(",")
            parts += "and($eqs)"
        }
        return "(" + parts.joinToString(",") + ")"
    }

    private suspend fun pull(base: String, jwt: String, uid: String) {
        val remoteDeleted = mutableSetOf<String>()
        val j = api.json
        val mine = listOf("user_id" to "eq.$uid") // filtro explícito además de RLS: usa el índice (user_id, updated_at)
        pullTable(base, jwt, "exercises", listOf("owner_id" to "eq.$uid")) { rows ->
            val dtos = rows.map { j.decodeFromJsonElement(ExerciseDto.serializer(), it) to it["updated_at"]!!.jsonPrimitive.content.ms() }
            val dirty = db.exercises().dirtyIds(dtos.map { it.first.id }).toSet()
            db.exercises().upsert(dtos.filter { it.first.id !in dirty }.map { (d, u) ->
                ExerciseEntity(d.id, d.name, d.name.searchKey(), d.primaryMuscle, d.secondaryMuscles.joinToString(","), d.equipment, d.tracking,
                    d.isLowerBody, isCustom = true, ownerId = d.ownerId, deleted = d.deleted, updatedAt = u, dirty = false)
            })
        }
        pullTable(base, jwt, "routines", mine) { rows ->
            val dtos = rows.map { j.decodeFromJsonElement(RoutineDto.serializer(), it) to it["updated_at"]!!.jsonPrimitive.content.ms() }
            val dirty = db.routines().dirtyRoutineIds(dtos.map { it.first.id }).toSet()
            db.routines().upsertRoutines(dtos.filter { it.first.id !in dirty }.map { (d, u) ->
                RoutineEntity(d.id, d.userId, d.name, d.notes, d.position, d.createdAt.ms(), u, d.deleted, dirty = false)
            })
        }
        pullTable(base, jwt, "routine_exercises", mine) { rows ->
            val dtos = rows.map { j.decodeFromJsonElement(RoutineExerciseDto.serializer(), it) to it["updated_at"]!!.jsonPrimitive.content.ms() }
            val dirty = db.routines().dirtyExerciseIds(dtos.map { it.first.id }).toSet()
            db.routines().upsertExercises(dtos.filter { it.first.id !in dirty && db.routines().get(it.first.routineId) != null }.map { (d, u) ->
                RoutineExerciseEntity(d.id, d.routineId, d.userId, d.exerciseId, d.position, d.supersetId, d.supersetRestMode, d.supersetRestSec,
                    d.restSec, d.targetSets, d.repMin, d.repMax, d.notes, u, d.deleted, dirty = false, setPlan = d.setPlan.planText())
            })
        }
        pullTable(base, jwt, "workouts", mine) { rows ->
            val dtos = rows.map { j.decodeFromJsonElement(WorkoutDto.serializer(), it) to it["updated_at"]!!.jsonPrimitive.content.ms() }
            val dirty = db.workouts().dirtyWorkoutIds(dtos.map { it.first.id }).toSet()
            db.workouts().upsertWorkouts(dtos.filter { it.first.id !in dirty }.map { (d, u) ->
                WorkoutEntity(d.id, d.userId, d.routineId, d.name, d.startedAt.ms(), d.endedAt?.ms(), d.timeZone, d.localDate.epochDay(),
                    d.durationSec, d.totalVolumeKg, d.totalSets, d.totalReps, 0, d.notes, u, d.deleted, dirty = false)
            })
        }
        pullTable(base, jwt, "workout_exercises", mine) { rows ->
            val dtos = rows.map { j.decodeFromJsonElement(WorkoutExerciseDto.serializer(), it) to it["updated_at"]!!.jsonPrimitive.content.ms() }
            val dirty = db.workouts().dirtyExerciseIds(dtos.map { it.first.id }).toSet()
            db.workouts().upsertExercises(dtos.filter { it.first.id !in dirty && db.workouts().get(it.first.workoutId) != null }.map { (d, u) ->
                WorkoutExerciseEntity(d.id, d.workoutId, d.userId, d.exerciseId, d.position, d.supersetId, d.supersetRestMode, d.supersetRestSec,
                    d.restSec, d.repMin, d.repMax, d.notes, u, d.deleted, dirty = false, setPlan = d.setPlan.planText())
            })
        }
        pullTable(base, jwt, "workout_sets", mine) { rows ->
            val dtos = rows.map { j.decodeFromJsonElement(WorkoutSetDto.serializer(), it) to it["updated_at"]!!.jsonPrimitive.content.ms() }
            val dirty = db.workouts().dirtySetIds(dtos.map { it.first.id }).toSet()
            val parents = db.workouts()
            remoteDeleted += dtos.filter { it.first.deleted && it.first.id !in dirty }.map { it.first.exerciseId }
            db.workouts().upsertSets(dtos.filter { it.first.id !in dirty }.filter { parents.get(it.first.workoutId) != null }.map { (d, u) ->
                WorkoutSetEntity(d.id, d.workoutExerciseId, d.workoutId, d.userId, d.exerciseId, d.position, d.setType, d.weightKg, d.reps,
                    d.durationSec, d.rpe, d.completed, d.completedAt?.ms(), d.performedOn.epochDay(), false, u, d.deleted, dirty = false)
            })
        }
        pullTable(base, jwt, "personal_records", mine, tie = listOf("exercise_id", "kind")) { rows ->
            val dtos = rows.map { j.decodeFromJsonElement(RecordDto.serializer(), it) to it["updated_at"]!!.jsonPrimitive.content.ms() }
            db.records().upsert(dtos.map { (d, u) -> PersonalRecordEntity(d.exerciseId, d.kind, d.userId, d.value, d.setId, d.achievedAt.ms(), u, dirty = false) })
        }
        pullTable(base, jwt, "body_weights", mine, tie = listOf("measured_on")) { rows ->
            val dtos = rows.map { j.decodeFromJsonElement(BodyWeightDto.serializer(), it) to it["updated_at"]!!.jsonPrimitive.content.ms() }
            val dirty = db.bodyWeights().dirtyDays(uid, dtos.map { it.first.measuredOn.epochDay() }).toSet()
            db.bodyWeights().upsertAll(dtos.filter { it.first.measuredOn.epochDay() !in dirty }.map { (d, u) ->
                BodyWeightEntity(d.userId, d.measuredOn.epochDay(), d.weightKg, u, d.deleted, dirty = false)
            })
        }
        if (remoteDeleted.isNotEmpty()) onRemoteSetDeletes(remoteDeleted)
    }

    fun resetCursors() { prefs.edit().clear().apply(); pendingDeletes.clear() }

    /** Vuelve a bajar todo una vez (p. ej. tras agregar columnas nuevas como set_plan). No toca lo pendiente de subir. */
    fun resetPullCursors() { val last = prefs.getLong("last_ok", 0); prefs.edit().clear().putLong("last_ok", last).apply() }

    companion object {
        fun schedule(context: Context) {
            val c = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "rupio-sync", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES).setConstraints(c).build(),
            )
        }
        fun now(context: Context) {
            val c = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "rupio-sync-now", ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(c).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build(),
            )
        }
    }
}

class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as RupioApp).container
        return if (c.sync.sync()) Result.success() else Result.retry()
    }
}
