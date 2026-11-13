package pe.pagobox.app

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import pe.pagobox.app.data.local.PagoboxDatabase
import java.io.File
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Comprueba que cada migración manual crea exactamente las tablas e índices que Room espera según
 * el esquema exportado por el compilador para la versión vigente (app/schemas/.../<versión>.json).
 * Si no coinciden, Room fallaría al abrir la base en celulares que actualizan desde una versión anterior.
 */
class MigrationSchemaTest {
    private fun executedSql(migration: Migration): List<String> {
        val statements = mutableListOf<String>()
        val db = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java),
        ) { _, method, args ->
            if (method.name == "execSQL") statements += args!![0] as String
            null
        } as SupportSQLiteDatabase
        migration.migrate(db)
        return statements.map { it.trim() }
    }

    /** Esquema de la versión más reciente exportada por Room (el único que existe en una compilación limpia). */
    private fun latestSchema(): String {
        val dir = File("schemas/pe.pagobox.app.data.local.PagoboxDatabase")
        val latest = dir.listFiles { f -> f.name.endsWith(".json") }
            ?.maxByOrNull { it.nameWithoutExtension.toIntOrNull() ?: -1 }
        return checkNotNull(latest) { "No se encontró el esquema exportado en ${dir.absolutePath}" }.readText()
    }

    private fun assertCreatesTables(migration: Migration, tables: List<String>) {
        val schema = latestSchema()
        val executed = executedSql(migration)
        for (table in tables) {
            val block = schemaBlockFor(schema, table)
            val expected = Regex("\"createSql\"\\s*:\\s*\"(CREATE TABLE IF NOT EXISTS `\\$\\{TABLE_NAME}` [^\"]*)\"")
                .find(block)!!.groupValues[1]
                .replace("\${TABLE_NAME}", table)
            assertTrue(expected in executed, "La migración no crea $table como Room espera:\n$expected\nEjecutado:\n${executed.joinToString("\n")}")
            Regex("\"createSql\"\\s*:\\s*\"(CREATE (?:UNIQUE )?INDEX IF NOT EXISTS `[^\"]*)\"")
                .findAll(block)
                .map { it.groupValues[1].replace("\${TABLE_NAME}", table) }
                .forEach { assertTrue(it in executed, "Falta el índice: $it") }
        }
    }

    @Test
    fun `la migracion 1 a 2 coincide con el esquema de Room`() =
        assertCreatesTables(
            PagoboxDatabase.MIGRATION_1_2,
            listOf("notification_samples", "package_assignments", "source_status", "physical_test_results"),
        )

    @Test
    fun `la migracion 2 a 3 coincide con el esquema de Room`() =
        assertCreatesTables(PagoboxDatabase.MIGRATION_2_3, listOf("unknown_financial_apps"))

    /** Bloque JSON de la entidad [table] dentro del esquema (desde su tableName hasta la siguiente entidad). */
    private fun schemaBlockFor(schema: String, table: String): String {
        val start = schema.indexOf("\"tableName\": \"$table\"")
        assertTrue(start >= 0, "El esquema no contiene la tabla $table")
        val next = schema.indexOf("\"tableName\":", start + 1).let { if (it < 0) schema.length else it }
        return schema.substring(start, next)
    }
}
