package com.autoscribe.android.engine

/**
 * Parses `ping` output exactly like the desktop PingRunner:
 * only lines containing "ttl" are replies (this keeps the Windows-style
 * Minimum/Maximum/Average summary line out of the numbers), and the
 * latency is the number before "ms" after '=' or '<'.
 *
 * Android's ping prints e.g.:
 *   64 bytes from 1.1.1.1: icmp_seq=1 ttl=57 time=23.4 ms
 * Windows prints e.g.:
 *   Reply from 1.1.1.1: bytes=32 time=23ms TTL=57
 * Both are handled. Decimal commas (some locales) are normalised.
 */
object PingParser {
    private val timeRe = Regex("""[=<]\s*(\d+(?:[.,]\d+)?)\s*ms""", RegexOption.IGNORE_CASE)

    /** Per-reply latencies in ms, in order. Empty when there were no replies. */
    fun parse(output: String): List<Double> {
        val times = mutableListOf<Double>()
        for (line in output.lines()) {
            if (!line.contains("ttl", ignoreCase = true)) continue
            val m = timeRe.find(line) ?: continue
            times.add(m.groupValues[1].replace(',', '.').toDouble())
        }
        return times
    }

    /** Builds a [PingResult] from reply times, or null when there were none. */
    fun summarize(times: List<Double>, count: Int, method: String): PingResult? {
        if (times.isEmpty()) return null
        return PingResult(
            avg = times.average(),
            min = times.min(),
            max = times.max(),
            loss = (count - times.size).toDouble() / count * 100.0,
            method = method,
        )
    }
}
