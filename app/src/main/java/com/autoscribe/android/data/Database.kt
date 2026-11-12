package com.autoscribe.android.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.autoscribe.android.engine.TestResult
import com.autoscribe.android.engine.TestTask

/**
 * Results database. Schema, UNIQUE constraint and INSERT OR REPLACE
 * semantics are identical to the desktop DatabaseManager, so a CSV
 * exported here has the same columns in the same order.
 *
 * All methods are synchronized: the engine thread writes while the UI
 * thread reads.
 */
class ResultsDatabase(context: Context) :
    SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    companion object {
        const val DB_NAME = "windscribe_results.db"
        const val DB_VERSION = 1
        const val TABLE = "tests"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                location TEXT,
                city TEXT,
                country TEXT,
                protocol TEXT,
                port INTEGER,
                status TEXT,
                duration REAL,
                ping REAL,
                packet_loss REAL,
                profile_name TEXT,
                timestamp TEXT,
                UNIQUE(location, city, country, protocol, port)
            )
            """.trimIndent(),
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // v1: no migrations yet.
    }

    @Synchronized
    fun record(result: TestResult): Boolean {
        return try {
            val values = ContentValues().apply {
                put("location", result.location)
                put("city", result.city)
                put("country", result.country)
                put("protocol", result.protocol)
                put("port", result.port)
                put("status", result.status)
                put("duration", result.duration)
                put("ping", result.ping)
                put("packet_loss", result.packetLoss)
                put("profile_name", result.profileName)
                put("timestamp", result.timestamp)
            }
            writableDatabase.insertWithOnConflict(
                TABLE, null, values, SQLiteDatabase.CONFLICT_REPLACE,
            )
            true
        } catch (e: Exception) {
            false
        }
    }

    @Synchronized
    fun isTested(task: TestTask): Boolean {
        val cursor = readableDatabase.query(
            TABLE, arrayOf("1"),
            "location=? AND city=? AND country=? AND protocol=? AND port=?",
            arrayOf(task.pop.node, task.pop.city, task.pop.country, "wireguard", task.port.toString()),
            null, null, null, "1",
        )
        cursor.use { return it.moveToFirst() }
    }

    @Synchronized
    fun rowCount(): Int {
        readableDatabase.rawQuery("SELECT COUNT(*) FROM $TABLE", null).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    @Synchronized
    fun fetchAll(): List<TestResult> {
        val out = mutableListOf<TestResult>()
        readableDatabase.query(
            TABLE, null, null, null, null, null, "id ASC",
        ).use { cursor ->
            val iId = cursor.getColumnIndexOrThrow("id")
            val iCity = cursor.getColumnIndexOrThrow("city")
            val iLoc = cursor.getColumnIndexOrThrow("location")
            val iCountry = cursor.getColumnIndexOrThrow("country")
            val iProto = cursor.getColumnIndexOrThrow("protocol")
            val iPort = cursor.getColumnIndexOrThrow("port")
            val iStatus = cursor.getColumnIndexOrThrow("status")
            val iDur = cursor.getColumnIndexOrThrow("duration")
            val iPing = cursor.getColumnIndexOrThrow("ping")
            val iLoss = cursor.getColumnIndexOrThrow("packet_loss")
            val iProfile = cursor.getColumnIndexOrThrow("profile_name")
            val iTs = cursor.getColumnIndexOrThrow("timestamp")
            while (cursor.moveToNext()) {
                out.add(
                    TestResult(
                        id = cursor.getLong(iId),
                        city = cursor.getString(iCity) ?: "",
                        location = cursor.getString(iLoc) ?: "",
                        country = cursor.getString(iCountry) ?: "",
                        protocol = cursor.getString(iProto) ?: "wireguard",
                        port = cursor.getInt(iPort),
                        status = cursor.getString(iStatus) ?: "",
                        duration = cursor.getDouble(iDur),
                        ping = if (cursor.isNull(iPing)) null else cursor.getDouble(iPing),
                        packetLoss = if (cursor.isNull(iLoss)) null else cursor.getDouble(iLoss),
                        profileName = cursor.getString(iProfile) ?: "",
                        timestamp = cursor.getString(iTs) ?: "",
                    ),
                )
            }
        }
        return out
    }

    @Synchronized
    fun clear() {
        writableDatabase.delete(TABLE, null, null)
    }
}
