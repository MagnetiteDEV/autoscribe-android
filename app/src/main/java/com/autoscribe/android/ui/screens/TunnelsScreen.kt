package com.autoscribe.android.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.stickyHeader
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autoscribe.android.EngineState
import com.autoscribe.android.MainViewModel
import com.autoscribe.android.data.Strings
import com.autoscribe.android.engine.REGIONS
import com.autoscribe.android.engine.TestTask
import com.autoscribe.android.engine.TunnelNaming
import com.autoscribe.android.engine.matchesRegion
import com.autoscribe.android.ui.components.MonoText
import com.autoscribe.android.ui.components.SectionCard
import com.autoscribe.android.ui.theme.AutoScribeColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Tunnel setup: WireGuard readiness, the naming contract, the imported
 * checklist, per-tunnel probing, and the server-list import.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TunnelsScreen(
    vm: MainViewModel,
    strings: Strings,
    onRequestWgPermission: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val settings by vm.settings.collectAsState()
    val pops by vm.pops.collectAsState()
    val imported by vm.imported.collectAsState()
    val importedListAt by vm.importedListAt.collectAsState()
    val wgInstalled by vm.wgInstalled.collectAsState()
    val wgPermission by vm.wgPermission.collectAsState()
    val running by EngineState.running.collectAsState()
    val busyLabel by EngineState.busyLabel.collectAsState()

    var query by remember { mutableStateOf("") }
    var region by remember { mutableStateOf("All") }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { vm.importLocations(it, context) } }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    val selectedPops = remember(pops, settings.selectedKeys) {
        if (settings.selectedKeys.isEmpty()) pops else pops.filter { it.key in settings.selectedKeys }
    }
    val tasks = remember(selectedPops, settings.portsInOrder) {
        selectedPops.flatMap { pop -> settings.portsInOrder.map { TestTask(pop, it) } }
    }
    val filtered = remember(tasks, query, region) {
        val q = query.trim().lowercase()
        tasks.filter { task ->
            matchesRegion(task.pop, region) &&
                (q.isEmpty() || task.pop.country.lowercase().contains(q) ||
                    task.pop.city.lowercase().contains(q) ||
                    task.pop.node.lowercase().contains(q) ||
                    task.tunnelName.lowercase().contains(q))
        }
    }
    val grouped = remember(filtered) { filtered.groupBy { it.pop.country }.toSortedMap() }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
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

        item {
            SectionCard(title = strings.t("tunnel_setup_title")) {
                Text(strings.t("tunnel_howto"), fontSize = 13.sp, color = AutoScribeColors.Text2)
                Spacer(Modifier.height(8.dp))
                Text(
                    if (importedListAt > 0) {
                        strings.t(
                            "list_source_imported",
                            "d" to SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(importedListAt)),
                        )
                    } else {
                        strings.t("list_source_builtin")
                    },
                    fontSize = 12.sp, color = AutoScribeColors.Muted,
                )
                Spacer(Modifier.height(4.dp))
                Text(strings.t("import_list_hint"), fontSize = 12.sp, color = AutoScribeColors.Muted)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) }) {
                    Text(strings.t("import_list"))
                }
            }
        }

        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(strings.t("search_tunnels")) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AutoScribeColors.Accent,
                    unfocusedBorderColor = AutoScribeColors.Border2,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            ) {
                for (r in REGIONS) {
                    FilterChip(
                        selected = region == r,
                        onClick = { region = r },
                        label = { Text(strings.region(r), fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AutoScribeColors.Selected,
                            selectedLabelColor = AutoScribeColors.ChipText,
                        ),
                    )
                }
            }
        }

        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                OutlinedButton(
                    onClick = { vm.setImportedMany(filtered.map { it.tunnelName }, true) },
                    enabled = filtered.isNotEmpty(),
                ) { Text(strings.t("tunnel_mark_all_shown"), fontSize = 12.sp) }
                OutlinedButton(
                    onClick = { vm.setImportedMany(filtered.map { it.tunnelName }, false) },
                    enabled = filtered.isNotEmpty(),
                ) { Text(strings.t("tunnel_clear_shown"), fontSize = 12.sp) }
                OutlinedButton(
                    onClick = {
                        val names = filtered.map { TunnelNaming.configFileName(it.pop, it.port) }
                        clipboard.setText(AnnotatedString(names.joinToString("\n")))
                        toast(strings.t("tunnel_names_copied", "n" to strings.num(names.size)))
                    },
                    enabled = filtered.isNotEmpty(),
                ) { Text(strings.t("tunnel_copy_names"), fontSize = 12.sp) }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                strings.t(
                    "n_selected",
                    "n" to strings.num(filtered.count { it.tunnelName in imported }),
                    "total" to strings.num(filtered.size),
                ),
                fontSize = 12.sp, color = AutoScribeColors.Muted,
            )
        }

        if (filtered.isEmpty()) {
            item {
                Text(strings.t("no_tunnels_shown"), color = AutoScribeColors.Muted, fontSize = 14.sp)
            }
        }

        for ((country, countryTasks) in grouped) {
            stickyHeader {
                Row(
                    modifier = Modifier.fillMaxWidth().background(AutoScribeColors.Sidebar).padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "$country (${countryTasks.size})",
                        fontSize = 14.sp, color = AutoScribeColors.Text,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "${countryTasks.count { it.tunnelName in imported }}/${countryTasks.size}",
                        fontSize = 12.sp, color = AutoScribeColors.Muted,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
            items(countryTasks, key = { it.tunnelName }) { task ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = task.tunnelName in imported,
                        onCheckedChange = { checked -> vm.setImported(task.tunnelName, checked) },
                    )
                    Column(
                        modifier = Modifier.weight(1f).clickable {
                            clipboard.setText(AnnotatedString(task.tunnelName))
                            toast(strings.t("copied"))
                        },
                    ) {
                        MonoText(task.tunnelName)
                        Text(
                            "${task.pop.displayName} · ${task.pop.country}",
                            fontSize = 11.sp, color = AutoScribeColors.Muted,
                        )
                    }
                    IconButton(
                        enabled = !running && busyLabel == null,
                        onClick = {
                            if (running || busyLabel != null) {
                                toast(strings.t("wait_run"))
                            } else {
                                toast(strings.t("tunnel_probe_running", "x" to task.tunnelName))
                                vm.probe(task, context)
                            }
                        },
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = strings.t("tunnel_probe"))
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            Text(strings.t("battery_hint"), fontSize = 12.sp, color = AutoScribeColors.Muted)
        }
    }
}
