package com.autoscribe.android

import com.autoscribe.android.engine.Phase
import com.autoscribe.android.engine.PhaseState
import com.autoscribe.android.engine.PingResult
import com.autoscribe.android.engine.RunOptions
import com.autoscribe.android.engine.TestTask
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Shared run state between the foreground service (writer, engine thread)
 * and the UI (reader). StateFlow writes are thread-safe, which is what
 * makes the desktop app.post() pattern unnecessary here.
 */
object EngineState {
    data class LogLine(val text: String, val tag: String)

    data class RunOutcome(
        val id: Long,
        val keptConnection: String?,
        val ok: Int,
        val fail: Int,
        val executed: Int,
        val skipped: Int,
        val stopped: Boolean,
    )

    data class ProbeResult(
        val id: Long,
        val task: TestTask,
        val connected: Boolean,
        val ping: PingResult?,
    )

    data class PendingRun(val tasks: List<TestTask>, val options: RunOptions)

    /** Set by the ViewModel immediately before starting the service. */
    @Volatile
    var pendingRun: PendingRun? = null

    @Volatile
    var pendingConnect: TestTask? = null

    @Volatile
    var pendingProbe: TestTask? = null

    /** Mirrors the desktop stop_event. */
    val stopFlag: AtomicBoolean = AtomicBoolean(false)

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _busyLabel = MutableStateFlow<String?>(null)
    /** Non-null while a manual connect / probe is in progress ("Connecting…"). */
    val busyLabel: StateFlow<String?> = _busyLabel.asStateFlow()

    private val _phaseStates = MutableStateFlow<Map<Phase, PhaseState>>(
        Phase.entries.associateWith { PhaseState.IDLE },
    )
    val phaseStates: StateFlow<Map<Phase, PhaseState>> = _phaseStates.asStateFlow()

    private val _phaseSecs = MutableStateFlow<Map<Phase, Double?>>(emptyMap())
    val phaseSecs: StateFlow<Map<Phase, Double?>> = _phaseSecs.asStateFlow()

    private val _currentTask = MutableStateFlow<TestTask?>(null)
    val currentTask: StateFlow<TestTask?> = _currentTask.asStateFlow()

    private val _progressIndex = MutableStateFlow(0)
    val progressIndex: StateFlow<Int> = _progressIndex.asStateFlow()

    private val _progressTotal = MutableStateFlow(0)
    val progressTotal: StateFlow<Int> = _progressTotal.asStateFlow()

    private val _executed = MutableStateFlow(0)
    val executed: StateFlow<Int> = _executed.asStateFlow()

    private val _okCount = MutableStateFlow(0)
    val okCount: StateFlow<Int> = _okCount.asStateFlow()

    private val _failCount = MutableStateFlow(0)
    val failCount: StateFlow<Int> = _failCount.asStateFlow()

    private val _lastPing = MutableStateFlow<PingResult?>(null)
    val lastPing: StateFlow<PingResult?> = _lastPing.asStateFlow()

    private val _bestPing = MutableStateFlow<Double?>(null)
    val bestPing: StateFlow<Double?> = _bestPing.asStateFlow()

    private val _logLines = MutableStateFlow<List<LogLine>>(emptyList())
    val logLines: StateFlow<List<LogLine>> = _logLines.asStateFlow()

    private val _connectedTunnel = MutableStateFlow<String?>(null)
    /** "City – Node · wireguard:port" kept after stop-on-success / manual connect. */
    val connectedTunnel: StateFlow<String?> = _connectedTunnel.asStateFlow()

    private val _runStartedAt = MutableStateFlow<Long?>(null)
    val runStartedAt: StateFlow<Long?> = _runStartedAt.asStateFlow()

    private val _lastOutcome = MutableStateFlow<RunOutcome?>(null)
    val lastOutcome: StateFlow<RunOutcome?> = _lastOutcome.asStateFlow()

    private val _probeResult = MutableStateFlow<ProbeResult?>(null)
    val probeResult: StateFlow<ProbeResult?> = _probeResult.asStateFlow()

    /** Bumped on every recorded result so the UI reloads the table. */
    private val _resultsVersion = MutableStateFlow(0)
    val resultsVersion: StateFlow<Int> = _resultsVersion.asStateFlow()

    fun setRunning(value: Boolean) { _running.value = value }
    fun setBusyLabel(value: String?) { _busyLabel.value = value }
    fun setPhase(phase: Phase, state: PhaseState, secs: Double?) {
        _phaseStates.value = _phaseStates.value + (phase to state)
        if (secs != null) _phaseSecs.value = _phaseSecs.value + (phase to secs)
    }

    fun resetPhases() {
        _phaseStates.value = Phase.entries.associateWith { PhaseState.IDLE }
        _phaseSecs.value = emptyMap()
    }

    fun beginTest(index: Int, total: Int, executedCount: Int, task: TestTask) {
        _progressIndex.value = index
        _progressTotal.value = total
        _executed.value = executedCount
        _currentTask.value = task
        resetPhases()
    }

    fun setCounts(ok: Int, fail: Int) {
        _okCount.value = ok
        _failCount.value = fail
    }

    fun setLastPing(result: PingResult?) {
        _lastPing.value = result
        if (result != null) {
            val best = _bestPing.value
            if (best == null || result.avg < best) _bestPing.value = result.avg
        }
    }

    fun resetRunStats() {
        _okCount.value = 0
        _failCount.value = 0
        _executed.value = 0
        _progressIndex.value = 0
        _progressTotal.value = 0
        _bestPing.value = null
        _lastPing.value = null
        _currentTask.value = null
        _runStartedAt.value = System.currentTimeMillis()
        resetPhases()
    }

    fun appendLog(text: String, tag: String) {
        val lines = _logLines.value + LogLine(text, tag)
        _logLines.value = if (lines.size > 200) lines.takeLast(200) else lines
    }

    fun setConnectedTunnel(value: String?) { _connectedTunnel.value = value }
    fun setOutcome(outcome: RunOutcome) { _lastOutcome.value = outcome }
    fun setProbeResult(result: ProbeResult) { _probeResult.value = result }
    fun bumpResultsVersion() { _resultsVersion.value += 1 }
}
