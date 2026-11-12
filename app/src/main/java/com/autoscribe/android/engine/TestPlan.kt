package com.autoscribe.android.engine

import kotlin.random.Random

/**
 * Test-plan builder. Mirrors desktop WindscribeWorker._build_tasks():
 * locations (optionally shuffled) x ports in order, optionally with the
 * whole task list shuffled (chaos mode). The Android-only addition is the
 * imported-tunnels filter.
 */
object TestPlan {
    fun buildTasks(
        allPops: List<Pop>,
        options: RunOptions,
        random: Random = Random.Default,
    ): List<TestTask> {
        var locations = if (options.selectedKeys.isEmpty()) {
            allPops
        } else {
            allPops.filter { it.key in options.selectedKeys }
        }
        if (options.randomCities) locations = locations.shuffled(random)
        var tasks = locations.flatMap { pop ->
            options.portsInOrder.map { port -> TestTask(pop, port) }
        }
        if (options.onlyImported) {
            tasks = tasks.filter { it.tunnelName in options.importedTunnelNames }
        }
        if (options.chaosMode) tasks = tasks.shuffled(random)
        return tasks
    }

    /** Servers covered by the selection (before the port expansion). */
    fun selectedPops(allPops: List<Pop>, options: RunOptions): List<Pop> =
        if (options.selectedKeys.isEmpty()) allPops
        else allPops.filter { it.key in options.selectedKeys }

    /** Best-routes ordering, mirroring the desktop: ping, then loss, then duration.
     *  Only SUCCESS rows with a measured ping qualify. */
    fun bestRoutes(results: List<TestResult>): List<TestResult> =
        results
            .filter { it.isSuccess && it.ping != null }
            .sortedWith(
                compareBy<TestResult> { it.ping!! }
                    .thenBy { it.packetLoss ?: 0.0 }
                    .thenBy { it.duration },
            )

    /** Per-port success stats for "What works here", mirroring render_insights()
     *  with ports in place of protocols. Sorted by success rate, best first. */
    data class PortStat(val port: Int, val ok: Int, val total: Int) {
        val rate: Double get() = if (total == 0) 0.0 else ok.toDouble() / total
    }

    fun portStats(results: List<TestResult>): List<PortStat> {
        val grouped = results.groupBy { it.port }
        return grouped.map { (port, rows) ->
            PortStat(port, rows.count { it.isSuccess }, rows.size)
        }.sortedByDescending { it.rate }
    }

    /** Ports that "rarely connect": >=4 tests and <40% success, mirroring the
     *  desktop weak-protocol tip. Empty when every port is weak (no tip then). */
    fun weakPorts(results: List<TestResult>): List<Int> {
        val stats = portStats(results)
        val weak = stats.filter { it.total >= 4 && it.rate < 0.4 }.map { it.port }
        return if (weak.isNotEmpty() && weak.size < stats.size) weak else emptyList()
    }
}
