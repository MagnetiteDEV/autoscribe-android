package com.autoscribe.android.data

import android.content.Context
import com.autoscribe.android.engine.Pop
import org.json.JSONObject
import java.io.File

/**
 * Parses a Windscribe `locations.json` file (the same format the desktop
 * app downloads from api.windscribe.com/WebsiteStatus and the same file
 * the desktop "Update list" button writes).
 *
 * The desktop download needs the website's auth token, which this app
 * deliberately does not ship; instead the user imports a locations.json
 * file (e.g. one refreshed by the desktop app). Flattening rules are the
 * desktop LocationManager rules, identical to the extraction script.
 */
object ServerListImport {
    const val FILE_NAME = "locations_imported.json"

    fun parseBytes(bytes: ByteArray): List<Pop> {
        val root = JSONObject(String(bytes, Charsets.UTF_8))
        val locations = root.optJSONObject("data")?.optJSONObject("locations")
            ?: throw IllegalArgumentException("the file did not contain a server list")
        val pops = mutableListOf<Pop>()
        val keys = locations.keys()
        while (keys.hasNext()) {
            val entry = locations.optJSONObject(keys.next()) ?: continue
            val country = (entry.optString("name", "Unknown")).trim()
            val countryCode = entry.optString("country_code", "").trim()
            val continent = (entry.optString("continent", "Unknown")).trim()
            val popsArray = entry.optJSONArray("pops") ?: continue
            for (i in 0 until popsArray.length()) {
                val pop = popsArray.optJSONObject(i) ?: continue
                if (pop.optInt("status", 1) != 1) continue
                val name = pop.optString("name", "").trim()
                if (name.isEmpty()) continue
                val (city, node) = if (" - " in name) {
                    val parts = name.split(" - ", limit = 2)
                    parts[0].trim() to parts[1].trim()
                } else {
                    name to ""
                }
                pops.add(
                    Pop(
                        country = country,
                        countryCode = countryCode,
                        continent = continent,
                        city = city,
                        node = node,
                        premium = pop.optInt("is_premium", 0) != 0,
                        p2p = pop.optInt("is_p2p", 0) != 0,
                    ),
                )
            }
        }
        if (pops.isEmpty()) {
            throw IllegalArgumentException("the server list has no servers in it")
        }
        return pops.sortedWith(compareBy({ it.country }, { it.city }, { it.node }))
    }

    fun saveImported(context: Context, bytes: ByteArray) {
        File(context.filesDir, FILE_NAME).writeBytes(bytes)
    }

    fun loadImported(context: Context): List<Pop>? {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return null
        return try {
            parseBytes(file.readBytes())
        } catch (e: Exception) {
            null
        }
    }
}
