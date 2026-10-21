package com.cadence.player

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.util.Locale

private fun bandLabel(f: Float): String =
    if (f >= 1000f) "${(f / 1000f).toInt()}k" else "${f.toInt()}"

@Composable
fun SoundScreen(vm: PlayerVm) {
    val c = cadence
    val ctx = LocalContext.current
    var importError by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            val text = try {
                ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
            } catch (_: Exception) { "" }
            val name = uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.') ?: "AutoEq"
            importError = !vm.importAutoEq(name, text)
        }
    }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = 150.dp)
    ) {
        Text("Sound", style = T.largeTitle, color = c.label,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp))

        Card {
            ToggleRow("Equaliser", vm.eqEnabled) { vm.setEqEnabled(it) }
            Divider()
            ToggleRow("AutoEQ profile", vm.autoEqEnabled && vm.autoEqName.isNotEmpty(), enabled = vm.autoEqName.isNotEmpty()) {
                vm.setAutoEqEnabled(it)
            }
            Divider()
            Row(
                Modifier.fillMaxWidth().clickable { picker.launch(arrayOf("text/plain", "*/*")) }.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Import AutoEQ file", style = T.body, color = c.accent)
                    Text(
                        if (vm.autoEqName.isNotEmpty()) vm.autoEqName else "Choose a ParametricEQ.txt from AutoEq",
                        style = T.footnote, color = c.secondaryLabel
                    )
                }
                if (vm.autoEqName.isNotEmpty()) {
                    Text("Remove", style = T.body, color = c.secondaryLabel,
                        modifier = Modifier.clickable { vm.clearAutoEq() }.padding(start = 12.dp))
                }
            }
        }
        if (importError) {
            Text("Couldn't read any filters from that file. Use an AutoEq \"ParametricEQ.txt\".",
                style = T.footnote, color = c.accent, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
        }

        SectionHeader("Your tweaks")
        Card {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Preamp", style = T.body, color = c.label, modifier = Modifier.weight(1f))
                    Text(String.format(Locale.US, "%+.1f dB", vm.preamp), style = T.subhead, color = c.secondaryLabel)
                }
                Slider(
                    value = vm.preamp, onValueChange = { vm.setPreamp(it) },
                    onValueChangeFinished = { vm.bandsCommitted() },
                    valueRange = -12f..6f,
                    colors = sliderColors()
                )
                Text("Boosts are compensated automatically so the signal can't clip.",
                    style = T.footnote, color = c.secondaryLabel)
            }
        }
        Spacer(Modifier.height(12.dp))
        Card {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                for (i in EqSettings.bandFreqs.indices) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(bandLabel(EqSettings.bandFreqs[i]), style = T.footnote, color = c.secondaryLabel,
                            modifier = Modifier.width(34.dp))
                        Slider(
                            value = vm.bands[i], onValueChange = { vm.setBand(i, it) },
                            onValueChangeFinished = { vm.bandsCommitted() },
                            valueRange = -12f..12f,
                            colors = sliderColors(),
                            modifier = Modifier.weight(1f)
                        )
                        Text(String.format(Locale.US, "%+.1f", vm.bands[i]), style = T.footnote, color = c.secondaryLabel,
                            modifier = Modifier.width(44.dp))
                    }
                }
                Text("Reset bands", style = T.body, color = c.accent,
                    modifier = Modifier.clickable { vm.resetBands() }.padding(top = 6.dp, bottom = 6.dp))
            }
        }

        SectionHeader("Output")
        Card {
            Column(Modifier.padding(16.dp)) {
                Text("32-bit float pipeline", style = T.headline, color = c.label)
                Spacer(Modifier.height(4.dp))
                Text(
                    "24-bit and 32-bit files are decoded to 32-bit float and never reduced to 16-bit before the " +
                        "equaliser. Android's mixer still resamples to your output device's rate, usually 48 kHz.",
                    style = T.footnote, color = c.secondaryLabel
                )
            }
        }
    }
}

@Composable
private fun sliderColors() = SliderDefaults.colors(
    thumbColor = cadence.label,
    activeTrackColor = cadence.accent,
    inactiveTrackColor = cadence.fill
)

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(cadence.elevated)
    ) { content() }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    val c = cadence
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = T.body, color = if (enabled) c.label else c.secondaryLabel, modifier = Modifier.weight(1f))
        Switch(
            checked = checked, onCheckedChange = onChange, enabled = enabled,
            colors = SwitchDefaults.colors(checkedTrackColor = androidx.compose.ui.graphics.Color(0xFF34C759),
                checkedThumbColor = androidx.compose.ui.graphics.Color.White)
        )
    }
}
