package com.autoscribe.android.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autoscribe.android.data.Strings
import com.autoscribe.android.engine.Phase
import com.autoscribe.android.engine.PhaseState
import com.autoscribe.android.ui.theme.AutoScribeColors
import com.autoscribe.android.ui.theme.pingColor

@Composable
fun SectionCard(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AutoScribeColors.Card),
        border = BorderStroke(1.dp, AutoScribeColors.Border),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = AutoScribeColors.Text)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, fontSize = 12.sp, color = AutoScribeColors.Muted)
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
fun ToggleRow(
    label: String,
    hint: String?,
    checked: Boolean,
    modifier: Modifier = Modifier,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 14.sp, color = AutoScribeColors.Text)
            if (hint != null) {
                Text(hint, fontSize = 12.sp, color = AutoScribeColors.Muted)
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun CheckRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label, fontSize = 14.sp, color = AutoScribeColors.Text, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
fun NumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label, fontSize = 12.sp) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AutoScribeColors.Accent,
                unfocusedBorderColor = AutoScribeColors.Border2,
                focusedLabelColor = AutoScribeColors.ChipText,
                unfocusedLabelColor = AutoScribeColors.Muted,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(hint, fontSize = 11.sp, color = AutoScribeColors.Muted, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
fun TextFieldRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label, fontSize = 12.sp) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AutoScribeColors.Accent,
                unfocusedBorderColor = AutoScribeColors.Border2,
                focusedLabelColor = AutoScribeColors.ChipText,
                unfocusedLabelColor = AutoScribeColors.Muted,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(hint, fontSize = 11.sp, color = AutoScribeColors.Muted, modifier = Modifier.padding(top = 2.dp))
    }
}

/** The four desktop phase chips: disconnect -> connect -> settle -> ping. */
@Composable
fun PhaseRow(
    states: Map<Phase, PhaseState>,
    secs: Map<Phase, Double?>,
    strings: Strings,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (phase in Phase.entries) {
            val state = states[phase] ?: PhaseState.IDLE
            val (bg, fg) = when (state) {
                PhaseState.ACTIVE -> AutoScribeColors.AccentSoft to AutoScribeColors.ChipText
                PhaseState.DONE -> AutoScribeColors.GoodSoft to AutoScribeColors.GoodText
                PhaseState.FAIL -> AutoScribeColors.BadSoft to AutoScribeColors.BadText
                PhaseState.SKIP -> AutoScribeColors.Input to AutoScribeColors.Dim
                PhaseState.IDLE -> AutoScribeColors.Input to AutoScribeColors.Muted
            }
            val key = when (phase) {
                Phase.DISCONNECT -> "phase_disconnect"
                Phase.CONNECT -> "phase_connect"
                Phase.SETTLE -> "phase_settle"
                Phase.PING -> "phase_ping"
            }
            val label = strings.t(key)
            val seconds = secs[phase]
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(bg)
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(label, fontSize = 12.sp, color = fg, fontWeight = FontWeight.Medium)
                    if (seconds != null && state != PhaseState.IDLE) {
                        Text(
                            "%.1fs".format(seconds),
                            fontSize = 11.sp,
                            color = fg,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PingDot(ping: Double?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(pingColor(ping)),
    )
}

@Composable
fun StatusChip(status: String, strings: Strings, modifier: Modifier = Modifier) {
    val (bg, fg) = when (status) {
        "SUCCESS" -> AutoScribeColors.GoodSoft to AutoScribeColors.GoodText
        "INVALID_LOC" -> AutoScribeColors.WarnSoft to AutoScribeColors.WarnText
        else -> AutoScribeColors.BadSoft to AutoScribeColors.BadText
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(strings.t("status_$status"), fontSize = 12.sp, color = fg)
    }
}

@Composable
fun StatItem(label: String, value: String, modifier: Modifier = Modifier, valueColor: androidx.compose.ui.graphics.Color = AutoScribeColors.Text) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = valueColor)
        Text(label, fontSize = 11.sp, color = AutoScribeColors.Muted)
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismissLabel) } },
        containerColor = AutoScribeColors.Card,
        titleContentColor = AutoScribeColors.Text,
        textContentColor = AutoScribeColors.Text2,
    )
}

@Composable
fun MonoText(text: String, fontSize: Int = 13, modifier: Modifier = Modifier) {
    Text(
        text,
        fontSize = fontSize.sp,
        fontFamily = FontFamily.Monospace,
        color = AutoScribeColors.Text,
        modifier = modifier,
    )
}
