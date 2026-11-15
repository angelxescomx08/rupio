package com.rupio.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.rupio.app.data.local.RupioDatabase
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Quien ya tenía Rupio instalado (base v1) actualiza sin perder datos:
 * crea la base EXACTA de la v1 (desde el esquema exportado), mete datos, y la abre con Room v3.
 * Room valida tabla por tabla que el resultado sea idéntico al esquema actual; si no, la prueba truena.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {
    @Test fun migraDeV1aV3SinPerderDatos() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration_test.db"
        ctx.deleteDatabase(name)
        val schema = JSONObject(File("schemas/com.rupio.app.data.local.RupioDatabase/1.json").readText()).getJSONObject("database")
        assertEquals(1, schema.getInt("version"))

        val path = ctx.getDatabasePath(name).apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(path, null).use { db ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val e = entities.getJSONObject(i)
                val table = e.getString("tableName")
                db.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", table))
                e.optJSONArray("indices")?.let { idx -> for (k in 0 until idx.length()) db.execSQL(idx.getJSONObject(k).getString("createSql").replace("\${TABLE_NAME}", table)) }
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            // Un dato de la v1 que debe sobrevivir.
            db.execSQL("""INSERT INTO routines (id, user_id, name, notes, position, created_at, updated_at, deleted, dirty)
                          VALUES ('r1', 'u1', 'Lunes y jueves', NULL, 0, 1, 1, 0, 0)""")
            db.version = 1
        }

        val room = Room.databaseBuilder(ctx, RupioDatabase::class.java, name)
            .addMigrations(RupioDatabase.MIGRATION_1_2, RupioDatabase.MIGRATION_2_3)
            .allowMainThreadQueries().build()
        val sql = room.openHelper.writableDatabase  // aquí corre la migración y la validación de Room
        assertEquals(3, sql.version)
        sql.query("SELECT name FROM routines WHERE id = 'r1'").use { c -> assertTrue(c.moveToFirst()); assertEquals("Lunes y jueves", c.getString(0)) }
        // Las columnas y tablas nuevas existen y funcionan.
        sql.execSQL("INSERT INTO body_weights (user_id, measured_on, weight_kg, updated_at, deleted, dirty) VALUES ('u1', 20000, 80.5, 1, 0, 1)")
        sql.query("SELECT set_plan FROM routine_exercises LIMIT 1").close()
        room.close()
    }
}
