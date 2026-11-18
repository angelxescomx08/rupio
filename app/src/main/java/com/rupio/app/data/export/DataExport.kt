package com.rupio.app.data.export

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.rupio.app.data.local.BodyWeightEntity
import com.rupio.app.data.local.RupioDatabase
import com.rupio.app.data.local.WorkoutEntity
import com.rupio.app.data.local.WorkoutSetEntity
import com.rupio.app.domain.SetType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * v1.7 (N9): Perfil → "Exportar mis datos". Tres archivos CSV (entrenamientos, series y peso corporal) que se abren en
 * Excel, Google Sheets o Numbers, y se comparten con el menú de Android (correo, Drive, WhatsApp…).
 * Pesos en kilos con punto decimal; fechas AAAA-MM-DD; horas en la zona donde entrenaste. UTF-8 con BOM para que
 * Excel muestre bien los acentos.
 */
object DataExport {
    const val WORKOUTS = "rupio_entrenamientos.csv"
    const val SETS = "rupio_series.csv"
    const val BODY = "rupio_peso_corporal.csv"
    private const val BOM = "﻿"
    private val hm = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    /**
     * Un campo de texto: entre comillas si trae coma, comillas o salto de línea. Un texto que empieza con = + - @
     * lleva un apóstrofo para que la hoja de cálculo no lo ejecute como fórmula (inyección CSV).
     */
    fun text(v: String?): String {
        var s = v.orEmpty()
        if (s.isNotEmpty() && s[0] in "=+-@\t\r") s = "'$s"
        return if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
    }

    /** Número con punto decimal y sin ceros de más ("102.5", "80"); vacío si no hay. */
    fun num(v: Double?): String = when {
        v == null || !v.isFinite() -> ""
        v == Math.rint(v) -> v.toLong().toString()
        else -> String.format(Locale.US, "%.3f", v).trimEnd('0').trimEnd('.')
    }

    private fun day(epochDay: Int) = LocalDate.ofEpochDay(epochDay.toLong()).toString()
    private fun time(ms: Long?, zone: String): String = ms?.let {
        val z = runCatching { ZoneId.of(zone) }.getOrDefault(ZoneId.systemDefault())
        Instant.ofEpochMilli(it).atZone(z).format(hm)
    }.orEmpty()

    fun workoutsCsv(ws: List<WorkoutEntity>): String = buildString {
        append(BOM).append("id,fecha,nombre,inicio,fin,zona_horaria,duracion_min,volumen_kg,series,repeticiones,records,notas\n")
        ws.forEach { w ->
            val min = (w.durationSec ?: w.endedAt?.let { ((it - w.startedAt) / 1000).toInt() })?.let { Math.round(it / 60.0) }
            append(listOf(w.id, day(w.localDate), text(w.name), time(w.startedAt, w.timeZone), time(w.endedAt, w.timeZone), w.timeZone,
                min?.toString().orEmpty(), num(w.totalVolumeKg), w.totalSets.toString(), w.totalReps.toString(), w.prCount.toString(), text(w.notes))
                .joinToString(",")).append('\n')
        }
    }

    /** [names]: nombre de cada ejercicio por id. La serie se numera dentro de cada ejercicio del entreno. */
    fun setsCsv(sets: List<WorkoutSetEntity>, workouts: Map<String, WorkoutEntity>, names: Map<String, String>): String = buildString {
        append(BOM).append("entrenamiento_id,fecha,entrenamiento,ejercicio,serie,tipo,peso_kg,repeticiones,segundos,rpe,record\n")
        val counter = HashMap<String, Int>()
        sets.forEach { s ->
            val n = (counter[s.workoutExerciseId] ?: 0) + 1; counter[s.workoutExerciseId] = n
            val w = workouts[s.workoutId]
            append(listOf(s.workoutId, day(w?.localDate ?: s.performedOn), text(w?.name), text(names[s.exerciseId] ?: "Ejercicio"), n.toString(),
                SetType.of(s.setType).label, num(s.weightKg), s.reps?.toString().orEmpty(), s.durationSec?.toString().orEmpty(), num(s.rpe),
                if (s.isPr) "sí" else "no").joinToString(",")).append('\n')
        }
    }

    fun bodyWeightCsv(list: List<BodyWeightEntity>): String = buildString {
        append(BOM).append("fecha,peso_kg\n")
        list.forEach { append(day(it.measuredOn)).append(',').append(num(it.weightKg)).append('\n') }
    }

    /** Escribe los tres CSV en la caché (carpeta export/, la única que comparte el FileProvider). */
    suspend fun write(context: Context, db: RupioDatabase): List<File> = withContext(Dispatchers.IO) {
        val a = db.admin()
        val ws = a.exportWorkouts()
        val sets = a.exportSets()
        val names = db.exercises().getMany(sets.map { it.exerciseId }.distinct()).associate { it.id to it.name }
        val dir = File(context.cacheDir, "export").apply { deleteRecursively(); mkdirs() }
        listOf(
            File(dir, WORKOUTS).apply { writeText(workoutsCsv(ws)) },
            File(dir, SETS).apply { writeText(setsCsv(sets, ws.associateBy { it.id }, names)) },
            File(dir, BODY).apply { writeText(bodyWeightCsv(a.exportBodyWeights())) },
        )
    }

    fun authority(context: Context) = "${context.packageName}.files"

    /** Abre el menú de compartir de Android con los archivos (permiso de lectura temporal para la app elegida). */
    fun share(context: Context, files: List<File>) {
        val uris = ArrayList<Uri>(files.map { FileProvider.getUriForFile(context, authority(context), it) })
        val send = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "text/csv"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            putExtra(Intent.EXTRA_SUBJECT, "Mis datos de Rupio")
            putExtra(Intent.EXTRA_TEXT, "Mis entrenamientos, series y peso corporal exportados de Rupio (archivos CSV).")
            clipData = ClipData.newUri(context.contentResolver, "Rupio", uris.first()).also { c -> uris.drop(1).forEach { c.addItem(ClipData.Item(it)) } }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, "Exportar mis datos").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(chooser)
    }
}
