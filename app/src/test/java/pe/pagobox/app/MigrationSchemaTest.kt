package pe.pagobox.app

import androidx.sqlite.db.SupportSQLiteDatabase
import pe.pagobox.app.data.local.PagoboxDatabase
import java.io.File
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Comprueba que la migración manual 1 -> 2 crea exactamente las tablas e índices que Room
 * espera según el esquema exportado por el compilador (app/schemas/.../2.json).
 * Si no coinciden, Room fallaría al abrir la base en celulares que actualizan desde la v1.
 */
class MigrationSchemaTest {
    private fun executedSql(): List<String> {
        val statements = mutableListOf<String>()
        val db = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java),
        ) { _, method, args ->
            if (method.name == "execSQL") statements += args!![0] as String
            null
        } as SupportSQLiteDatabase
        PagoboxDatabase.MIGRATION_1_2.migrate(db)
        return statements
    }

    private fun schemaV2(): String {
        val file = File("schemas/pe.pagobox.app.data.local.PagoboxDatabase/2.json")
        assertTrue(file.exists(), "No se encontró el esquema exportado: ${file.absolutePath}")
        return file.readText()
    }

    @Test
    fun `la migracion 1 a 2 coincide con el esquema de Room`() {
        val schema = schemaV2()
        val executed = executedSql().map { it.trim() }
        val newTables = listOf("notification_samples", "package_assignments", "source_status", "physical_test_results")
        for (table in newTables) {
            val expected = Regex("\"createSql\"\\s*:\\s*\"(CREATE TABLE IF NOT EXISTS `\\$\\{TABLE_NAME}` [^\"]*)\"")
                .findAll(schema)
                .map { it.groupValues[1] }
                .first { schemaBlockFor(schema, table).contains(it) }
                .replace("\${TABLE_NAME}", table)
            assertTrue(expected in executed, "La migración no crea $table como Room espera:\n$expected\nEjecutado:\n${executed.joinToString("\n")}")
        }
        val expectedIndices = Regex("\"createSql\"\\s*:\\s*\"(CREATE INDEX IF NOT EXISTS `index_notification_samples_[^\"]*)\"")
            .findAll(schema).map { it.groupValues[1].replace("\${TABLE_NAME}", "notification_samples") }.toList()
        assertTrue(expectedIndices.isNotEmpty(), "El esquema debería declarar índices en notification_samples")
        expectedIndices.forEach { assertTrue(it in executed, "Falta el índice: $it") }
    }

    /** Bloque JSON de la entidad [table] dentro del esquema (desde su tableName hasta la siguiente entidad). */
    private fun schemaBlockFor(schema: String, table: String): String {
        val start = schema.indexOf("\"tableName\": \"$table\"")
        assertTrue(start >= 0, "El esquema no contiene la tabla $table")
        val next = schema.indexOf("\"tableName\":", start + 1).let { if (it < 0) schema.length else it }
        return schema.substring(start, next)
    }
}
