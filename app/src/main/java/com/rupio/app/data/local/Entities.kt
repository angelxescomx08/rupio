package com.rupio.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * Base de datos LOCAL (SQLite vía Room) = fuente de verdad de la UI.
 * La app funciona sin internet en el gimnasio y sincroniza con Neon después.
 * Solo vive aquí la información del usuario con sesión iniciada, por eso
 * los índices locales no empiezan por user_id (sería un byte inútil por fila).
 *
 * Fechas: instantes en epoch ms (UTC); días locales como epochDay (Int) → comparaciones
 * enteras rapidísimas y agrupar por semana con aritmética.
 * dirty = cambios pendientes de subir.
 */

@Entity(
    tableName = "exercises",
    indices = [Index("primary_muscle"), Index(value = ["is_custom", "dirty"])],
)
data class ExerciseEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "search_key") val searchKey: String,
    @ColumnInfo(name = "primary_muscle") val primaryMuscle: String,
    @ColumnInfo(name = "secondary_muscles") val secondaryMuscles: String, // csv
    val equipment: String,
    val tracking: String,
    @ColumnInfo(name = "is_lower_body") val isLowerBody: Boolean,
    val level: String = "beginner",
    val cue: String = "",
    @ColumnInfo(name = "image0") val image0: String? = null,
    @ColumnInfo(name = "image1") val image1: String? = null,
    @ColumnInfo(name = "is_custom") val isCustom: Boolean = false,
    @ColumnInfo(name = "owner_id") val ownerId: String? = null,
    val deleted: Boolean = false,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = 0,
    val dirty: Boolean = false,
)

@Entity(tableName = "routines", indices = [Index(value = ["deleted", "position"]), Index("dirty")])
data class RoutineEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    val name: String,
    val notes: String? = null,
    val position: Int = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    val deleted: Boolean = false,
    val dirty: Boolean = true,
)

@Entity(
    tableName = "routine_exercises",
    foreignKeys = [ForeignKey(entity = RoutineEntity::class, parentColumns = ["id"], childColumns = ["routine_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["routine_id", "position"]), Index("dirty")],
)
data class RoutineExerciseEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "routine_id") val routineId: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "exercise_id") val exerciseId: String,
    val position: Int,
    @ColumnInfo(name = "superset_id") val supersetId: String? = null,
    @ColumnInfo(name = "superset_rest_mode") val supersetRestMode: Int = 0,
    @ColumnInfo(name = "superset_rest_sec") val supersetRestSec: Int? = null,
    @ColumnInfo(name = "rest_sec") val restSec: Int = 90,
    @ColumnInfo(name = "target_sets") val targetSets: Int = 3,
    @ColumnInfo(name = "rep_min") val repMin: Int = 8,
    @ColumnInfo(name = "rep_max") val repMax: Int = 12,
    val notes: String? = null,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    val deleted: Boolean = false,
    val dirty: Boolean = true,
    /** Series planeadas (JSON de PlanSet): peso × reps por serie, como en Hevy. */
    @ColumnInfo(name = "set_plan") val setPlan: String? = null,
)

@Entity(
    tableName = "workouts",
    indices = [
        // Historial (orden + keyset) y estadísticas por rango de días.
        Index(value = ["deleted", "local_date", "started_at"]),
        Index("dirty"),
        Index("ended_at"),
    ],
)
data class WorkoutEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "routine_id") val routineId: String? = null,
    val name: String,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "ended_at") val endedAt: Long? = null,
    @ColumnInfo(name = "time_zone") val timeZone: String,
    @ColumnInfo(name = "local_date") val localDate: Int,
    @ColumnInfo(name = "duration_sec") val durationSec: Int? = null,
    @ColumnInfo(name = "total_volume_kg") val totalVolumeKg: Double = 0.0,
    @ColumnInfo(name = "total_sets") val totalSets: Int = 0,
    @ColumnInfo(name = "total_reps") val totalReps: Int = 0,
    @ColumnInfo(name = "pr_count") val prCount: Int = 0,
    val notes: String? = null,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    val deleted: Boolean = false,
    val dirty: Boolean = true,
)

@Entity(
    tableName = "workout_exercises",
    foreignKeys = [ForeignKey(entity = WorkoutEntity::class, parentColumns = ["id"], childColumns = ["workout_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["workout_id", "position"]), Index("dirty")],
)
data class WorkoutExerciseEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "workout_id") val workoutId: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "exercise_id") val exerciseId: String,
    val position: Int,
    @ColumnInfo(name = "superset_id") val supersetId: String? = null,
    @ColumnInfo(name = "superset_rest_mode") val supersetRestMode: Int = 0,
    @ColumnInfo(name = "superset_rest_sec") val supersetRestSec: Int? = null,
    @ColumnInfo(name = "rest_sec") val restSec: Int = 90,
    @ColumnInfo(name = "rep_min") val repMin: Int = 8,
    @ColumnInfo(name = "rep_max") val repMax: Int = 12,
    val notes: String? = null,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    val deleted: Boolean = false,
    val dirty: Boolean = true,
    /** Copia del plan de la rutina al empezar (valores sugeridos si aún no hay historial). */
    @ColumnInfo(name = "set_plan") val setPlan: String? = null,
)

@Entity(
    tableName = "workout_sets",
    foreignKeys = [ForeignKey(entity = WorkoutExerciseEntity::class, parentColumns = ["id"], childColumns = ["workout_exercise_id"], onDelete = ForeignKey.CASCADE)],
    indices = [
        Index(value = ["workout_exercise_id", "position"]),
        // Progresión / historial / récords por ejercicio (cubre el filtro y el orden).
        Index(value = ["exercise_id", "completed", "performed_on"]),
        // Volumen y series por músculo en un rango de días.
        Index(value = ["performed_on", "completed"]),
        Index("dirty"),
    ],
)
data class WorkoutSetEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "workout_exercise_id") val workoutExerciseId: String,
    @ColumnInfo(name = "workout_id") val workoutId: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "exercise_id") val exerciseId: String,
    val position: Int,
    @ColumnInfo(name = "set_type") val setType: Int = 0,
    @ColumnInfo(name = "weight_kg") val weightKg: Double? = null,
    val reps: Int? = null,
    @ColumnInfo(name = "duration_sec") val durationSec: Int? = null,
    val rpe: Double? = null,
    val completed: Boolean = false,
    @ColumnInfo(name = "completed_at") val completedAt: Long? = null,
    @ColumnInfo(name = "performed_on") val performedOn: Int,
    @ColumnInfo(name = "is_pr") val isPr: Boolean = false,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    val deleted: Boolean = false,
    val dirty: Boolean = true,
)

@Entity(tableName = "personal_records", primaryKeys = ["exercise_id", "kind"], indices = [Index("dirty")])
data class PersonalRecordEntity(
    @ColumnInfo(name = "exercise_id") val exerciseId: String,
    val kind: Int,
    @ColumnInfo(name = "user_id") val userId: String,
    val value: Double,
    @ColumnInfo(name = "set_id") val setId: String?,
    @ColumnInfo(name = "achieved_at") val achievedAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    val dirty: Boolean = true,
)

/**
 * Peso corporal: UNA fila por día (clave natural usuario + día local). Registrar dos veces el mismo día
 * actualiza la fila (no duplica) y dos teléfonos que registran el mismo día convergen sin conflictos.
 * measured_on = día local (epoch day) → las consultas por rango recorren la clave primaria en orden.
 */
@Entity(tableName = "body_weights", primaryKeys = ["user_id", "measured_on"], indices = [Index("dirty")])
data class BodyWeightEntity(
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "measured_on") val measuredOn: Int,
    @ColumnInfo(name = "weight_kg") val weightKg: Double,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    val deleted: Boolean = false,
    val dirty: Boolean = true,
)

/** Mejor serie de un ejercicio en un día (para "Tu progreso"). */
data class DayBest(
    @ColumnInfo(name = "exercise_id") val exerciseId: String,
    @ColumnInfo(name = "performed_on") val day: Int,
    val e1rm: Double?,
    val weight: Double?,
    val reps: Int?,
)

/** Máximos de cada tipo de récord de un ejercicio (una sola pasada por sus series). */
data class RecordMaxes(
    @ColumnInfo(name = "max_weight") val maxWeight: Double?,
    @ColumnInfo(name = "e1rm") val e1rm: Double?,
    @ColumnInfo(name = "set_volume") val setVolume: Double?,
    @ColumnInfo(name = "max_reps") val maxReps: Double?,
    @ColumnInfo(name = "max_duration") val maxDuration: Double?,
)

data class SetRef(val id: String, @ColumnInfo(name = "completed_at") val completedAt: Long?)

data class Totals2(val n: Int, val vol: Double, val sets: Int, val first: Int?)

/** Punto de la gráfica de peso (día o promedio de una semana/mes según el rango). */
data class WeightPoint(val day: Int, val kg: Double, val n: Int)

// ------------------------------------------------------------------ proyecciones para consultas
data class BucketRow(val bucket: String, val volume: Double, val sets: Int, val reps: Int, val workouts: Int)
data class SessionSetRow(
    @ColumnInfo(name = "performed_on") val performedOn: Int,
    @ColumnInfo(name = "workout_id") val workoutId: String,
    @ColumnInfo(name = "weight_kg") val weightKg: Double?,
    val reps: Int?,
    @ColumnInfo(name = "duration_sec") val durationSec: Int?,
    val rpe: Double?,
    @ColumnInfo(name = "set_type") val setType: Int,
    val position: Int,
)
data class E1rmPoint(@ColumnInfo(name = "performed_on") val performedOn: Int, val best: Double, @ColumnInfo(name = "top_weight") val topWeight: Double)
data class ExerciseUsage(@ColumnInfo(name = "exercise_id") val exerciseId: String, val name: String, val sessions: Int)
data class DayCount(@ColumnInfo(name = "local_date") val localDate: Int, val n: Int)
data class RecordWithName(
    @ColumnInfo(name = "exercise_id") val exerciseId: String, val kind: Int, val value: Double,
    @ColumnInfo(name = "achieved_at") val achievedAt: Long, val name: String, val tracking: String,
)
data class WorkoutExerciseSummary(
    @ColumnInfo(name = "workout_id") val workoutId: String, @ColumnInfo(name = "superset_id") val supersetId: String?,
    val name: String, val sets: Int,
)
data class ExerciseSetCount(@ColumnInfo(name = "exercise_id") val exerciseId: String, val sets: Int, val volume: Double)

/** v1.8: lo mejor de cada sesión de un ejercicio, para la gráfica dentro del entreno. */
data class SessionProgressRow(
    @ColumnInfo(name = "performed_on") val performedOn: Int,
    val e1rm: Double?,
    @ColumnInfo(name = "top_weight") val topWeight: Double?,
    val volume: Double,
    @ColumnInfo(name = "max_reps") val maxReps: Int?,
    @ColumnInfo(name = "max_duration") val maxDuration: Int?,
)

/** Nombre y día de una fila, para decir qué cambio rechazó el servidor. */
data class RowLabel(val name: String?, val day: Int?)

/** Meses con entrenos (selector de mes del Historial). ym = "2019-03". */
data class MonthCount(val ym: String, val n: Int)
