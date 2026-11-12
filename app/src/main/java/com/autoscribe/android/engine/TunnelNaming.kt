package com.autoscribe.android.engine

/**
 * Tunnel naming: the contract between this app, the Windscribe config
 * generator and the WireGuard app.
 *
 * The WireGuard app names an imported tunnel after the config file name
 * (without ".conf"). So the user saves each generated config as
 * `<tunnelName>.conf`, and this app can then bring that exact tunnel up
 * with a SET_TUNNEL_UP broadcast.
 *
 * Format: `WS-<City>-<Node>-<port>`, e.g. `WS-Amsterdam-Tulip-443`.
 * Sanitising keeps only ASCII letters, digits and hyphens: every city/node
 * in the embedded list is already ASCII (spaces and one hyphen), so names
 * stay human-readable and are always valid file names. The country is not
 * in the name because no city name appears in two countries in the list
 * (verified by the extraction script's data: (city, node) pairs are unique).
 */
object TunnelNaming {
    fun sanitize(segment: String): String {
        val out = StringBuilder()
        var lastWasDash = false
        for (ch in segment) {
            when {
                ch.isLetterOrDigit() && ch.code < 128 -> {
                    out.append(ch)
                    lastWasDash = false
                }
                ch == ' ' || ch == '-' || ch == '_' -> {
                    if (out.isNotEmpty() && !lastWasDash) {
                        out.append('-')
                        lastWasDash = true
                    }
                }
                // Drop anything else (accents, punctuation): names must stay
                // identical between the file name and the broadcast extra.
            }
        }
        return out.toString().trim('-')
    }

    fun tunnelNameFor(pop: Pop, port: Int): String {
        val city = sanitize(pop.city)
        val node = sanitize(pop.node)
        val middle = if (node.isEmpty()) city else "$city-$node"
        return "WS-$middle-$port"
    }

    /** File name the user saves the generated config under. */
    fun configFileName(pop: Pop, port: Int): String = "${tunnelNameFor(pop, port)}.conf"
}

/** Convenience top-level, used by [TestTask]. */
fun tunnelNameFor(pop: Pop, port: Int): String = TunnelNaming.tunnelNameFor(pop, port)
