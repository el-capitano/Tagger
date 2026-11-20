package com.whoisthis.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

@Composable
internal fun DisplayControls(value: DisplayOptions, onChange: (DisplayOptions) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.verticalScroll(rememberScrollState())) {
        Text("On screen", fontSize = 23.sp, fontWeight = FontWeight.Medium)
        Text("Choose the detectors and information to show.", Modifier.padding(top = 6.dp, bottom = 14.dp),
            color = Palette.Muted, fontSize = 13.sp)
        OptionSwitch("Face recognition", "Match your registered people", value.recognizeFaces) { onChange(value.copy(recognizeFaces = it)) }
        OptionSwitch("Object identification", "People, vehicles, objects and scene labels", value.identifyObjects) { onChange(value.copy(identifyObjects = it)) }
        OptionSwitch("People counter", "Everyone visible, including unregistered people", value.countPeople) { onChange(value.copy(countPeople = it)) }
        OptionSwitch("Recognized faces counter", "Show the number of matched faces", value.showRecognizedCount) { onChange(value.copy(showRecognizedCount = it)) }
        HorizontalDivider(Modifier.padding(vertical = 10.dp), color = Palette.Border)
        OptionSwitch("Bounding boxes", "Outline detected faces and objects", value.showBoxes) { onChange(value.copy(showBoxes = it)) }
        OptionSwitch("Names and labels", "Show identity panels and object names", value.showLabels) { onChange(value.copy(showLabels = it)) }
        OptionSwitch("Profile details", "Role, notes and user-entered attributes", value.showDetails) { onChange(value.copy(showDetails = it)) }
        OptionSwitch("Model scores", "Similarity and object detection scores", value.showScores) { onChange(value.copy(showScores = it)) }
    }
}

@Composable
private fun OptionSwitch(title: String, detail: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(detail, Modifier.padding(top = 3.dp), color = Palette.Muted, fontSize = 11.sp)
        }
        Switch(checked = value, onCheckedChange = onChange, modifier = Modifier.semantics { contentDescription = title })
    }
}

@Composable
internal fun ControlsScreen(state: AppState, onThreshold: (Float) -> Unit, onDisplay: (DisplayOptions) -> Unit,
    modifier: Modifier = Modifier) {
    var threshold by remember(state.threshold) { mutableFloatStateOf(state.threshold) }
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        Text("Detection controls", fontSize = 27.sp, fontWeight = FontWeight.Medium)
        Surface(color = Palette.Surface, shape = RoundedCornerShape(16.dp), modifier = Modifier.padding(top = 20.dp)) {
            Column(Modifier.padding(18.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Face match threshold", fontWeight = FontWeight.Medium)
                    Text(String.format(Locale.US, "%.2f", threshold), color = Palette.Accent)
                }
                Text("Higher values require a closer match. Test with your own camera and people.",
                    Modifier.padding(top = 10.dp), color = Palette.Muted, fontSize = 12.sp)
                Slider(value = threshold, onValueChange = { threshold = it }, valueRange = 0.6f..0.9f,
                    onValueChangeFinished = { onThreshold(threshold) })
                TextButton(onClick = { onThreshold(FaceMatcher.DEFAULT_THRESHOLD) }) { Text("Reset to 0.72") }
            }
        }
        DisplayControls(state.display, onDisplay, Modifier.padding(top = 24.dp).heightIn(max = 680.dp))
        HorizontalDivider(Modifier.padding(vertical = 20.dp), color = Palette.Border)
        Text("How the results work", fontWeight = FontWeight.Medium)
        Text("Appearance labels describe samples you capture; they are not emotion predictions. Gender and age category are optional profile details, never guessed from images. Unknown people are labeled Person.",
            Modifier.padding(top = 10.dp), color = Palette.Muted, fontSize = 12.sp)
        Text("The people counter estimates how many people are visible now, using body detections and faces without counting the same detection twice. It does not track unique visitors. Small, hidden or overlapping people can be missed. A + means the object model reached its result limit.",
            Modifier.padding(top = 12.dp), color = Palette.Muted, fontSize = 12.sp)
        Text("Object detection covers common COCO categories. Buildings and indoor settings appear as scene labels, without a localized box. Scores are model outputs, not accuracy percentages. Photos or screens can fool recognition; this version has no liveness verification.",
            Modifier.padding(top = 12.dp), color = Palette.Muted, fontSize = 12.sp)
        Text("Tagger v1.2.1 / Encrypted profiles stay on this device. Camera images are not saved.", Modifier.padding(vertical = 22.dp),
            color = Palette.Muted, fontSize = 11.sp)
    }
}
