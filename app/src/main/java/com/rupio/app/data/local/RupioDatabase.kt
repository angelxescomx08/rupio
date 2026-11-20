package com.rupio.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.rupio.app.core.searchKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Database(
    entities = [
        ExerciseEntity::class, RoutineEntity::class, RoutineExerciseEntity::class, WorkoutEntity::class,
        WorkoutExerciseEntity::class, WorkoutSetEntity::class, PersonalRecordEntity::class, BodyWeightEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class RupioDatabase : RoomDatabase() {
    abstract fun exercises(): ExerciseDao
    abstract fun routines(): RoutineDao
    abstract fun workouts(): WorkoutDao
    abstract fun records(): RecordDao
    abstract fun bodyWeights(): BodyWeightDao
    abstract fun stats(): StatsDao
    abstract fun admin(): AdminDao

    companion object {
        /** v2: series planeadas por serie en rutinas (y su copia en el entrenamiento). Sin pérdida de datos. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE routine_exercises ADD COLUMN set_plan TEXT")
                db.execSQL("ALTER TABLE workout_exercises ADD COLUMN set_plan TEXT")
            }
        }

        /** v3: registro de peso corporal (una fila por día). */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `body_weights` (`user_id` TEXT NOT NULL, `measured_on` INTEGER NOT NULL, `weight_kg` REAL NOT NULL, `updated_at` INTEGER NOT NULL, `deleted` INTEGER NOT NULL, `dirty` INTEGER NOT NULL, PRIMARY KEY(`user_id`, `measured_on`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_body_weights_dirty` ON `body_weights` (`dirty`)")
            }
        }

        fun build(context: Context): RupioDatabase =
            Room.databaseBuilder(context, RupioDatabase::class.java, "rupio.db")
                // WAL: lecturas (estadísticas) no bloquean escrituras (marcar series).
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .addCallback(object : Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        db.execSQL("PRAGMA foreign_keys = ON")
                        // Mantiene las estadísticas del planificador de SQLite al día (barato, SQLite ≥ 3.18).
                        db.query("PRAGMA optimize").close()
                    }
                })
                .build()
    }
}

/**
 * Catálogo de ejercicios en español (assets/exercises_es.json). La lista parte de free-exercise-db (Unlicense).
 * Imágenes: desde la v1.9 son ilustraciones de RepDB (licencia gratuita con crédito, ver docs/IMAGENES_REPDB.md); las
 * fotos de free-exercise-db se quitaron porque su origen y licencia no están claros. Van DENTRO de la app (assets/ex,
 * WebP) y NO se suben a git: se regeneran con tools/repdb/instalar_imagenes.py. Un ejercicio sin equivalente tiene
 * "images": [] y la app muestra el ícono de pesa.
 */
object CatalogSeeder {
    /** Súbelo cuando cambie el catálogo (nombres, imágenes…): se vuelve a escribir aunque ya esté completo. */
    // v3 (v1.9): técnica en pasos (posición, movimiento y error común) y ejercicios de cardio por tiempo.
    // v4 (v1.9): imágenes de RepDB; 35 ejercicios quedan sin imagen y 9 con una sola pose.
    // v5 (v1.9): 7 más con imagen de RepDB (crunch oblicuo, glute ham raise…); quedan 28 sin imagen.
    // v6 (v1.9): 15 más con imagen aproximada (otro aparato) y su nota; quedan 13 sin imagen.
    private const val VERSION = 6
    @Serializable
    private data class Item(
        val id: String, val src: String, val name: String, val primary: String, val secondary: List<String>,
        val equipment: String, val tracking: String, val lower: Boolean, val level: String, val mechanic: String,
        val cue: String, val images: List<String>,
        /** v1.9: qué cambia entre la imagen y el ejercicio ("La imagen muestra mancuernas; aquí se hace en polea"). */
        val imageNote: String? = null,
    )

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun seedIfNeeded(context: Context, db: RupioDatabase) {
        val text = context.assets.open("exercises_es.json").bufferedReader().use { it.readText() }
        val items = json.decodeFromString<List<Item>>(text)
        val prefs = context.getSharedPreferences("rupio_app", Context.MODE_PRIVATE)
        if (db.exercises().catalogCount() >= items.size && prefs.getInt("catalog_v", 1) >= VERSION) return
        db.exercises().upsert(items.map {
            ExerciseEntity(
                id = it.id, name = it.name, searchKey = it.name.searchKey(), primaryMuscle = it.primary,
                secondaryMuscles = it.secondary.joinToString(","), equipment = it.equipment, tracking = it.tracking,
                isLowerBody = it.lower, level = it.level, cue = it.cue, image0 = it.images.getOrNull(0),
                image1 = it.images.getOrNull(1), isCustom = false, dirty = false,
            )
        })
        prefs.edit().putInt("catalog_v", VERSION).apply()
    }

    @Volatile private var notes: Map<String, String>? = null

    /**
     * v1.9: la nota de la imagen de un ejercicio del catálogo (null si la imagen es exacta, no tiene o es personalizado).
     * Sale del mismo exercises_es.json (se lee una vez y queda en memoria): no hace falta otra columna en la base.
     */
    suspend fun imageNote(context: Context, exerciseId: String): String? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val map = notes ?: run {
            val text = context.assets.open("exercises_es.json").bufferedReader().use { it.readText() }
            json.decodeFromString<List<Item>>(text).mapNotNull { i -> i.imageNote?.takeIf { it.isNotBlank() }?.let { i.id to it } }.toMap()
                .also { notes = it }
        }
        map[exerciseId]
    }
}

object Muscles {
    val names = linkedMapOf(
        "pecho" to "Pecho", "dorsales" to "Dorsales", "espalda_media" to "Espalda media", "lumbar" to "Lumbar",
        "hombros" to "Hombros", "trapecio" to "Trapecio", "biceps" to "Bíceps", "triceps" to "Tríceps",
        "antebrazos" to "Antebrazos", "abdomen" to "Abdomen", "cuadriceps" to "Cuádriceps",
        "isquiotibiales" to "Isquiotibiales", "gluteos" to "Glúteos", "pantorrillas" to "Pantorrillas",
        "aductores" to "Aductores", "abductores" to "Abductores", "cuello" to "Cuello",
        // v1.9: grupos que no son un músculo (no cuentan para las series por músculo, ver MuscleSplit.NOT_COUNTED).
        "cardio" to "Cardio", "cuerpo_completo" to "Cuerpo completo",
    )
    fun label(code: String) = names[code] ?: code.replaceFirstChar { it.uppercase() }

    /** v1.9: "Espalda" junta lo que un principiante llama espalda (los filtros usaban solo nombres de anatomía). */
    val BACK = setOf("dorsales", "espalda_media", "lumbar", "trapecio")

    val equipment = mapOf(
        "barra" to "Barra", "mancuernas" to "Mancuernas", "polea" to "Polea", "maquina" to "Máquina",
        "peso_corporal" to "Peso corporal", "pesa_rusa" to "Pesa rusa", "bandas" to "Bandas", "barra_z" to "Barra Z",
        "smith" to "Máquina Smith", "barra_hexagonal" to "Barra hexagonal", "fitball" to "Fitball",
        "balon_medicinal" to "Balón medicinal", "otro" to "Otro",
    )
    fun equipmentLabel(code: String) = equipment[code] ?: code
}
