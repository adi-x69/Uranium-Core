package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MistText
import com.example.ui.theme.MistTextMuted
import com.example.ui.theme.VoidBlack

/** Port of the extension popup: Pause/Resume switch + editable custom header list. */
@Composable
fun HeaderSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    remember { HeaderSettings.ensureLoaded(context) }

    var enabled by remember { mutableStateOf(HeaderSettings.enabled) }
    val rows = remember { mutableStateListOf<CustomHeader>().apply { addAll(HeaderSettings.customHeaders) } }
    var saved by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VoidBlack)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = MistText)
            }
            Text("Header Rules", color = MistText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Status: ${if (enabled) "Active" else "Paused"}",
                color = MistText,
                fontWeight = FontWeight.Bold
            )
            Switch(
                checked = enabled,
                onCheckedChange = {
                    enabled = it
                    HeaderSettings.setEnabled(context, it)
                }
            )
        }

        Spacer(Modifier.height(16.dp))
        Text("Built-in referer rules", color = MistText, fontWeight = FontWeight.Bold)
        AppConfig.REFERER_RULES.forEach { (key, referer) ->
            Text("$key  ->  $referer", color = MistTextMuted, fontSize = 12.sp)
        }

        Spacer(Modifier.height(20.dp))
        Text("Custom headers (added to every video request)", color = MistText, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        rows.forEachIndexed { index, row ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = row.name,
                    onValueChange = { rows[index] = row.copy(name = it); saved = false },
                    label = { Text("Header") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = row.value,
                    onValueChange = { rows[index] = row.copy(value = it); saved = false },
                    label = { Text("Value") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { rows.removeAt(index); saved = false }) {
                    Icon(Icons.Filled.Close, contentDescription = "Remove", tint = MistText)
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { rows.add(CustomHeader("", "")); saved = false }) {
                Text("Add Header")
            }
            Button(onClick = {
                HeaderSettings.saveHeaders(context, rows.toList())
                saved = true
            }) {
                Text("Save Headers")
            }
        }
        if (saved) {
            Spacer(Modifier.height(8.dp))
            Text("Headers saved and applied.", color = MistTextMuted, fontSize = 12.sp)
        }
    }
}
