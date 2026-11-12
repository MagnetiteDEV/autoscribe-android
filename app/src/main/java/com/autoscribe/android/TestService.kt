package com.autoscribe.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.autoscribe.android.engine.Phase
import com.autoscribe.android.engine.PhaseState
import com.autoscribe.android.engine.PingResult
import com.autoscribe.android.engine.TestEngine
import com.autoscribe.android.engine.TestResult
import com.autoscribe.android.engine.TestTask

/**
 * Foreground service owning the engine thread, so a run survives the user
 * leaving the app. One job at a time: a run, a manual connect, a
 * disconnect or a probe. The engine blocks this service's worker thread;
 * all UI updates flow through [EngineState].
 */
class TestService : Service() {

    private var worker: Thread? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        AppDeps.init(this)
        createChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                EngineState.stopFlag.set(true)
                if (worker?.isAlive != true) stopSelf()
                return START_NOT_STICKY
            }
            ACTION_DISCONNECT -> {
                startForeground(NOTIFICATION_ID, buildNotification(null))
                runWorker { doDisconnect() }
                return START_NOT_STICKY
            }
            ACTION_CONNECT_ONE -> {
                val task = EngineState.pendingConnect ?: return START_NOT_STICKY
                if (worker?.isAlive == true) return START_NOT_STICKY
                startForeground(NOTIFICATION_ID, buildNotification(task))
                runWorker { doConnectOne(task) }
                return START_NOT_STICKY
            }
            ACTION_PROBE -> {
                val task = EngineState.pendingProbe ?: return START_NOT_STICKY
                if (worker?.isAlive == true) return START_NOT_STICKY
                startForeground(NOTIFICATION_ID, buildNotification(task))
                runWorker { doProbe(task) }
                return START_NOT_STICKY
            }
            ACTION_START_RUN -> {
                val pending = EngineState.pendingRun ?: return START_NOT_STICKY
                if (worker?.isAlive == true) return START_NOT_STICKY
                EngineState.stopFlag.set(false)
                acquireWakeLock()
                startForeground(NOTIFICATION_ID, buildNotification(null))
                runWorker { doRun(pending) }
                return START_NOT_STICKY
            }
            else -> return START_NOT_STICKY
        }
    }

    override fun onDestroy() {
        EngineState.stopFlag.set(true)
        worker?.interrupt()
        releaseWakeLock()
        super.onDestroy()
    }

    private fun runWorker(block: () -> Unit) {
        worker = Thread(block, "autoscribe-engine").also { it.start() }
    }

    private fun engineListener(): TestEngine.RunListener = object : TestEngine.RunListener {
        override fun log(msg: String, tag: String) {
            EngineState.appendLog(msg, tag)
        }

        override fun phase(phase: Phase, state: PhaseState, secs: Double?) {
            EngineState.setPhase(phase, state, secs)
        }

        override fun beginTest(index: Int, total: Int, executed: Int, task: TestTask) {
            EngineState.beginTest(index, total, executed, task)
            updateNotification(task)
        }

        override fun pingResult(result: PingResult?) {
            EngineState.setLastPing(result)
        }

        override fun resultRecorded(result: TestResult) {
            EngineState.bumpResultsVersion()
        }
    }

    private fun newEngine(): TestEngine {
        val wg = AppDeps.wireGuard
        return TestEngine(
            control = object : TestEngine.TunnelControl {
                override fun up(name: String) = wg.tunnelUp(name)
                override fun down(name: String) = wg.tunnelDown(name)
                override fun vpnActive(): Boolean = wg.vpnActive()
            },
            pinger = object : TestEngine.Pinger {
                override fun ping(host: String, count: Int): PingResult? =
                    AppDeps.pinger.run(host, count)
            },
            sink = object : TestEngine.ResultSink {
                override fun isTested(task: TestTask): Boolean = AppDeps.db.isTested(task)
                override fun record(result: TestResult) {
                    AppDeps.db.record(result)
                }
            },
            listener = engineListener(),
            stopRequested = { EngineState.stopFlag.get() },
        )
    }

    private fun doRun(pending: EngineState.PendingRun) {
        EngineState.setRunning(true)
        EngineState.resetRunStats()
        EngineState.setConnectedTunnel(null)
        try {
            val outcome = newEngine().run(pending.tasks, pending.options)
            EngineState.setCounts(outcome.ok, outcome.fail)
            if (outcome.keptConnection != null) {
                EngineState.setConnectedTunnel(outcome.keptConnection)
                // The kept tunnel is the loop's last task when stop-on-success hit.
                EngineState.currentTask.value?.let {
                    AppDeps.settings.lastTunnelName = it.tunnelName
                }
            }
            EngineState.setOutcome(
                EngineState.RunOutcome(
                    id = System.currentTimeMillis(),
                    keptConnection = outcome.keptConnection,
                    ok = outcome.ok,
                    fail = outcome.fail,
                    executed = outcome.executed,
                    skipped = outcome.skipped,
                    stopped = outcome.stopped,
                ),
            )
            EngineState.appendLog("Job finished.", "HEADER")
        } catch (e: Exception) {
            EngineState.appendLog("Critical Error: ${e.message}", "FAILURE")
        } finally {
            EngineState.setRunning(false)
            EngineState.setBusyLabel(null)
            releaseWakeLock()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun doConnectOne(task: TestTask) {
        EngineState.setBusyLabel("connecting:${task.tunnelName}")
        EngineState.resetPhases()
        EngineState.beginTest(1, 1, 1, task)
        try {
            val timeout = AppDeps.settings.loadSettings().timeoutSec
            val connected = newEngine().connectOne(
                task, timeout, AppDeps.settings.lastTunnelName.ifEmpty { null },
            )
            if (connected) {
                AppDeps.settings.lastTunnelName = task.tunnelName
                EngineState.setConnectedTunnel("${task.pop.displayName} · ${task.route}")
            } else {
                EngineState.setConnectedTunnel(null)
            }
        } catch (e: Exception) {
            EngineState.appendLog("Critical Error: ${e.message}", "FAILURE")
        } finally {
            EngineState.setBusyLabel(null)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun doDisconnect() {
        try {
            val wg = AppDeps.wireGuard
            val last = AppDeps.settings.lastTunnelName
            if (last.isNotEmpty()) wg.tunnelDown(last)
            EngineState.currentTask.value?.let { wg.tunnelDown(it.tunnelName) }
            Thread.sleep(1500)
            val done = !wg.vpnActive()
            EngineState.appendLog(
                if (done) "Disconnected." else "Could not disconnect.",
                if (done) "INFO" else "FAILURE",
            )
            if (done) EngineState.setConnectedTunnel(null)
        } catch (e: Exception) {
            EngineState.appendLog("Critical Error: ${e.message}", "FAILURE")
        } finally {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun doProbe(task: TestTask) {
        EngineState.setBusyLabel("probing:${task.tunnelName}")
        EngineState.resetPhases()
        EngineState.beginTest(1, 1, 1, task)
        try {
            val engine = newEngine()
            val connected = engine.connectOne(
                task, PROBE_TIMEOUT_SEC, AppDeps.settings.lastTunnelName.ifEmpty { null },
            )
            var ping: PingResult? = null
            if (connected) {
                AppDeps.settings.lastTunnelName = task.tunnelName
                ping = try {
                    AppDeps.pinger.run("1.1.1.1", 1)
                } catch (e: Exception) {
                    null
                }
                AppDeps.wireGuard.tunnelDown(task.tunnelName)
                Thread.sleep(1000)
            }
            EngineState.setProbeResult(
                EngineState.ProbeResult(
                    id = System.currentTimeMillis(),
                    task = task,
                    connected = connected,
                    ping = ping,
                ),
            )
        } catch (e: Exception) {
            EngineState.appendLog("Critical Error: ${e.message}", "FAILURE")
        } finally {
            EngineState.setBusyLabel(null)
            EngineState.setConnectedTunnel(null)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    // -- Foreground notification --

    private fun createChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Test runs",
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }
    }

    private fun buildNotification(task: TestTask?): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, TestService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = task?.let { "Testing ${it.pop.displayName} · ${it.route}" }
            ?: "AutoScribe is working"
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("AutoScribe")
            .setContentText(text)
            .setContentIntent(openApp)
            .addAction(R.drawable.ic_notification, "Stop", stop)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun updateNotification(task: TestTask) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(task))
    }

    private fun acquireWakeLock() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AutoScribe:run").apply {
                acquire(12 * 60 * 60 * 1000L) // 12h cap; released when the run ends.
            }
        } catch (e: Exception) {
            wakeLock = null
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.takeIf { it.isHeld }?.release()
        } catch (e: Exception) {
            // Already released.
        }
        wakeLock = null
    }

    companion object {
        const val ACTION_START_RUN = "com.autoscribe.android.START_RUN"
        const val ACTION_STOP = "com.autoscribe.android.STOP"
        const val ACTION_CONNECT_ONE = "com.autoscribe.android.CONNECT_ONE"
        const val ACTION_DISCONNECT = "com.autoscribe.android.DISCONNECT"
        const val ACTION_PROBE = "com.autoscribe.android.PROBE"
        const val CHANNEL_ID = "autoscribe_run"
        const val NOTIFICATION_ID = 1
        const val PROBE_TIMEOUT_SEC = 15
    }
}
