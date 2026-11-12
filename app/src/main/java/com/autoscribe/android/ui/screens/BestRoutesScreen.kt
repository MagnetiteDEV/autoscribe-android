package com.autoscribe.android.ui.screens

import android.widget.Toast
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.autoscribe.android.ui.components.PingDot
import com.autoscribe.android.ui.components.SectionCard
import com.autoscribe.android.ui.theme.AutoScribeColors
import com.autoscribe.android.ui.theme.pingColor

/**
 * Best-routes leaderboard, mirroring the desktop tab: SUCCESS rows with a
 * measured ping, ordered by ping, then loss, then connect time. Tapping a
 * row connects to it (the desktop double-click).
 */
@Composable
fun BestRoutesScreen(vm: MainViewModel, strings: Strings) {
    val context = LocalContext.current
    val results by vm.results.collectAsState()
    val running by EngineState.running.collectAsState()
    val busyLabel by EngineState.busyLabel.collectAsState()
    val best = remember(results) { TestPlan.bestRoutes(results) }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        if (best.isEmpty()) {
            Text(strings.t("empty_results"), color = AutoScribeColors.Muted, fontSize = 14.sp)
            return@Column
        }
        val top = best.first()
        SectionCard(
            title = strings.t("best_route_caption"),
            subtitle = strings.t("found_route"),
        ) {
            Text(top.displayName, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AutoScribeColors.Text)
            Text("${top.country} · ${top.route}", fontSize = 13.sp, color = AutoScribeColors.Muted)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "%.0f ${strings.t("ms")}".format(top.ping!!),
                    fontSize = 22.sp, fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace, color = pingColor(top.ping),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    "${strings.t("loss")} ${"%.0f%%".format(top.packetLoss ?: 0.0)} · ${"%.1f".format(top.duration)}s",
                    fontSize = 12.sp, color = AutoScribeColors.Muted,
                )
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    if (running || busyLabel != null) toast(strings.t("wait_run"))
                    else vm.connectOne(top, context)
                },
                enabled = !running && busyLabel == null,
            ) { Text(strings.t("connect_btn")) }
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            itemsIndexed(best, key = { _, r -> r.id }) { index, r ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (running || busyLabel != null) toast(strings.t("wait_run"))
                            else vm.connectOne(r, context)
                        }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${index + 1}",
                        fontSize = 13.sp, color = AutoScribeColors.Muted,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.width(28.dp),
                    )
                    PingDot(r.ping)
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(r.displayName, fontSize = 14.sp, color = AutoScribeColors.Text)
                        Text("${r.country} · ${r.route}", fontSize = 12.sp, color = AutoScribeColors.Muted)
                    }
                    Text(
                        "%.0f ${strings.t("ms")}".format(r.ping!!),
                        fontSize = 14.sp, fontFamily = FontFamily.Monospace,
                        color = pingColor(r.ping),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "%.0f%%".format(r.packetLoss ?: 0.0),
                        fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                        color = AutoScribeColors.Muted,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
