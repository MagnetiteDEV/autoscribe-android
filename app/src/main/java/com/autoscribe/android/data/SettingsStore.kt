package com.autoscribe.android.data

import android.content.Context
import android.content.SharedPreferences
import com.autoscribe.android.engine.RunOptions
import com.autoscribe.android.engine.WIREGUARD_PORTS
import org.json.JSONObject

/**
 * Settings + profiles, mirroring desktop config.json
 * (last_settings + saved_profiles), stored in SharedPreferences.
 *
 * Desktop defaults preserved: timeout 25, settle 2, ping host 1.1.1.1,
 * 5 pings, all WireGuard ports on, all toggles off. The Android-only
 * addition is `onlyImported` (default on) and the imported-tunnel set.
 */
data class AppSettings(
    val timeoutSec: Int = 25,
    val settleSec: Int = 2,
    val pingHost: String = "1.1.1.1",
    val pingCount: Int = 5,
    val resume: Boolean = false,
    val randomCities: Boolean = false,
    val chaosMode: Boolean = false,
    val stopOnSuccess: Boolean = false,
    val onlyImported: Boolean = true,
    /** Ports in test order; every listed port is enabled. */
    val portsInOrder: List<Int> = WIREGUARD_PORTS,
    /** Empty = every server (desktop semantics). */
    val selectedKeys: Set<String> = emptySet(),
    val currentProfileName: String = "",
) {
    fun toRunOptions(profileName: String, imported: Set<String>): RunOptions =
        RunOptions(
            selectedKeys = selectedKeys,
            portsInOrder = portsInOrder,
            timeoutSec = timeoutSec,
            settleSec = settleSec,
            pingHost = pingHost,
            pingCount = pingCount,
            resume = resume,
            randomCities = randomCities,
            chaosMode = chaosMode,
            stopOnSuccess = stopOnSuccess,
            profileName = profileName.ifEmpty { "Manual" },
            onlyImported = onlyImported,
            importedTunnelNames = imported,
        )

    fun toJson(): JSONObject = JSONObject().apply {
        put("timeout", timeoutSec)
        put("settle", settleSec)
        put("ping_host", pingHost)
        put("ping_count", pingCount)
        put("resume", resume)
        put("random_cities", randomCities)
        put("chaos_mode", chaosMode)
        put("stop_on_success", stopOnSuccess)
        put("only_imported", onlyImported)
        put("ports", portsInOrder.joinToString(","))
        put("selected", selectedKeys.joinToString("\n"))
        put("profile_name", currentProfileName)
    }

    companion object {
        fun fromJson(obj: JSONObject?): AppSettings {
            if (obj == null) return AppSettings()
            val ports = obj.optString("ports", "")
                .split(",")
                .mapNotNull { it.trim().toIntOrNull() }
                .filter { it in WIREGUARD_PORTS }
            return AppSettings(
                timeoutSec = obj.optInt("timeout", 25),
                settleSec = obj.optInt("settle", 2),
                pingHost = obj.optString("ping_host", "1.1.1.1"),
                pingCount = obj.optInt("ping_count", 5),
                resume = obj.optBoolean("resume", false),
                randomCities = obj.optBoolean("random_cities", false),
                chaosMode = obj.optBoolean("chaos_mode", false),
                stopOnSuccess = obj.optBoolean("stop_on_success", false),
                onlyImported = obj.optBoolean("only_imported", true),
                portsInOrder = ports.ifEmpty { WIREGUARD_PORTS },
                selectedKeys = obj.optString("selected", "")
                    .split("\n").filter { it.isNotEmpty() }.toSet(),
                currentProfileName = obj.optString("profile_name", ""),
            )
        }
    }
}

class SettingsStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var language: String
        get() = prefs.getString(KEY_LANGUAGE, "")?.takeIf { it in Strings.LANGUAGES } ?: ""
        set(value) = prefs.edit().putString(KEY_LANGUAGE, value).apply()

    fun loadSettings(): AppSettings {
        val raw = prefs.getString(KEY_LAST_SETTINGS, null) ?: return AppSettings()
        return try {
            AppSettings.fromJson(JSONObject(raw))
        } catch (e: Exception) {
            AppSettings()
        }
    }

    fun saveSettings(settings: AppSettings) {
        prefs.edit().putString(KEY_LAST_SETTINGS, settings.toJson().toString()).apply()
    }

    // -- Profiles (desktop saved_profiles) --

    fun profileNames(): List<String> {
        val raw = prefs.getString(KEY_PROFILES, null) ?: return emptyList()
        return try {
            val obj = JSONObject(raw)
            obj.keys().asSequence().toList().sorted()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun loadProfile(name: String): AppSettings? {
        val raw = prefs.getString(KEY_PROFILES, null) ?: return null
        return try {
            val obj = JSONObject(raw)
            if (!obj.has(name)) null else AppSettings.fromJson(obj.optJSONObject(name))
        } catch (e: Exception) {
            null
        }
    }

    fun saveProfile(name: String, settings: AppSettings) {
        val obj = try {
            JSONObject(prefs.getString(KEY_PROFILES, null) ?: "{}")
        } catch (e: Exception) {
            JSONObject()
        }
        obj.put(name, settings.toJson())
        prefs.edit().putString(KEY_PROFILES, obj.toString()).apply()
    }

    fun deleteProfile(name: String) {
        val obj = try {
            JSONObject(prefs.getString(KEY_PROFILES, null) ?: "{}")
        } catch (e: Exception) {
            JSONObject()
        }
        obj.remove(name)
        prefs.edit().putString(KEY_PROFILES, obj.toString()).apply()
    }

    // -- Imported tunnels --

    fun loadImported(): Set<String> =
        prefs.getStringSet(KEY_IMPORTED, emptySet())?.toSet() ?: emptySet()

    fun saveImported(names: Set<String>) {
        prefs.edit().putStringSet(KEY_IMPORTED, names.toSet()).apply()
    }

    // -- CSV export counter (desktop numbering: max existing id + 1) --

    fun nextExportNumber(): Int {
        val next = prefs.getInt(KEY_EXPORT_COUNTER, 0) + 1
        prefs.edit().putInt(KEY_EXPORT_COUNTER, next).apply()
        return next
    }

    // -- Imported server list --

    var importedListTimestamp: Long
        get() = prefs.getLong(KEY_IMPORTED_LIST_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_IMPORTED_LIST_AT, value).apply()

    var lastTunnelName: String
        get() = prefs.getString(KEY_LAST_TUNNEL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAST_TUNNEL, value).apply()

    companion object {
        const val PREFS_NAME = "autoscribe"
        const val KEY_LANGUAGE = "language"
        const val KEY_LAST_SETTINGS = "last_settings"
        const val KEY_PROFILES = "saved_profiles"
        const val KEY_IMPORTED = "imported_tunnels"
        const val KEY_EXPORT_COUNTER = "export_counter"
        const val KEY_IMPORTED_LIST_AT = "imported_list_at"
        const val KEY_LAST_TUNNEL = "last_tunnel"
    }
}
