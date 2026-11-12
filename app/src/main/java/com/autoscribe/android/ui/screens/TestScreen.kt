package com.autoscribe.android.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autoscribe.android.EngineState
import com.autoscribe.android.MainViewModel
import com.autoscribe.android.data.Strings
import com.autoscribe.android.engine.TestPlan
import com.autoscribe.android.engine.WIREGUARD_PORTS
import com.autoscribe.android.ui.components.CheckRow
import com.autoscribe.android.ui.components.ConfirmDialog
import com.autoscribe.android.ui.components.NumberField
import com.autoscribe.android.ui.components.PhaseRow
import com.autoscribe.android.ui.components.SectionCard
import com.autoscribe.android.ui.components.StatItem
import com.autoscribe.android.ui.components.TextFieldRow
import com.autoscribe.android.ui.components.ToggleRow
import com.autoscribe.android.ui.theme.AutoScribeColors
import com.autoscribe.android.ui.theme.pingColor
import com.autoscribe.android.ui.theme.rateColor
import kotlinx.coroutines.delay

@Composable
fun TestScreen(
    vm: MainViewModel,
    strings: Strings,
    onRequestWgPermission: () -> Unit,
    onGoToTunnels: () -> Unit,
) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsState()
    val pops by vm.pops.collectAsState()
    val imported by vm.imported.collectAsState()
    val results by vm.results.collectAsState()
    val wgInstalled by vm.wgInstalled.collectAsState()
    val wgPermission by vm.wgPermission.collectAsState()
    val profileNames by vm.profileNames.collectAsState()

    val running by EngineState.running.collectAsState()
    val busyLabel by EngineState.busyLabel.collectAsState()
    val phaseStates by EngineState.phaseStates.collectAsState()
    val phaseSecs by EngineState.phaseSecs.collectAsState()
    val currentTask by EngineState.currentTask.collectAsState()
    val progressIndex by EngineState.progressIndex.collectAsState()
    val progressTotal by EngineState.progressTotal.collectAsState()
    val executed by EngineState.executed.collectAsState()
    val okCount by EngineState.okCount.collectAsState()
    val failCount by EngineState.failCount.collectAsState()
    val lastPing by EngineState.lastPing.collectAsState()
    val bestPing by EngineState.bestPing.collectAsState()
    val logLines by EngineState.logLines.collectAsState()
    val connectedTunnel by EngineState.connectedTunnel.collectAsState()
    val runStartedAt by EngineState.runStartedAt.collectAsState()

    var showPicker by remember { mutableStateOf(false) }
    var showFreshConfirm by remember { mutableStateOf<Int?>(null) }
    var showSaveProfile by remember { mutableStateOf(false) }
    var profileNameInput by rememberSaveable { mutableStateOf("") }
    var profileMenuExpanded by remember { mutableStateOf(false) }
    var showDeleteProfile by remember { mutableStateOf<String?>(null) }

    var timeoutText by rememberSaveable(settings.timeoutSec) { mutableStateOf(settings.timeoutSec.toString()) }
    var settleText by rememberSaveable(settings.settleSec) { mutableStateOf(settings.settleSec.toString()) }
    var pingCountText by rememberSaveable(settings.pingCount) { mutableStateOf(settings.pingCount.toString()) }
    var pingHostText by rememberSaveable(settings.pingHost) { mutableStateOf(settings.pingHost) }

    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(running) {
        while (running) {
            nowMs = System.currentTimeMillis()
            delay(1000)
        }
    }

    val selectedPops = remember(pops, settings.selectedKeys) {
        if (settings.selectedKeys.isEmpty()) pops else pops.filter { it.key in settings.selectedKeys }
    }
    val planTasks = remember(pops, settings, imported) { vm.currentPlanTasks() }
    val importedInPlan = remember(planTasks, imported) { planTasks.count { it.tunnelName in imported } }
    val portStats = remember(results) { TestPlan.portStats(results) }
    val weakPorts = remember(results) { TestPlan.weakPorts(results) }
    val okRows = remember(results) { results.filter { it.isSuccess } }
    val avgConnect = remember(okRows) { if (okRows.isEmpty()) null else okRows.map { it.duration }.average() }
    val totalDone = okCount + failCount
    val successRate = if (totalDone == 0) null else okCount.toDouble() / totalDone

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    fun onStartClicked() {
        val error = vm.validateTiming(timeoutText, settleText, pingCountText, pingHostText.trim())
        if (error != null) {
            toast(error)
            return
        }
        vm.updateSettings {
            it.copy(
                timeoutSec = timeoutText.trim().toInt(),
                settleSec = settleText.trim().toInt(),
                pingCount = pingCountText.trim().toInt(),
                pingHost = pingHostText.trim(),
            )
        }
        when (val check = vm.prepareStart()) {
            is MainViewModel.StartCheck.Ready -> vm.startRun(clearFirst = false, context)
            is MainViewModel.StartCheck.NeedFreshConfirm -> showFreshConfirm = check.stored
            is MainViewModel.StartCheck.Blocked -> {
                toast(strings.t(check.messageKey))
                if (check.messageKey == "need_wg" || check.messageKey == "need_imported") onGoToTunnels()
            }
        }
    }

    val logScroll = rememberScrollState()
    LaunchedEffect(logLines.size) {
        if (logLines.isNotEmpty()) logScroll.scrollTo(logScroll.maxValue)
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // WireGuard readiness banner (only when something is missing).
        if (!wgInstalled || !wgPermission) {
            SectionCard(title = strings.t("wg_title")) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (wgInstalled) Icons.Filled.Check else Icons.Filled.Close,
                        contentDescription = null,
                        tint = if (wgInstalled) AutoScribeColors.Good else AutoScribeColors.Bad,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (wgInstalled) strings.t("wg_installed") else strings.t("wg_not_installed"),
                        color = AutoScribeColors.Text, fontSize = 14.sp,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (wgPermission) Icons.Filled.Check else Icons.Filled.Close,
                        contentDescription = null,
                        tint = if (wgPermission) AutoScribeColors.Good else AutoScribeColors.Bad,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (wgPermission) strings.t("wg_permission_granted") else strings.t("wg_permission_missing"),
                        color = AutoScribeColors.Text, fontSize = 14.sp,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(strings.t("wg_remote_hint"), fontSize = 12.sp, color = AutoScribeColors.Muted)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (wgInstalled && !wgPermission) {
                        Button(onClick = onRequestWgPermission) { Text(strings.t("wg_request_permission")) }
                    }
                    if (wgInstalled) {
                        OutlinedButton(onClick = { vm.openWireGuardApp(context) }) { Text(strings.t("wg_open_app")) }
                    }
                }
                if (!wgInstalled) {
                    Spacer(Modifier.height(6.dp))
                    Text(strings.t("wg_install_hint"), fontSize = 12.sp, color = AutoScribeColors.WarnText)
                }
            }
        }

        // Connected chip.
        if (connectedTunnel != null) {
            SectionCard(title = strings.t("connected_chip", "x" to connectedTunnel!!)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.disconnect(context) }, enabled = !running && busyLabel == null) {
                        Text(strings.t("disconnect"))
                    }
                }
            }
        }

        // Live dashboard.
        SectionCard(
            title = when {
                running -> strings.t("cap_testing")
                busyLabel != null -> strings.t("cap_connecting")
                else -> strings.t("cap_ready")
            },
            subtitle = currentTask?.let {
                "${it.pop.displayName} (${it.pop.country}) · ${it.route}"
            } ?: strings.t(
                "ready_sub",
                "scope" to strings.t(
                    "scope_math",
                    "s" to strings.num(selectedPops.size),
                    "r" to strings.num(settings.portsInOrder.size),
                ),
                "n" to strings.num(planTasks.size),
            ),
        ) {
            PhaseRow(states = phaseStates, secs = phaseSecs, strings = strings)
            Spacer(Modifier.height(12.dp))
            if (running && progressTotal > 0) {
                LinearProgressIndicator(
                    progress = { progressIndex.toFloat() / progressTotal },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    strings.t(
                        "test_progress",
                        "i" to strings.num(progressIndex),
                        "total" to strings.num(progressTotal),
                        "pct" to ((progressIndex * 100) / progressTotal),
                    ),
                    fontSize = 12.sp, color = AutoScribeColors.Muted,
                )
                runStartedAt?.let { started ->
                    val elapsedSec = ((nowMs - started) / 1000).toInt()
                    val remaining = if (executed > 0 && elapsedSec > 0) {
                        val perTest = elapsedSec.toDouble() / executed
                        strings.t(
                            "time_left",
                            "left" to strings.duration(((progressTotal - executed) * perTest).toInt()),
                            "elapsed" to strings.t("elapsed", "t" to strings.duration(elapsedSec)),
                        )
                    } else {
                        strings.t("estimating", "elapsed" to strings.t("elapsed", "t" to strings.duration(elapsedSec)))
                    }
                    Text(remaining, fontSize = 12.sp, color = AutoScribeColors.Muted)
                }
                Spacer(Modifier.height(8.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    lastPing?.let { "%.0f".format(it.avg) } ?: "–",
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = pingColor(lastPing?.avg),
                )
                Spacer(Modifier.width(8.dp))
                Text(strings.t("ms"), color = AutoScribeColors.Muted)
                Spacer(Modifier.width(16.dp))
                lastPing?.let {
                    Text(
                        strings.t(
                            "ping_detail",
                            "a" to "%.0f".format(it.min),
                            "b" to "%.0f".format(it.max),
                            "c" to "%.0f".format(it.loss),
                        ) + " [${it.method}]",
                        fontSize = 12.sp, color = AutoScribeColors.Muted,
                    )
                } ?: Text(strings.t("no_reply"), fontSize = 12.sp, color = AutoScribeColors.Muted)
            }
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatItem(strings.t("stat_connected"), strings.num(okCount))
                StatItem(strings.t("stat_failed"), strings.num(failCount))
                StatItem(
                    strings.t("stat_rate"),
                    successRate?.let { "%.0f%%".format(it * 100) } ?: "–",
                    valueColor = successRate?.let { rateColor(it) } ?: AutoScribeColors.Text,
                )
                StatItem(
                    strings.t("stat_best"),
                    (bestPing ?: TestPlan.bestRoutes(results).firstOrNull()?.ping)?.let { "%.0f".format(it) } ?: "–",
                    valueColor = pingColor(bestPing ?: TestPlan.bestRoutes(results).firstOrNull()?.ping),
                )
                StatItem(strings.t("stat_avg"), avgConnect?.let { strings.t("secs", "v" to "%.1f".format(it)) } ?: "–")
            }
        }

        // Start / Stop.
        if (running) {
            Button(
                onClick = { vm.stopRun(context) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = AutoScribeColors.Warn,
                    contentColor = AutoScribeColors.OnAccent,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(strings.t("stop")) }
        } else {
            Button(
                onClick = ::onStartClicked,
                enabled = busyLabel == null,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(strings.t("start_n", "n" to strings.num(planTasks.size))) }
        }

        // Setup: locations.
        SectionCard(
            title = strings.t("locations"),
            subtitle = strings.t(
                "plan_summary",
                "s" to strings.num(selectedPops.size),
                "r" to strings.num(settings.portsInOrder.size),
                "n" to strings.num(importedInPlan),
            ),
        ) {
            Text(
                if (settings.selectedKeys.isEmpty()) {
                    strings.t("servers_in", "n" to strings.num(pops.size), "c" to strings.num(pops.map { it.country }.toSet().size))
                } else {
                    strings.t("n_selected", "n" to strings.num(selectedPops.size), "total" to strings.num(pops.size))
                },
                fontSize = 13.sp, color = AutoScribeColors.Text2,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { showPicker = true }, enabled = !running) {
                Text(strings.t("change"))
            }
        }

        // Setup: WireGuard ports.
        SectionCard(
            title = strings.t("ports_title", "p" to "WireGuard"),
            subtitle = strings.t("ports_hint"),
        ) {
            val ordered = settings.portsInOrder + WIREGUARD_PORTS.filter { it !in settings.portsInOrder }
            for (port in ordered) {
                val enabled = port in settings.portsInOrder
                val indexInOrder = settings.portsInOrder.indexOf(port)
                CheckRow(
                    label = "WireGuard : $port",
                    checked = enabled,
                    onCheckedChange = { checked ->
                        vm.updateSettings { s ->
                            if (checked) {
                                s.copy(
                                    portsInOrder = (s.portsInOrder + port)
                                        .sortedBy { WIREGUARD_PORTS.indexOf(it) },
                                )
                            } else {
                                s.copy(portsInOrder = s.portsInOrder - port)
                            }
                        }
                    },
                    trailing = {
                        if (enabled) {
                            IconButton(
                                enabled = indexInOrder > 0,
                                onClick = {
                                    vm.updateSettings { s ->
                                        val list = s.portsInOrder.toMutableList()
                                        val i = list.indexOf(port)
                                        if (i > 0) {
                                            list[i] = list[i - 1].also { list[i - 1] = list[i] }
                                        }
                                        s.copy(portsInOrder = list)
                                    }
                                },
                            ) { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "▲") }
                            IconButton(
                                enabled = indexInOrder < settings.portsInOrder.size - 1,
                                onClick = {
                                    vm.updateSettings { s ->
                                        val list = s.portsInOrder.toMutableList()
                                        val i = list.indexOf(port)
                                        if (i < list.size - 1) {
                                            list[i] = list[i + 1].also { list[i + 1] = list[i] }
                                        }
                                        s.copy(portsInOrder = list)
                                    }
                                },
                            ) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "▼") }
                        }
                    },
                )
            }
            Text(
                when (settings.portsInOrder.size) {
                    0 -> strings.t("ports_none")
                    WIREGUARD_PORTS.size -> strings.t("ports_all", "n" to settings.portsInOrder.size)
                    else -> strings.t("ports_some", "a" to settings.portsInOrder.size, "b" to WIREGUARD_PORTS.size)
                },
                fontSize = 12.sp, color = AutoScribeColors.Muted,
            )
        }

        // Setup: options.
        SectionCard(title = strings.t("options")) {
            ToggleRow(
                strings.t("opt_resume"), strings.t("opt_resume_tip"),
                settings.resume,
            ) { vm.updateSettings { it.copy(resume = it.resume.not()) } }
            ToggleRow(
                strings.t("opt_shuffle"), strings.t("opt_shuffle_tip"),
                settings.randomCities,
            ) { vm.updateSettings { it.copy(randomCities = it.randomCities.not()) } }
            ToggleRow(
                strings.t("opt_chaos"), strings.t("opt_chaos_tip"),
                settings.chaosMode,
            ) { vm.updateSettings { it.copy(chaosMode = it.chaosMode.not()) } }
            ToggleRow(
                strings.t("opt_stop"), strings.t("opt_stop_tip"),
                settings.stopOnSuccess,
            ) { vm.updateSettings { it.copy(stopOnSuccess = it.stopOnSuccess.not()) } }
            ToggleRow(
                strings.t("only_imported"), strings.t("only_imported_tip"),
                settings.onlyImported,
            ) { vm.updateSettings { it.copy(onlyImported = it.onlyImported.not()) } }
        }

        // Setup: ping & timing.
        SectionCard(
            title = strings.t("ping_timing"),
            subtitle = strings.t(
                "timing_summary",
                "host" to pingHostText.trim().ifEmpty { "?" },
                "count" to pingCountText.trim(),
                "timeout" to timeoutText.trim(),
            ),
        ) {
            TextFieldRow(strings.t("ping_host"), pingHostText, { pingHostText = it }, strings.t("ping_host_tip"))
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(strings.t("pings_per_test"), pingCountText, { pingCountText = it }, "1 – 20", Modifier.weight(1f))
                NumberField(strings.t("connect_timeout"), timeoutText, { timeoutText = it }, strings.t("connect_timeout_tip"), Modifier.weight(1f))
                NumberField(strings.t("settle_delay"), settleText, { settleText = it }, strings.t("settle_delay_tip"), Modifier.weight(1f))
            }
        }

        // Profiles.
        SectionCard(title = strings.t("profile")) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    settings.currentProfileName.ifEmpty { strings.t("choose_profile") },
                    modifier = Modifier.weight(1f),
                    color = AutoScribeColors.Text2, fontSize = 14.sp,
                )
                OutlinedButton(onClick = { profileMenuExpanded = true }) { Text(strings.t("choose_profile")) }
                DropdownMenu(expanded = profileMenuExpanded, onDismissRequest = { profileMenuExpanded = false }) {
                    if (profileNames.isEmpty()) {
                        DropdownMenuItem(text = { Text(strings.t("no_profiles")) }, onClick = { profileMenuExpanded = false })
                    }
                    for (name in profileNames) {
                        DropdownMenuItem(
                            text = { Text(name) },
                            onClick = {
                                profileMenuExpanded = false
                                vm.loadProfile(name)
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    profileNameInput = settings.currentProfileName
                    showSaveProfile = true
                }) { Text(strings.t("save_profile")) }
                if (settings.currentProfileName.isNotEmpty()) {
                    OutlinedButton(onClick = { showDeleteProfile = settings.currentProfileName }) {
                        Text(strings.t("delete_profile"))
                    }
                }
            }
            Text(strings.t("save_profile_hint"), fontSize = 12.sp, color = AutoScribeColors.Muted)
        }

        // What works here (per port).
        SectionCard(title = strings.t("works_title"), subtitle = strings.t("works_sub")) {
            if (portStats.isEmpty()) {
                Text(strings.t("works_empty"), fontSize = 13.sp, color = AutoScribeColors.Muted)
            } else {
                for (stat in portStats) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                        Text("WireGuard : ${stat.port}", fontSize = 13.sp, color = AutoScribeColors.Text, modifier = Modifier.weight(1f))
                        Text("${stat.ok}/${stat.total}", fontSize = 12.sp, color = AutoScribeColors.Muted, fontFamily = FontFamily.Monospace)
                        Spacer(Modifier.width(8.dp))
                        Text("%.0f%%".format(stat.rate * 100), fontSize = 12.sp, color = rateColor(stat.rate), fontFamily = FontFamily.Monospace)
                    }
                    LinearProgressIndicator(
                        progress = { stat.rate.toFloat() },
                        color = rateColor(stat.rate),
                        trackColor = AutoScribeColors.Border,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(4.dp))
                }
                if (weakPorts.isNotEmpty() && !running) {
                    val names = weakPorts.joinToString(strings.t("and")) { it.toString() }
                    Text(
                        strings.t(if (weakPorts.size == 1) "tip_one" else "tip_many", "names" to names),
                        fontSize = 12.sp, color = AutoScribeColors.WarnText,
                    )
                }
            }
        }

        // Log.
        SectionCard(title = strings.t("tab_log")) {
            Column(
                modifier = Modifier.fillMaxWidth().height(220.dp).verticalScroll(logScroll),
            ) {
                for (line in logLines.takeLast(60)) {
                    Text(
                        line.text,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = when (line.tag) {
                            "FAILURE" -> AutoScribeColors.BadText
                            "SUCCESS" -> AutoScribeColors.GoodText
                            "WARN" -> AutoScribeColors.WarnText
                            "HEADER" -> AutoScribeColors.ChipText
                            else -> AutoScribeColors.Text2
                        },
                    )
                }
                if (logLines.isEmpty()) {
                    Text(strings.t("press_start"), fontSize = 12.sp, color = AutoScribeColors.Muted)
                }
            }
        }

        Text(strings.t("battery_hint"), fontSize = 12.sp, color = AutoScribeColors.Muted)
        Spacer(Modifier.height(16.dp))
    }

    if (showPicker) {
        LocationPicker(
            pops = pops,
            initialSelected = settings.selectedKeys,
            strings = strings,
            onDone = { selected ->
                vm.updateSettings { it.copy(selectedKeys = selected) }
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }

    showFreshConfirm?.let { stored ->
        ConfirmDialog(
            title = strings.t("start_fresh"),
            text = strings.t("start_fresh_q", "n" to strings.num(stored)),
            confirmLabel = strings.t("start"),
            dismissLabel = strings.t("cancel"),
            onConfirm = {
                showFreshConfirm = null
                vm.startRun(clearFirst = true, context)
            },
            onDismiss = { showFreshConfirm = null },
        )
    }

    if (showSaveProfile) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showSaveProfile = false },
            title = { Text(strings.t("save_profile")) },
            text = {
                OutlinedTextField(
                    value = profileNameInput,
                    onValueChange = { profileNameInput = it },
                    label = { Text(strings.t("profile_name")) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = profileNameInput.isNotBlank(),
                    onClick = {
                        vm.saveProfile(profileNameInput)
                        showSaveProfile = false
                    },
                ) { Text(strings.t("save")) }
            },
            dismissButton = { TextButton(onClick = { showSaveProfile = false }) { Text(strings.t("cancel")) } },
            containerColor = AutoScribeColors.Card,
        )
    }

    showDeleteProfile?.let { name ->
        ConfirmDialog(
            title = strings.t("delete_profile"),
            text = strings.t("delete_profile_q", "n" to name),
            confirmLabel = strings.t("delete_profile"),
            dismissLabel = strings.t("cancel"),
            onConfirm = {
                vm.deleteProfile(name)
                showDeleteProfile = null
            },
            onDismiss = { showDeleteProfile = null },
        )
    }
}
