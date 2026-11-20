package com.rupio.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.rupio.app.core.AppLanguage
import com.rupio.app.core.I18n
import com.rupio.app.core.searchKey
import com.rupio.app.core.tr
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
    // v2.0: el catálogo se escribe en el idioma de la app. exercises_en.json trae, por id, el nombre, la técnica y la nota
    // de la imagen en inglés; al cambiar de idioma se vuelve a escribir (son 262 filas: tarda milisegundos). La clave de
    // búsqueda lleva los dos nombres, así "press banca" y "bench press" encuentran lo mismo en cualquier idioma.
    @Serializable
    private data class Item(
        val id: String, val src: String, val name: String, val primary: String, val secondary: List<String>,
        val equipment: String, val tracking: String, val lower: Boolean, val level: String, val mechanic: String,
        val cue: String, val images: List<String>,
        /** v1.9: qué cambia entre la imagen y el ejercicio ("La imagen muestra mancuernas; aquí se hace en polea"). */
        val imageNote: String? = null,
    )

    /** Traducción de un ejercicio (exercises_en.json). */
    @Serializable
    private data class English(val id: String, val name: String, val cue: String, val imageNote: String? = null)
    private val json = Json { ignoreUnknownKeys = true }
    private fun english(context: Context): Map<String, English> =
        json.decodeFromString<List<English>>(context.assets.open("exercises_en.json").bufferedReader().use { it.readText() }).associateBy { it.id }

    suspend fun seedIfNeeded(context: Context, db: RupioDatabase, lang: AppLanguage = I18n.language) {
        val text = context.assets.open("exercises_es.json").bufferedReader().use { it.readText() }
        val items = json.decodeFromString<List<Item>>(text)
        val prefs = context.getSharedPreferences("rupio_app", Context.MODE_PRIVATE)
        if (db.exercises().catalogCount() >= items.size && prefs.getInt("catalog_v", 1) >= VERSION &&
            prefs.getString("catalog_lang", AppLanguage.ES.code) == lang.code) return
        val en = if (lang == AppLanguage.EN) english(context) else emptyMap()
        db.exercises().upsert(items.map {
            val t = en[it.id]
            val name = t?.name ?: it.name
            ExerciseEntity(
                id = it.id, name = name, searchKey = (if (t != null) "$name ${it.name}" else name).searchKey(), primaryMuscle = it.primary,
                secondaryMuscles = it.secondary.joinToString(","), equipment = it.equipment, tracking = it.tracking,
                isLowerBody = it.lower, level = it.level, cue = t?.cue ?: it.cue, image0 = it.images.getOrNull(0),
                image1 = it.images.getOrNull(1), isCustom = false, dirty = false,
            )
        })
        prefs.edit().putInt("catalog_v", VERSION).putString("catalog_lang", lang.code).apply()
    }
    @Volatile private var notes: Map<String, String>? = null
    @Volatile private var notesEn: Map<String, String>? = null
    /**
     * v1.9: la nota de la imagen de un ejercicio del catálogo (null si la imagen es exacta, no tiene o es personalizado).
     * Sale del mismo exercises_es.json (o exercises_en.json en inglés), leído una vez: no hace falta otra columna en la base.
     */
    suspend fun imageNote(context: Context, exerciseId: String): String? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (I18n.isEnglish) {
            val map = notesEn ?: english(context).values.mapNotNull { e -> e.imageNote?.takeIf { it.isNotBlank() }?.let { e.id to it } }.toMap()
                .also { notesEn = it }
            return@withContext map[exerciseId]
        }
        val map = notes ?: run {
            val text = context.assets.open("exercises_es.json").bufferedReader().use { it.readText() }
            json.decodeFromString<List<Item>>(text).mapNotNull { i -> i.imageNote?.takeIf { it.isNotBlank() }?.let { i.id to it } }.toMap()
                .also { notes = it }
        }
        map[exerciseId]
    }
}

object Muscles {
    private val es = linkedMapOf(
        "pecho" to "Pecho", "dorsales" to "Dorsales", "espalda_media" to "Espalda media", "lumbar" to "Lumbar",
        "hombros" to "Hombros", "trapecio" to "Trapecio", "biceps" to "Bíceps", "triceps" to "Tríceps",
        "antebrazos" to "Antebrazos", "abdomen" to "Abdomen", "cuadriceps" to "Cuádriceps",
        "isquiotibiales" to "Isquiotibiales", "gluteos" to "Glúteos", "pantorrillas" to "Pantorrillas",
        "aductores" to "Aductores", "abductores" to "Abductores", "cuello" to "Cuello",
        // v1.9: grupos que no son un músculo (no cuentan para las series por músculo, ver MuscleSplit.NOT_COUNTED).
        "cardio" to "Cardio", "cuerpo_completo" to "Cuerpo completo",
    )
    /** v2.0: los mismos códigos en inglés (los nombres que usan Hevy y Strong). */
    private val en = linkedMapOf(
        "pecho" to "Chest", "dorsales" to "Lats", "espalda_media" to "Upper back", "lumbar" to "Lower back",
        "hombros" to "Shoulders", "trapecio" to "Traps", "biceps" to "Biceps", "triceps" to "Triceps",
        "antebrazos" to "Forearms", "abdomen" to "Abs", "cuadriceps" to "Quads",
        "isquiotibiales" to "Hamstrings", "gluteos" to "Glutes", "pantorrillas" to "Calves",
        "aductores" to "Adductors", "abductores" to "Abductors", "cuello" to "Neck",
        "cardio" to "Cardio", "cuerpo_completo" to "Full body",
    )
    /** Código → nombre en el idioma de la app (el orden es el de los filtros). */
    val names: Map<String, String> get() = if (I18n.isEnglish) en else es
    fun label(code: String) = names[code] ?: code.replaceFirstChar { it.uppercase() }

    /** v1.9: "Espalda" junta lo que un principiante llama espalda (los filtros usaban solo nombres de anatomía). */
    val BACK = setOf("dorsales", "espalda_media", "lumbar", "trapecio")

    private val equipmentEs = linkedMapOf(
        "barra" to "Barra", "mancuernas" to "Mancuernas", "polea" to "Polea", "maquina" to "Máquina",
        "peso_corporal" to "Peso corporal", "pesa_rusa" to "Pesa rusa", "bandas" to "Bandas", "barra_z" to "Barra Z",
        "smith" to "Máquina Smith", "barra_hexagonal" to "Barra hexagonal", "fitball" to "Fitball",
        "balon_medicinal" to "Balón medicinal", "otro" to "Otro",
    )
    private val equipmentEn = linkedMapOf(
        "barra" to "Barbell", "mancuernas" to "Dumbbells", "polea" to "Cable", "maquina" to "Machine",
        "peso_corporal" to "Bodyweight", "pesa_rusa" to "Kettlebell", "bandas" to "Bands", "barra_z" to "EZ bar",
        "smith" to "Smith machine", "barra_hexagonal" to "Trap bar", "fitball" to "Stability ball",
        "balon_medicinal" to "Medicine ball", "otro" to "Other",
    )
    val equipment: Map<String, String> get() = if (I18n.isEnglish) equipmentEn else equipmentEs
    fun equipmentLabel(code: String) = equipment[code] ?: code
}
