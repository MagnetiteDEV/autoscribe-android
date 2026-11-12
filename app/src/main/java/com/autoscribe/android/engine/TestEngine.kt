package com.autoscribe.android.engine

import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToLong

/**
 * The test loop, ported from desktop WindscribeWorker._run().
 *
 * Differences from the desktop worker, and only these:
 *  - `windscribe-cli` calls are replaced by [TunnelControl] (WireGuard app
 *    broadcasts) and [vpnActive] replaces parsing `status` output.
 *  - There is no INVALID_LOC: a tunnel name that does not exist in the
 *    WireGuard app simply never brings the VPN up, so the test times out
 *    as FAILURE.
 *  - Everything else is identical: disconnect -> connect -> settle -> ping,
 *    resume skipping, stop-on-success keeping the connection, "stop while
 *    connecting records nothing", FAILURE duration 0.0, SUCCESS even when
 *    ping returns no replies, 2s pause after disconnecting each test.
 *
 * This class blocks the calling thread (the desktop worker is a thread
 * too); the foreground service owns that thread. All UI contact goes
 * through [RunListener], mirroring the desktop app.post() queue.
 * [stopRequested] is polled between steps, like the desktop stop_event.
 */
class TestEngine(
    private val control: TunnelControl,
    private val pinger: Pinger,
    private val sink: ResultSink,
    private val listener: RunListener,
    private val stopRequested: () -> Boolean,
) {
    interface TunnelControl {
        fun up(name: String)
        fun down(name: String)
        fun vpnActive(): Boolean
    }

    interface Pinger {
        fun ping(host: String, count: Int): PingResult?
    }

    interface ResultSink {
        fun isTested(task: TestTask): Boolean
        fun record(result: TestResult)
    }

    interface RunListener {
        fun log(msg: String, tag: String = "INFO")
        fun phase(phase: Phase, state: PhaseState, secs: Double? = null)
        fun beginTest(index: Int, total: Int, executed: Int, task: TestTask)
        fun pingResult(result: PingResult?)
        fun resultRecorded(result: TestResult)
    }

    data class Outcome(
        val keptConnection: String?,
        val ok: Int,
        val fail: Int,
        val executed: Int,
        val skipped: Int,
        val stopped: Boolean,
    )

    /** Sleeps in slices so a stop request shortens the wait. Returns false if stopped. */
    private fun sleepInterruptible(ms: Long): Boolean {
        var remaining = ms
        while (remaining > 0) {
            if (stopRequested()) return false
            val slice = minOf(100L, remaining)
            try {
                Thread.sleep(slice)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                return false
            }
            remaining -= slice
        }
        return true
    }

    /** Desktop ensure_disconnected(): up to 5 rounds of DOWN + 3s waits. */
    fun ensureDisconnected(lastTunnel: String?, fallbackTunnel: String): Boolean {
        repeat(5) {
            if (!control.vpnActive()) return true
            val name = lastTunnel ?: fallbackTunnel
            control.down(name)
            if (!sleepInterruptible(3000)) return !control.vpnActive()
        }
        return !control.vpnActive()
    }

    private fun waitForConnection(startNanos: Long, timeoutSec: Int): Pair<Boolean, Double> {
        val deadline = startNanos + timeoutSec * 1_000_000_000L
        while (System.nanoTime() < deadline) {
            if (control.vpnActive()) {
                val duration = (System.nanoTime() - startNanos) / 1_000_000_000.0
                return true to duration
            }
            if (stopRequested()) return false to 0.0
            try {
                Thread.sleep(1000)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                return false to 0.0
            }
        }
        return false to 0.0
    }

    fun run(tasks: List<TestTask>, options: RunOptions): Outcome {
        var keptConnection: String? = null
        var ok = 0
        var fail = 0
        var executed = 0
        var skipped = 0
        var lastTunnel: String? = null

        listener.log(
            "Queued ${tasks.size} tests (WireGuard only)",
            "HEADER",
        )
        for ((index, task) in tasks.withIndex()) {
            val i = index + 1
            if (stopRequested()) break

            if (options.resume && sink.isTested(task)) {
                skipped++
                continue
            }
            executed++
            listener.beginTest(i, tasks.size, executed, task)
            listener.log("[${i}/${tasks.size}] ${task.pop.displayName} (${task.pop.country}) via ${task.route}", "HEADER")

            // -- Disconnect phase --
            listener.phase(Phase.DISCONNECT, PhaseState.ACTIVE)
            val discStart = System.nanoTime()
            if (!ensureDisconnected(lastTunnel, task.tunnelName)) {
                listener.phase(Phase.DISCONNECT, PhaseState.FAIL)
                listener.log("Could not disconnect, skipping this test.", "FAILURE")
                continue
            }
            listener.phase(
                Phase.DISCONNECT, PhaseState.DONE,
                (System.nanoTime() - discStart) / 1_000_000_000.0,
            )
            if (stopRequested()) break

            // -- Connect phase --
            listener.phase(Phase.CONNECT, PhaseState.ACTIVE)
            val startNanos = System.nanoTime()
            control.up(task.tunnelName)
            lastTunnel = task.tunnelName
            sleepInterruptible(1000)
            val (connected, duration) = waitForConnection(startNanos, options.timeoutSec)

            // Stopped while connecting: undo and leave, don't record a bogus failure.
            if (stopRequested() && !connected) {
                control.down(task.tunnelName)
                listener.log("Stopped while connecting; this test was not recorded.", "WARN")
                break
            }

            var pingResult: PingResult? = null
            var status = "FAILURE"
            if (connected) {
                status = "SUCCESS"
                ok++
                listener.phase(Phase.CONNECT, PhaseState.DONE, duration)
                listener.log("Connected in ${"%.2f".format(Locale.US, duration)}s", "SUCCESS")

                if (options.settleSec > 0) {
                    listener.phase(Phase.SETTLE, PhaseState.ACTIVE)
                    sleepInterruptible(options.settleSec * 1000L)
                    listener.phase(Phase.SETTLE, PhaseState.DONE, options.settleSec.toDouble())
                } else {
                    listener.phase(Phase.SETTLE, PhaseState.SKIP)
                }

                listener.phase(Phase.PING, PhaseState.ACTIVE)
                val pingStart = System.nanoTime()
                pingResult = try {
                    pinger.ping(options.pingHost, options.pingCount)
                } catch (e: Exception) {
                    listener.log("Ping failed: ${e.message}", "WARN")
                    null
                }
                listener.phase(
                    Phase.PING,
                    if (pingResult != null) PhaseState.DONE else PhaseState.FAIL,
                    (System.nanoTime() - pingStart) / 1_000_000_000.0,
                )
                listener.pingResult(pingResult)
                if (pingResult != null) {
                    listener.log(
                        "Ping avg ${"%.0f".format(Locale.US, pingResult.avg)} ms " +
                            "(min ${"%.0f".format(Locale.US, pingResult.min)} / " +
                            "max ${"%.0f".format(Locale.US, pingResult.max)}), " +
                            "loss ${"%.0f".format(Locale.US, pingResult.loss)}% [${pingResult.method}]",
                        "INFO",
                    )
                } else {
                    listener.log("Ping failed: no replies", "WARN")
                }
            } else {
                fail++
                listener.phase(Phase.CONNECT, PhaseState.FAIL, options.timeoutSec.toDouble())
                listener.log("Timed out on ${task.route}", "FAILURE")
            }

            val result = TestResult(
                city = task.pop.city,
                location = task.pop.node,
                country = task.pop.country,
                protocol = "wireguard",
                port = task.port,
                status = status,
                duration = ((duration * 100).roundToLong()) / 100.0,
                ping = pingResult?.avg,
                packetLoss = pingResult?.loss,
                profileName = options.profileName,
                timestamp = CsvExport.nowTimestamp(),
            )
            sink.record(result)
            listener.resultRecorded(result)

            if (connected && options.stopOnSuccess) {
                keptConnection = "${task.pop.displayName} · ${task.route}"
                listener.log("Stop on success: staying connected to $keptConnection.", "SUCCESS")
                break
            }

            control.down(task.tunnelName)
            sleepInterruptible(2000)
        }

        return Outcome(
            keptConnection = keptConnection,
            ok = ok,
            fail = fail,
            executed = executed,
            skipped = skipped,
            stopped = stopRequested(),
        )
    }

    /** Connects to a single route picked from the results (desktop _connect_one). */
    fun connectOne(task: TestTask, timeoutSec: Int, lastTunnel: String?): Boolean {
        listener.log("Connecting to ${task.pop.displayName} via ${task.route}", "HEADER")
        listener.phase(Phase.DISCONNECT, PhaseState.ACTIVE)
        val discStart = System.nanoTime()
        if (!ensureDisconnected(lastTunnel, task.tunnelName)) {
            listener.phase(Phase.DISCONNECT, PhaseState.FAIL)
            listener.log("Could not disconnect the current session first.", "FAILURE")
            return false
        }
        listener.phase(
            Phase.DISCONNECT, PhaseState.DONE,
            (System.nanoTime() - discStart) / 1_000_000_000.0,
        )
        listener.phase(Phase.CONNECT, PhaseState.ACTIVE)
        val startNanos = System.nanoTime()
        control.up(task.tunnelName)
        sleepInterruptible(1000)
        val (connected, duration) = waitForConnection(startNanos, timeoutSec)
        if (connected) {
            listener.phase(Phase.CONNECT, PhaseState.DONE, duration)
            listener.log("Connected to ${task.pop.displayName} via ${task.route} in ${"%.1f".format(Locale.US, duration)}s", "SUCCESS")
        } else {
            listener.phase(Phase.CONNECT, PhaseState.FAIL)
            listener.log("Could not connect to ${task.pop.displayName} via ${task.route}", "FAILURE")
        }
        return connected
    }

    companion object {
        fun stopFlag(): AtomicBoolean = AtomicBoolean(false)
    }
}
