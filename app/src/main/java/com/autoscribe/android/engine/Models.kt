package com.autoscribe.android.engine

/**
 * Pure models shared by the engine, the database and the UI.
 * No Android imports: this file (and the other files in this package
 * except [PingRunner] / [WireGuardController]) compiles standalone
 * and is covered by tests/PureLogicTest.kt.
 */

/** One Windscribe server (pop), flattened exactly like the desktop app. */
data class Pop(
    val country: String,
    val countryCode: String,
    val continent: String,
    val city: String,
    /** Server name inside the city ("Tulip" in "Amsterdam - Tulip"). Never empty in practice. */
    val node: String,
    val premium: Boolean,
    val p2p: Boolean,
) {
    /** Desktop loc_key(): "country|city|location". */
    val key: String get() = "$country|$city|$node"

    /** Desktop loc_name(): "City – Node". */
    val displayName: String get() = if (node.isEmpty()) city else "$city – $node"
}

/** Desktop DEFAULT_PROTOCOLS["wireguard"], in desktop order. */
val WIREGUARD_PORTS: List<Int> = listOf(443, 80, 53, 123, 1194, 65142)

/** One test: a server on one WireGuard port. */
data class TestTask(val pop: Pop, val port: Int) {
    val tunnelName: String get() = tunnelNameFor(pop, port)

    /** Same "wireguard:port" label as [TestResult.route], for log lines. */
    val route: String get() = "wireguard:$port"
}

/** A stored test result. Mirrors the desktop `tests` table, column for column. */
data class TestResult(
    val id: Long = 0,
    val city: String,
    val location: String,
    val country: String,
    val protocol: String = "wireguard",
    val port: Int,
    /** SUCCESS, FAILURE or INVALID_LOC (desktop status strings). */
    val status: String,
    /** Connect duration in seconds, rounded to 2dp; 0.0 when the connect timed out. */
    val duration: Double,
    val ping: Double?,
    val packetLoss: Double?,
    val profileName: String,
    /** "yyyy-MM-dd HH:mm:ss", local time — same format as the desktop app. */
    val timestamp: String,
) {
    val displayName: String get() = if (location.isEmpty()) city else "$city – $location"
    val route: String get() = "wireguard:$port"
    val isSuccess: Boolean get() = status == "SUCCESS"
}

/** Options for one run. Mirrors the desktop `opts` dict + work plan. */
data class RunOptions(
    /** Empty = every server (desktop semantics: nothing ticked means all). */
    val selectedKeys: Set<String> = emptySet(),
    val portsInOrder: List<Int> = WIREGUARD_PORTS,
    val timeoutSec: Int = 25,
    val settleSec: Int = 2,
    val pingHost: String = "1.1.1.1",
    val pingCount: Int = 5,
    val resume: Boolean = false,
    val randomCities: Boolean = false,
    val chaosMode: Boolean = false,
    val stopOnSuccess: Boolean = false,
    val profileName: String = "Manual",
    /** Android-only: only test tunnels the user marked as imported. */
    val onlyImported: Boolean = true,
    val importedTunnelNames: Set<String> = emptySet(),
)

/** Outcome of one ping measurement. */
data class PingResult(
    val avg: Double,
    val min: Double,
    val max: Double,
    /** 0..100. */
    val loss: Double,
    /** "ICMP" for the system ping binary, "TCP" for the socket fallback. */
    val method: String,
)

/** The four desktop phases, in order. */
enum class Phase { DISCONNECT, CONNECT, SETTLE, PING }

enum class PhaseState { IDLE, ACTIVE, DONE, FAIL, SKIP }

/** Region filter, mirroring desktop matches_region(). */
fun matchesRegion(pop: Pop, region: String): Boolean = when (region) {
    "All" -> true
    "USA" -> pop.country.startsWith("US ")
    else -> pop.continent == region
}

val REGIONS: List<String> = listOf(
    "All", "USA", "Europe", "Asia", "Africa", "Oceania", "Antarctica",
    "North America", "South America",
)

/** Desktop ping thresholds: <50 fast, <100 medium, else slow. */
fun pingQuality(ping: Double?): String = when {
    ping == null -> "slow"
    ping < 50 -> "fast"
    ping < 100 -> "medium"
    else -> "slow"
}

/** Desktop rate_color(): >=0.75 good, >=0.45 warn, else bad. */
fun rateQuality(rate: Double): String = when {
    rate >= 0.75 -> "good"
    rate >= 0.45 -> "warn"
    else -> "bad"
}
