package com.autoscribe.android.engine

import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit

/**
 * Runs a ping measurement through the active tunnel.
 *
 * Primary method: the system `ping` binary (ProcessBuilder), exactly like
 * the desktop PingRunner — same arguments, same output parsing, same
 * "no replies" semantics (returns null, which the engine records as a
 * connected test with no ping value).
 *
 * Fallback: some devices restrict the ping binary for apps. When the binary
 * cannot be started, or exits with no output at all / a permission error,
 * a TCP handshake "ping" to port 443 is used instead and labelled TCP, so a
 * result is never silently mislabelled as ICMP.
 */
class PingRunner {
    fun run(host: String, count: Int, replyTimeoutMs: Int = 2000): PingResult? {
        return try {
            runNative(host, count, replyTimeoutMs)
        } catch (e: IOException) {
            // ping binary missing: fall back to TCP.
            runTcp(host, count, replyTimeoutMs)
        } catch (e: Exception) {
            null
        }
    }

    private fun runNative(host: String, count: Int, replyTimeoutMs: Int): PingResult? {
        val waitSecs = maxOf(1, replyTimeoutMs / 1000)
        val process = ProcessBuilder("ping", "-c", count.toString(), "-W", waitSecs.toString(), host)
            .redirectErrorStream(true)
            .start()
        val budgetSecs = (count * (replyTimeoutMs / 1000.0 + 1) + 5).toLong()
        val finished = process.waitFor(budgetSecs, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            return null
        }
        val output = try {
            process.inputStream.bufferedReader(Charsets.UTF_8).readText()
        } catch (e: IOException) {
            ""
        }
        val times = PingParser.parse(output)
        if (times.isNotEmpty()) {
            return PingParser.summarize(times, count, "ICMP")
        }
        // No replies. Distinguish "genuine silence through the tunnel" from
        // "the binary could not run at all": an empty / permission-denied
        // output means the binary is unusable on this device.
        val lowered = output.lowercase()
        val binaryUnusable = output.isBlank() ||
            "permission" in lowered ||
            "not permitted" in lowered ||
            "operation not allowed" in lowered
        return if (binaryUnusable) runTcp(host, count, replyTimeoutMs) else null
    }

    private fun runTcp(host: String, count: Int, replyTimeoutMs: Int): PingResult? {
        val address: InetAddress = try {
            InetAddress.getByName(host)
        } catch (e: Exception) {
            return null
        }
        val times = mutableListOf<Double>()
        repeat(count) {
            val start = System.nanoTime()
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(address, 443), replyTimeoutMs)
                }
                times.add((System.nanoTime() - start) / 1_000_000.0)
            } catch (e: IOException) {
                // Miss: counts towards loss, like a lost ICMP reply.
            } catch (e: Exception) {
                // Miss.
            }
        }
        return PingParser.summarize(times, count, "TCP")
    }
}
