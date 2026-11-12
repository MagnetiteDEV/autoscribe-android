/**
 * Standalone tests for the run loop (TestEngine) with fake tunnel control,
 * pinger and result sink. Run with: bash tests/run_tests.sh
 *
 * These verify the desktop _run() semantics that matter most: success and
 * timeout recording, resume skipping, stop-on-success keeping the tunnel
 * up, "stop while connecting records nothing", and disconnect retries.
 */
import com.autoscribe.android.engine.Phase
import com.autoscribe.android.engine.PhaseState
import com.autoscribe.android.engine.PingResult
import com.autoscribe.android.engine.Pop
import com.autoscribe.android.engine.RunOptions
import com.autoscribe.android.engine.TestEngine
import com.autoscribe.android.engine.TestResult
import com.autoscribe.android.engine.TestTask
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess

private var passed = 0
private val failures = mutableListOf<String>()

private fun check(name: String, block: () -> Boolean) {
    try {
        if (block()) {
            passed++
            println("PASS: $name")
        } else {
            failures.add(name)
            println("FAIL: $name")
        }
    } catch (e: Exception) {
        failures.add("$name (threw ${e::class.simpleName}: ${e.message})")
        println("FAIL: $name (threw ${e::class.simpleName}: ${e.message})")
    }
}

private fun pop(city: String, node: String) =
    Pop("Netherlands", "NL", "Europe", city, node, true, true)

private class FakeControl(
    /** Tunnel names that bring the VPN up when brought up. */
    private val workingTunnels: Set<String>,
    initiallyUp: Boolean = false,
) : TestEngine.TunnelControl {
    var vpnUp: Boolean = initiallyUp
        private set
    val ups = mutableListOf<String>()
    val downs = mutableListOf<String>()

    override fun up(name: String) {
        ups.add(name)
        if (name in workingTunnels) vpnUp = true
    }

    override fun down(name: String) {
        downs.add(name)
        vpnUp = false
    }

    override fun vpnActive(): Boolean = vpnUp
}

private class FakePinger(private val result: PingResult?) : TestEngine.Pinger {
    var calls = 0
    override fun ping(host: String, count: Int): PingResult? {
        calls++
        return result
    }
}

private class FakeSink(private val tested: Set<String> = emptySet()) : TestEngine.ResultSink {
    val records = mutableListOf<TestResult>()
    override fun isTested(task: TestTask): Boolean = task.tunnelName in tested
    override fun record(result: TestResult) {
        records.add(result)
    }
}

private class CollectingListener : TestEngine.RunListener {
    val phases = mutableListOf<Pair<Phase, PhaseState>>()
    val logs = mutableListOf<String>()
    var begun = 0
    override fun log(msg: String, tag: String) {
        logs.add("[$tag] $msg")
    }

    override fun phase(phase: Phase, state: PhaseState, secs: Double?) {
        phases.add(phase to state)
    }

    override fun beginTest(index: Int, total: Int, executed: Int, task: TestTask) {
        begun++
    }

    override fun pingResult(result: PingResult?) {}
    override fun resultRecorded(result: TestResult) {}
}

private fun engine(
    control: FakeControl,
    pinger: FakePinger,
    sink: FakeSink,
    listener: TestEngine.RunListener,
    stop: AtomicBoolean,
) = TestEngine(control, pinger, sink, listener) { stop.get() }

private val PING = PingResult(avg = 23.0, min = 21.0, max = 25.0, loss = 0.0, method = "ICMP")

fun main() {
    val tasks = listOf(
        TestTask(pop("Amsterdam", "Tulip"), 443),
        TestTask(pop("Rotterdam", "Erasmus"), 443),
    )
    val options = RunOptions(onlyImported = false, timeoutSec = 2, settleSec = 0, pingCount = 1)

    check("engine: two working tunnels record two SUCCESS with ping") {
        val control = FakeControl(tasks.map { it.tunnelName }.toSet())
        val sink = FakeSink()
        val listener = CollectingListener()
        val outcome = engine(control, FakePinger(PING), sink, listener, AtomicBoolean(false))
            .run(tasks, options)
        sink.records.size == 2 && sink.records.all { it.status == "SUCCESS" } &&
            sink.records.all { it.ping == 23.0 && it.packetLoss == 0.0 } &&
            sink.records.all { it.duration > 0.0 } &&
            outcome.ok == 2 && outcome.fail == 0 && outcome.executed == 2 &&
            control.downs.size == 2 && listener.begun == 2
    }

    check("engine: tunnel that never comes up records FAILURE with duration 0") {
        val control = FakeControl(emptySet())
        val sink = FakeSink()
        val outcome = engine(control, FakePinger(PING), sink, CollectingListener(), AtomicBoolean(false))
            .run(listOf(tasks[0]), options)
        sink.records.size == 1 && sink.records[0].status == "FAILURE" &&
            sink.records[0].duration == 0.0 && sink.records[0].ping == null &&
            outcome.ok == 0 && outcome.fail == 1
    }

    check("engine: ping with no replies still records SUCCESS, ping null") {
        val control = FakeControl(setOf(tasks[0].tunnelName))
        val sink = FakeSink()
        engine(control, FakePinger(null), sink, CollectingListener(), AtomicBoolean(false))
            .run(listOf(tasks[0]), options)
        sink.records.size == 1 && sink.records[0].status == "SUCCESS" && sink.records[0].ping == null
    }

    check("engine: resume skips already-tested tasks") {
        val control = FakeControl(tasks.map { it.tunnelName }.toSet())
        val sink = FakeSink(tested = setOf(tasks[0].tunnelName))
        val outcome = engine(control, FakePinger(PING), sink, CollectingListener(), AtomicBoolean(false))
            .run(tasks, options.copy(resume = true))
        sink.records.size == 1 && sink.records[0].city == "Rotterdam" &&
            outcome.skipped == 1 && outcome.executed == 1
    }

    check("engine: stop-on-success keeps the connection (no DOWN after it)") {
        val control = FakeControl(tasks.map { it.tunnelName }.toSet())
        val sink = FakeSink()
        val outcome = engine(control, FakePinger(PING), sink, CollectingListener(), AtomicBoolean(false))
            .run(tasks, options.copy(stopOnSuccess = true))
        sink.records.size == 1 && outcome.keptConnection != null &&
            outcome.keptConnection!!.contains("Amsterdam") &&
            control.downs.isEmpty() && control.vpnUp
    }

    check("engine: stop while connecting records nothing and disconnects") {
        val control = FakeControl(emptySet())
        val sink = FakeSink()
        val stop = AtomicBoolean(false)
        Thread {
            Thread.sleep(600)
            stop.set(true)
        }.start()
        val outcome = engine(control, FakePinger(PING), sink, CollectingListener(), stop)
            .run(listOf(tasks[0]), options.copy(timeoutSec = 30))
        sink.records.isEmpty() && outcome.stopped && control.downs.size == 1
    }

    check("engine: stop between tests ends the run after the current test") {
        val control = FakeControl(tasks.map { it.tunnelName }.toSet())
        val sink = FakeSink()
        val stop = AtomicBoolean(false)
        val listener = object : TestEngine.RunListener by CollectingListener() {
            override fun resultRecorded(result: TestResult) {
                stop.set(true)
            }
        }
        val outcome = engine(control, FakePinger(PING), sink, listener, stop)
            .run(tasks, options)
        sink.records.size == 1 && outcome.stopped && control.downs.size == 1
    }

    check("engine: ensureDisconnected retries DOWN until VPN is gone") {
        val control = FakeControl(setOf(tasks[0].tunnelName), initiallyUp = true)
        val eng = engine(control, FakePinger(PING), FakeSink(), CollectingListener(), AtomicBoolean(false))
        val ok = eng.ensureDisconnected("WS-Stale-443", tasks[0].tunnelName)
        ok && control.downs == listOf("WS-Stale-443") && !control.vpnUp
    }

    check("engine: phase sequence per test is disconnect/connect/settle/ping") {
        val control = FakeControl(setOf(tasks[0].tunnelName))
        val listener = CollectingListener()
        engine(control, FakePinger(PING), FakeSink(), listener, AtomicBoolean(false))
            .run(listOf(tasks[0]), options)
        val donePhases = listener.phases.filter { it.second == PhaseState.DONE }.map { it.first }
        donePhases == listOf(Phase.DISCONNECT, Phase.CONNECT, Phase.PING) &&
            listener.phases.any { it == Phase.SETTLE to PhaseState.SKIP }
    }

    check("engine: connectOne succeeds and reports phases") {
        val control = FakeControl(setOf(tasks[0].tunnelName))
        val listener = CollectingListener()
        val connected = engine(control, FakePinger(PING), FakeSink(), listener, AtomicBoolean(false))
            .connectOne(tasks[0], timeoutSec = 2, lastTunnel = null)
        connected && control.vpnUp &&
            listener.phases.any { it == Phase.CONNECT to PhaseState.DONE }
    }

    check("engine: connectOne fails cleanly when the tunnel never comes up") {
        val control = FakeControl(emptySet())
        val connected = engine(control, FakePinger(PING), FakeSink(), CollectingListener(), AtomicBoolean(false))
            .connectOne(tasks[0], timeoutSec = 2, lastTunnel = null)
        !connected && !control.vpnUp
    }

    println()
    println("$passed passed, ${failures.size} failed")
    if (failures.isNotEmpty()) {
        println("Failures:")
        failures.forEach { println("  - $it") }
        exitProcess(1)
    }
}
