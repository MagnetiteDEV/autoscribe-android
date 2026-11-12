package com.autoscribe.android.engine

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * CSV export. Mirrors desktop DatabaseManager.export_to_csv():
 * same column order, same quoting (QUOTE_MINIMAL: quote only fields that
 * contain a comma, quote or newline, doubling embedded quotes), same file
 * name pattern `{n}_{profile}_{yyyy-MM-dd_HH-mm-ss}.csv`.
 */
object CsvExport {
    val COLUMNS: List<String> = listOf(
        "city", "location", "country", "protocol", "port", "status",
        "duration", "ping", "packet_loss", "profile_name", "timestamp",
    )

    fun quote(field: String): String {
        return if (field.contains(',') || field.contains('"') || field.contains('\n') || field.contains('\r')) {
            "\"" + field.replace("\"", "\"\"") + "\""
        } else {
            field
        }
    }

    private fun fmt(value: Double?): String = value?.toString() ?: ""

    fun buildCsv(results: List<TestResult>): String {
        val sb = StringBuilder()
        sb.append(COLUMNS.joinToString(",")).append("\n")
        for (r in results) {
            val fields = listOf(
                r.city, r.location, r.country, r.protocol, r.port.toString(), r.status,
                r.duration.toString(), fmt(r.ping), fmt(r.packetLoss), r.profileName, r.timestamp,
            )
            sb.append(fields.joinToString(",") { quote(it) }).append("\n")
        }
        return sb.toString()
    }

    /** Desktop safe_profile: keep alphanumerics, space, '-' and '_'; spaces -> '_'. */
    fun safeProfileName(profileName: String): String {
        val kept = profileName.filter { it.isLetterOrDigit() || it == ' ' || it == '-' || it == '_' }.trim()
        return kept.replace(' ', '_').ifEmpty { "Manual" }
    }

    fun fileName(exportNumber: Int, profileName: String, now: Date = Date()): String {
        val ts = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(now)
        return "${exportNumber}_${safeProfileName(profileName)}_$ts.csv"
    }

    /** Desktop DB timestamp format. */
    fun nowTimestamp(now: Date = Date()): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(now)
}
