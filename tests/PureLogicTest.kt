/**
 * Standalone regression tests for the pure engine (no Android imports).
 * Run with: bash tests/run_tests.sh
 *
 * Covers the exact behaviours ported from the desktop app: ping parsing,
 * tunnel naming, plan building, best-routes ordering, port stats, the
 * weak-port tip rule, CSV output, and the string table.
 */
import com.autoscribe.android.data.EMBEDDED_POPS
import com.autoscribe.android.data.REGION_NAMES
import com.autoscribe.android.data.STRING_TABLE
import com.autoscribe.android.data.Strings
import com.autoscribe.android.engine.CsvExport
import com.autoscribe.android.engine.PingParser
import com.autoscribe.android.engine.Pop
import com.autoscribe.android.engine.RunOptions
import com.autoscribe.android.engine.TestPlan
import com.autoscribe.android.engine.TestResult
import com.autoscribe.android.engine.TestTask
import com.autoscribe.android.engine.TunnelNaming
import com.autoscribe.android.engine.WIREGUARD_PORTS
import com.autoscribe.android.engine.matchesRegion
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.random.Random
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

private fun pop(city: String, node: String, country: String = "Netherlands", continent: String = "Europe") =
    Pop(country, "NL", continent, city, node, true, true)

private fun result(
    city: String, port: Int, status: String, ping: Double?, loss: Double?, duration: Double,
) = TestResult(
    city = city, location = "Node", country = "Netherlands", protocol = "wireguard",
    port = port, status = status, duration = duration, ping = ping, packetLoss = loss,
    profileName = "Manual", timestamp = "2026-11-12 13:00:00",
)

fun main() {
    // ---- PingParser ----
    val androidOut = """
        PING 1.1.1.1 (1.1.1.1) 56(84) bytes of data.
        64 bytes from 1.1.1.1: icmp_seq=1 ttl=57 time=23.4 ms
        64 bytes from 1.1.1.1: icmp_seq=2 ttl=57 time=21.1 ms
        64 bytes from 1.1.1.1: icmp_seq=3 ttl=57 time=25.0 ms
        64 bytes from 1.1.1.1: icmp_seq=4 ttl=57 time=22.8 ms
        64 bytes from 1.1.1.1: icmp_seq=5 ttl=57 time=24.2 ms
        --- 1.1.1.1 ping statistics ---
        5 packets transmitted, 5 received, 0% packet loss, time 4004ms
        rtt min/avg/max/mdev = 21.100/23.300/25.000/1.234 ms
    """.trimIndent()
    check("ping: android 5 replies parsed") {
        PingParser.parse(androidOut) == listOf(23.4, 21.1, 25.0, 22.8, 24.2)
    }
    check("ping: android summary avg/min/max/loss") {
        val s = PingParser.summarize(PingParser.parse(androidOut), 5, "ICMP")!!
        kotlin.math.abs(s.avg - 23.3) < 1e-9 && s.min == 21.1 && s.max == 25.0 && s.loss == 0.0 && s.method == "ICMP"
    }
    val windowsOut = """
        Reply from 1.1.1.1: bytes=32 time=23ms TTL=57
        Reply from 1.1.1.1: bytes=32 time=21ms TTL=57
        Reply from 1.1.1.1: bytes=32 time=25ms TTL=57
        Ping statistics for 1.1.1.1:
            Packets: Sent = 5, Received = 3, Lost = 2 (40% loss),
        Approximate round trip times in milli-seconds:
            Minimum = 21ms, Maximum = 25ms, Average = 23ms
    """.trimIndent()
    check("ping: windows replies parsed, summary line excluded") {
        PingParser.parse(windowsOut) == listOf(23.0, 21.0, 25.0)
    }
    check("ping: partial loss 40%") {
        PingParser.summarize(PingParser.parse(windowsOut), 5, "ICMP")!!.loss == 40.0
    }
    check("ping: decimal comma normalised") {
        PingParser.parse("64 bytes from 1.1.1.1: icmp_seq=1 ttl=57 time=23,4 ms") == listOf(23.4)
    }
    check("ping: sub-millisecond '<1ms' form") {
        PingParser.parse("Reply from 1.1.1.1: bytes=32 time<1ms TTL=57") == listOf(1.0)
    }
    check("ping: no replies -> empty + null summary") {
        val out = "PING 1.1.1.1\n5 packets transmitted, 0 received, 100% packet loss"
        PingParser.parse(out).isEmpty() && PingParser.summarize(emptyList(), 5, "ICMP") == null
    }
    check("ping: rtt line without ttl excluded") {
        PingParser.parse("rtt min/avg/max/mdev = 21.100/23.300/25.000/1.234 ms").isEmpty()
    }

    // ---- TunnelNaming ----
    check("naming: Amsterdam Tulip 443") {
        TunnelNaming.tunnelNameFor(pop("Amsterdam", "Tulip"), 443) == "WS-Amsterdam-Tulip-443"
    }
    check("naming: multi-word city and node") {
        TunnelNaming.tunnelNameFor(pop("Buenos Aires", "Yerba Mate"), 80) ==
            "WS-Buenos-Aires-Yerba-Mate-80"
    }
    check("naming: Salt Lake City") {
        TunnelNaming.tunnelNameFor(pop("Salt Lake City", "Missionary"), 1194) ==
            "WS-Salt-Lake-City-Missionary-1194"
    }
    check("naming: config file name") {
        TunnelNaming.configFileName(pop("Amsterdam", "Tulip"), 443) == "WS-Amsterdam-Tulip-443.conf"
    }
    check("naming: sanitize drops punctuation, collapses dashes") {
        TunnelNaming.sanitize("Côte d'Azur") == "Cte-dAzur" &&
            TunnelNaming.sanitize("  A  --  B ") == "A-B" &&
            TunnelNaming.sanitize("a_b") == "a-b"
    }
    check("naming: all 199 pops x 6 ports unique") {
        val names = EMBEDDED_POPS.flatMap { p -> WIREGUARD_PORTS.map { TunnelNaming.tunnelNameFor(p, it) } }
        names.size == 1194 && names.toSet().size == 1194
    }
    check("naming: all names file-safe and short") {
        val re = Regex("^WS-[A-Za-z0-9-]+-\\d+$")
        EMBEDDED_POPS.all { p ->
            WIREGUARD_PORTS.all { port ->
                val n = TunnelNaming.tunnelNameFor(p, port)
                re.matches(n) && n.length <= 60
            }
        }
    }

    // ---- Embedded locations ----
    check("locations: 199 pops, 74 countries, unique keys, nodes present") {
        EMBEDDED_POPS.size == 199 &&
            EMBEDDED_POPS.map { it.country }.toSet().size == 74 &&
            EMBEDDED_POPS.map { it.key }.toSet().size == 199 &&
            EMBEDDED_POPS.all { it.node.isNotEmpty() && it.city.isNotEmpty() }
    }
    check("locations: no city in two countries") {
        EMBEDDED_POPS.groupBy { it.city }.values.all { group ->
            group.map { it.country }.toSet().size == 1
        }
    }

    // ---- TestPlan ----
    // NOTE: RunOptions defaults to onlyImported=true (the production-safe
    // default); full-plan tests below opt out explicitly.
    val options = RunOptions(onlyImported = false)
    check("plan: empty selection + all ports = 1194 tasks") {
        TestPlan.buildTasks(EMBEDDED_POPS, options).size == 1194
    }
    check("plan: subset selection x port order") {
        val pops = listOf(pop("Amsterdam", "Tulip"), pop("Rotterdam", "Erasmus"))
        val tasks = TestPlan.buildTasks(
            pops,
            RunOptions(selectedKeys = pops.map { it.key }.toSet(), portsInOrder = listOf(443, 80), onlyImported = false),
        )
        tasks.size == 4 && tasks.map { it.port } == listOf(443, 80, 443, 80) &&
            tasks[0].pop.city == "Amsterdam" && tasks[2].pop.city == "Rotterdam"
    }
    check("plan: onlyImported filter") {
        val pops = listOf(pop("Amsterdam", "Tulip"), pop("Rotterdam", "Erasmus"))
        val one = setOf(TunnelNaming.tunnelNameFor(pops[0], 443))
        val tasks = TestPlan.buildTasks(
            pops,
            RunOptions(portsInOrder = listOf(443, 80), onlyImported = true, importedTunnelNames = one),
        )
        tasks.size == 1 && tasks[0].port == 443 && tasks[0].pop.city == "Amsterdam"
    }
    check("plan: onlyImported with nothing imported = 0 tasks") {
        TestPlan.buildTasks(EMBEDDED_POPS, RunOptions(onlyImported = true)).isEmpty()
    }
    check("plan: seeded shuffle is deterministic and preserves the set") {
        val shuffled = RunOptions(randomCities = true, onlyImported = false)
        val a = TestPlan.buildTasks(EMBEDDED_POPS, shuffled, Random(42))
        val b = TestPlan.buildTasks(EMBEDDED_POPS, shuffled, Random(42))
        val plain = TestPlan.buildTasks(EMBEDDED_POPS, options)
        a == b && a.toSet() == plain.toSet() && a != plain
    }
    check("plan: chaos shuffle is deterministic and preserves the set") {
        val chaotic = RunOptions(chaosMode = true, onlyImported = false)
        val a = TestPlan.buildTasks(EMBEDDED_POPS, chaotic, Random(7))
        val b = TestPlan.buildTasks(EMBEDDED_POPS, chaotic, Random(7))
        a == b && a.toSet() == TestPlan.buildTasks(EMBEDDED_POPS, options).toSet() && a.size == 1194
    }
    check("plan: bestRoutes ordering ping/loss/duration, failures excluded") {
        val rows = listOf(
            result("A", 443, "SUCCESS", 50.0, 0.0, 2.0),
            result("B", 443, "SUCCESS", 30.0, 10.0, 1.0),
            result("C", 443, "SUCCESS", 30.0, 0.0, 5.0),
            result("D", 443, "SUCCESS", 30.0, 0.0, 1.0),
            result("E", 443, "FAILURE", null, null, 0.0),
            result("F", 443, "SUCCESS", null, null, 1.0),
        )
        TestPlan.bestRoutes(rows).map { it.city } == listOf("D", "C", "B", "A")
    }
    check("plan: portStats sorted by rate desc") {
        val rows = listOf(
            result("A", 443, "SUCCESS", 30.0, 0.0, 1.0),
            result("B", 443, "FAILURE", null, null, 0.0),
            result("C", 80, "SUCCESS", 30.0, 0.0, 1.0),
            result("D", 80, "SUCCESS", 30.0, 0.0, 1.0),
        )
        val stats = TestPlan.portStats(rows)
        stats.map { it.port } == listOf(80, 443) && stats[0].rate == 1.0 && stats[1].rate == 0.5
    }
    check("plan: weakPorts rule (>=4 tests, <40%, proper subset)") {
        val weak = (1..4).map { result("W$it", 53, "FAILURE", null, null, 0.0) } +
            listOf(result("W5", 53, "SUCCESS", 30.0, 0.0, 1.0))
        val strong = (1..4).map { result("S$it", 443, "SUCCESS", 30.0, 0.0, 1.0) }
        TestPlan.weakPorts(weak + strong) == listOf(53) &&
            TestPlan.weakPorts(weak) == emptyList<Int>() &&
            TestPlan.weakPorts(strong) == emptyList<Int>()
    }
    check("plan: matchesRegion USA/continent/All") {
        val usEast = Pop("US East", "US", "North America", "New York", "Liberty", true, true)
        val uk = Pop("United Kingdom", "GB", "Europe", "London", "Thames", true, true)
        matchesRegion(usEast, "USA") && !matchesRegion(uk, "USA") &&
            matchesRegion(uk, "Europe") && matchesRegion(usEast, "All") &&
            matchesRegion(usEast, "North America")
    }

    // ---- CsvExport ----
    check("csv: header and row, nulls empty") {
        val csv = CsvExport.buildCsv(listOf(result("A", 443, "SUCCESS", 23.0, 0.0, 1.25)))
        val lines = csv.trimEnd('\n').split("\n")
        lines[0] == "city,location,country,protocol,port,status,duration,ping,packet_loss,profile_name,timestamp" &&
            lines[1] == "A,Node,Netherlands,wireguard,443,SUCCESS,1.25,23.0,0.0,Manual,2026-11-12 13:00:00"
    }
    check("csv: null ping/loss render empty") {
        val csv = CsvExport.buildCsv(listOf(result("A", 443, "FAILURE", null, null, 0.0)))
        csv.contains("FAILURE,0.0,,,Manual,")
    }
    check("csv: quoting commas, quotes, newlines") {
        CsvExport.quote("a,b") == "\"a,b\"" &&
            CsvExport.quote("a\"b") == "\"a\"\"b\"" &&
            CsvExport.quote("a\nb") == "\"a\nb\"" &&
            CsvExport.quote("plain") == "plain"
    }
    check("csv: safe profile names") {
        CsvExport.safeProfileName("My Profile!") == "My_Profile" &&
            CsvExport.safeProfileName("!!!") == "Manual" &&
            CsvExport.safeProfileName("  a-b_c ") == "a-b_c"
    }
    check("csv: file name pattern") {
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).parse("2026-11-12 13:00:00")!!
        CsvExport.fileName(3, "My Profile", date) == "3_My_Profile_2026-11-12_13-00-00.csv"
    }
    check("csv: timestamp format") {
        Regex("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}").matches(CsvExport.nowTimestamp())
    }

    // ---- Strings ----
    check("strings: 244 keys, all with en/fa/ru non-empty") {
        STRING_TABLE.size == 244 &&
            STRING_TABLE.values.all { e ->
                listOf("en", "fa", "ru").all { (e[it] ?: "").isNotBlank() }
            }
    }
    check("strings: fa lookup + placeholder substitution") {
        val s = Strings("fa")
        s.t("start") == "شروع" && s.t("start_n", "n" to 5) == "شروع  ·  5 تست"
    }
    check("strings: unknown key returns key, unknown lang falls back to en") {
        Strings("fa").t("no_such_key") == "no_such_key" &&
            Strings("xx").t("start") == "Start"
    }
    check("strings: duration formatting") {
        val s = Strings("fa")
        s.duration(45) == "45 ثانیه" && s.duration(90) == "1 دقیقه" &&
            s.duration(3661) == "1 ساعت و 01 دقیقه"
    }
    check("strings: num grouping (ru uses NBSP)") {
        Strings("en").num(1234567) == "1,234,567" &&
            Strings("ru").num(1234567) == "1\u00a0234\u00a0567"
    }
    check("strings: regions table") {
        REGION_NAMES.size == 9 && Strings("fa").region("Europe") == "اروپا" &&
            Strings("ru").region("USA") == "США" && Strings("en").region("Asia") == "Asia"
    }
    check("strings: overrides applied (no Ctrl hints, per-port wording)") {
        val s = Strings("en")
        !s.t("empty_results").contains("Ctrl") && s.t("stop") == "Stop" &&
            s.t("works_sub") == "Success rate by port"
    }

    println()
    println("$passed passed, ${failures.size} failed")
    if (failures.isNotEmpty()) {
        println("Failures:")
        failures.forEach { println("  - $it") }
        exitProcess(1)
    }
}
