package com.autoscribe.android.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.autoscribe.android.engine.TestResult
import com.autoscribe.android.ui.components.ConfirmDialog
import com.autoscribe.android.ui.components.PingDot
import com.autoscribe.android.ui.components.StatusChip
import com.autoscribe.android.ui.theme.AutoScribeColors
import com.autoscribe.android.ui.theme.pingColor

@Composable
fun ResultsScreen(vm: MainViewModel, strings: Strings) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val results by vm.results.collectAsState()
    val running by EngineState.running.collectAsState()
    val busyLabel by EngineState.busyLabel.collectAsState()

    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("all") } // all | success | failed
    var sort by remember { mutableStateOf("newest") } // newest | ping | city | port
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<TestResult?>(null) }
    var showClearConfirm by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri -> uri?.let { vm.exportCsv(it, context) } }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    val shown = remember(results, query, filter, sort) {
        val q = query.trim().lowercase()
        var list = results.filter { r ->
            (filter == "all" || (filter == "success") == r.isSuccess) &&
                (q.isEmpty() || r.city.lowercase().contains(q) ||
                    r.location.lowercase().contains(q) ||
                    r.country.lowercase().contains(q) ||
                    r.protocol.lowercase().contains(q))
        }
        list = when (sort) {
            "ping" -> list.sortedWith(
                compareBy<TestResult> { it.ping ?: Double.MAX_VALUE }
                    .thenBy { it.packetLoss ?: 0.0 },
            )
            "city" -> list.sortedWith(compareBy({ it.country }, { it.city }, { it.location }))
            "port" -> list.sortedWith(compareBy({ it.port }, { it.city }))
            else -> list.sortedByDescending { it.id }
        }
        list
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(strings.t("search_results")) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AutoScribeColors.Accent,
                unfocusedBorderColor = AutoScribeColors.Border2,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            for ((key, labelKey) in listOf("all" to "filter_all", "success" to "filter_connected", "failed" to "filter_failed")) {
                FilterChip(
                    selected = filter == key,
                    onClick = { filter = key },
                    label = { Text(strings.t(labelKey), fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AutoScribeColors.Selected,
                        selectedLabelColor = AutoScribeColors.ChipText,
                    ),
                )
            }
            Spacer(Modifier.width(4.dp))
            OutlinedButton(onClick = { sortMenuExpanded = true }) {
                Text(
                    strings.t("sort_label") + ": " + strings.t(
                        when (sort) {
                            "ping" -> "sort_ping"
                            "city" -> "sort_city"
                            "port" -> "sort_port"
                            else -> "sort_newest"
                        },
                    ),
                    fontSize = 12.sp,
                )
            }
            DropdownMenu(expanded = sortMenuExpanded, onDismissRequest = { sortMenuExpanded = false }) {
                for ((key, labelKey) in listOf(
                    "newest" to "sort_newest", "ping" to "sort_ping",
                    "city" to "sort_city", "port" to "sort_port",
                )) {
                    DropdownMenuItem(
                        text = { Text(strings.t(labelKey)) },
                        onClick = { sort = key; sortMenuExpanded = false },
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { exportLauncher.launch(vm.prepareExportFileName()) }, enabled = results.isNotEmpty()) {
                Text(strings.t("export_csv"))
            }
            OutlinedButton(onClick = { showClearConfirm = true }, enabled = results.isNotEmpty()) {
                Text(strings.t("clear_results"))
            }
            Spacer(Modifier.weight(1f))
            Text(
                strings.num(results.size),
                fontSize = 13.sp, color = AutoScribeColors.Muted,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
        }
        Spacer(Modifier.height(8.dp))
        if (shown.isEmpty()) {
            Text(
                if (results.isEmpty()) strings.t("empty_results") else strings.t("empty_filter"),
                color = AutoScribeColors.Muted, fontSize = 14.sp,
            )
        }
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(shown, key = { it.id }) { r ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selected = r }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PingDot(r.ping)
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(r.displayName, fontSize = 14.sp, color = AutoScribeColors.Text)
                        Text(
                            "${r.country} · ${r.route}",
                            fontSize = 12.sp, color = AutoScribeColors.Muted,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            r.ping?.let { "%.0f ${strings.t("ms")}".format(it) } ?: "–",
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace,
                            color = pingColor(r.ping),
                        )
                        Text(
                            "${strings.t("loss")} ${r.packetLoss?.let { "%.0f%%".format(it) } ?: "–"} · ${"%.1f".format(r.duration)}s",
                            fontSize = 11.sp, color = AutoScribeColors.Muted,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    StatusChip(r.status, strings)
                }
            }
        }
    }

    selected?.let { r ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(r.displayName) },
            text = {
                Text(
                    "${r.country} · ${r.route}\n" +
                        "${strings.t("col_result")}: ${strings.t("status_${r.status}")}\n" +
                        "${strings.t("col_ping")}: ${r.ping?.let { "%.0f".format(it) } ?: "–"} ${strings.t("ms")}\n" +
                        "${strings.t("col_loss")}: ${r.packetLoss?.let { "%.0f%%".format(it) } ?: "–"}\n" +
                        "${strings.t("connect_time")}: ${"%.2f".format(r.duration)}s\n" +
                        r.timestamp,
                    fontSize = 13.sp,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        selected = null
                        if (running || busyLabel != null) {
                            toast(strings.t("wait_run"))
                        } else {
                            vm.connectOne(r, context)
                        }
                    },
                ) { Text(strings.t("menu_connect")) }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            clipboard.setText(AnnotatedString("${r.displayName} ${r.route}"))
                            toast(strings.t("copied"))
                        },
                    ) { Text(strings.t("menu_copy_route")) }
                    TextButton(
                        onClick = {
                            clipboard.setText(
                                AnnotatedString(
                                    "${r.displayName} | ${r.country} | ${r.route} | ${r.status} | " +
                                        "ping ${r.ping ?: "-"} | loss ${r.packetLoss ?: "-"} | ${r.duration}s",
                                ),
                            )
                            toast(strings.t("copied"))
                        },
                    ) { Text(strings.t("menu_copy_row")) }
                }
            },
            containerColor = AutoScribeColors.Card,
        )
    }

    if (showClearConfirm) {
        ConfirmDialog(
            title = strings.t("clear_results"),
            text = strings.t("clear_results_q", "n" to strings.num(results.size)),
            confirmLabel = strings.t("clear"),
            dismissLabel = strings.t("cancel"),
            onConfirm = {
                vm.clearResults()
                showClearConfirm = false
            },
            onDismiss = { showClearConfirm = false },
        )
    }
}
