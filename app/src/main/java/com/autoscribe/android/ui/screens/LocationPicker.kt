package com.autoscribe.android.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.autoscribe.android.data.Strings
import com.autoscribe.android.engine.Pop
import com.autoscribe.android.engine.REGIONS
import com.autoscribe.android.engine.matchesRegion
import com.autoscribe.android.ui.theme.AutoScribeColors

/**
 * Location picker, mirroring the desktop dialog: search, region filter,
 * per-country and per-server checkboxes. Desktop semantics preserved:
 * an empty selection means every server.
 */
@Composable
fun LocationPicker(
    pops: List<Pop>,
    initialSelected: Set<String>,
    strings: Strings,
    onDone: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var region by remember { mutableStateOf("All") }
    // Draft as an effective set: empty initial selection means "all".
    var draft by remember {
        mutableStateOf(
            if (initialSelected.isEmpty()) pops.map { it.key }.toSet() else initialSelected,
        )
    }
    val allKeys = remember(pops) { pops.map { it.key }.toSet() }

    val filtered = remember(pops, query, region) {
        val q = query.trim().lowercase()
        pops.filter { pop ->
            matchesRegion(pop, region) &&
                (q.isEmpty() || pop.country.lowercase().contains(q) ||
                    pop.city.lowercase().contains(q) || pop.node.lowercase().contains(q))
        }
    }
    val grouped = remember(filtered) { filtered.groupBy { it.country }.toSortedMap() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = AutoScribeColors.Bg,
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        strings.t("choose_locations"),
                        fontSize = 18.sp,
                        color = AutoScribeColors.Text,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = strings.t("cancel"))
                    }
                }
                Text(
                    strings.t("picker_hint"),
                    fontSize = 12.sp,
                    color = AutoScribeColors.Muted,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(strings.t("search_locations")) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AutoScribeColors.Accent,
                        unfocusedBorderColor = AutoScribeColors.Border2,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
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
                Spacer(Modifier.height(8.dp))
                if (filtered.isEmpty()) {
                    Text(strings.t("no_match_locations"), color = AutoScribeColors.Muted)
                }
                LazyColumn(modifier = Modifier.weight(1f)) {
                    for ((country, countryPops) in grouped) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(AutoScribeColors.Sidebar)
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                val countryKeys = countryPops.map { it.key }.toSet()
                                val allChecked = countryKeys.all { it in draft }
                                Checkbox(
                                    checked = allChecked,
                                    onCheckedChange = { checked ->
                                        draft = if (checked) draft + countryKeys else draft - countryKeys
                                    },
                                )
                                Text(
                                    "$country (${countryPops.size})",
                                    fontSize = 14.sp,
                                    color = AutoScribeColors.Text,
                                )
                            }
                        }
                        items(countryPops, key = { it.key }) { pop ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(start = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = pop.key in draft,
                                    onCheckedChange = { checked ->
                                        draft = if (checked) draft + pop.key else draft - pop.key
                                    },
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(pop.displayName, fontSize = 14.sp, color = AutoScribeColors.Text)
                                    Text(
                                        pop.continent +
                                            (if (pop.premium) " · ★" else "") +
                                            (if (pop.p2p) " · P2P" else ""),
                                        fontSize = 11.sp,
                                        color = AutoScribeColors.Muted,
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    if (draft.isEmpty() || draft.size == allKeys.size) {
                        // Empty draft means "all" (desktop semantics).
                        strings.t("nothing_selected", "total" to strings.num(allKeys.size))
                    } else {
                        strings.t("n_selected", "n" to strings.num(draft.size), "total" to strings.num(allKeys.size))
                    },
                    fontSize = 12.sp,
                    color = AutoScribeColors.Muted,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { draft = draft + filtered.map { it.key } }) {
                        Text(strings.t("select_shown"))
                    }
                    TextButton(onClick = { draft = emptySet() }) {
                        Text(strings.t("select_none"))
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            // Empty draft (via "None") means "all" on save,
                            // exactly like the desktop dialog.
                            onDone(if (draft.size == allKeys.size || draft.isEmpty()) emptySet() else draft)
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(strings.t("done"))
                    }
                }
            }
        }
    }
}
