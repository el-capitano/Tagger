package com.whoisthis.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun PeopleScreen(people: List<Person>, onRegister: (String?) -> Unit, onEdit: (Person) -> Unit,
    onDelete: (Person) -> Unit, onDeleteAppearance: (String, String) -> Unit, modifier: Modifier = Modifier) {
    var expandedId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<Person?>(null) }
    var removing by remember { mutableStateOf<Pair<Person, AppearanceProfile>?>(null) }
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Your people", Modifier.weight(1f), fontSize = 27.sp, fontWeight = FontWeight.Medium)
            FilledTonalButton(onClick = { onRegister(null) }) { Text("New person") }
        }
        Text("${people.size} people saved. Multiple appearances, one identity.",
            Modifier.padding(top = 6.dp, bottom = 18.dp), color = Palette.Muted, fontSize = 12.sp)
        if (people.isEmpty()) CenterMessage(Symbol.People, "Put a name to a face",
            "Register a person, then add appearances such as Normal, Smiling or Angry.", Modifier.weight(1f))
        else LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(people, key = { it.id }) { person ->
                Surface(color = Palette.Surface, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Palette.Border)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(42.dp).background(Palette.Raised, CircleShape), contentAlignment = Alignment.Center) {
                                Text(person.name.take(1).uppercase(), color = Palette.Accent, fontSize = 21.sp)
                            }
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(person.name, fontSize = 17.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (person.description.isNotBlank()) Text(person.description, color = Palette.Muted, fontSize = 12.sp,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text("${person.appearances.size} appearances / ${person.templates.size} samples",
                                    Modifier.padding(top = 5.dp), color = Palette.Accent, fontSize = 11.sp)
                            }
                            IconButton(onClick = { deleting = person }, modifier = Modifier.semantics { contentDescription = "Delete ${person.name}" }) {
                                Glyph(Symbol.Delete, Palette.Muted)
                            }
                        }
                        if (person.notes.isNotBlank()) Text(person.notes, Modifier.padding(top = 12.dp), color = Palette.Muted,
                            fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            TextButton(onClick = { onRegister(person.id) }, enabled = person.appearances.size < FaceMatcher.MAX_APPEARANCES) { Text("Add appearance", fontSize = 12.sp) }
                            TextButton(onClick = { expandedId = if (expandedId == person.id) null else person.id }) {
                                Text(if (expandedId == person.id) "Hide" else "Manage", fontSize = 12.sp)
                            }
                        }
                        if (expandedId == person.id) {
                            HorizontalDivider(color = Palette.Border)
                            person.appearances.forEach { appearance ->
                                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(appearance.label, fontSize = 14.sp)
                                        Text("${appearance.templates.size} samples", color = Palette.Muted, fontSize = 11.sp)
                                    }
                                    IconButton(onClick = { removing = person to appearance }, enabled = person.appearances.size > 1,
                                        modifier = Modifier.semantics { contentDescription = "Delete ${appearance.label} appearance" }) {
                                        Glyph(Symbol.Delete, Palette.Muted, Modifier.size(18.dp))
                                    }
                                }
                            }
                            TextButton(onClick = { onEdit(person) }) { Text("Edit personal details") }
                            if (person.appearances.size == 1) Text("Keep one appearance, or delete the person to remove all samples.",
                                color = Palette.Muted, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
    deleting?.let { person ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Delete ${person.name}?") },
            text = { Text("This removes their details and every appearance from this device.") },
            confirmButton = { TextButton(onClick = { onDelete(person); deleting = null }) { Text("Delete person", color = Palette.Error) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Keep person") } })
    }
    removing?.let { (person, appearance) ->
        AlertDialog(onDismissRequest = { removing = null }, title = { Text("Delete ${appearance.label}?") },
            text = { Text("Other appearances for ${person.name} will stay registered.") },
            confirmButton = { TextButton(onClick = { onDeleteAppearance(person.id, appearance.id); removing = null }) { Text("Delete appearance", color = Palette.Error) } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } })
    }
}

@Composable
internal fun RegistrationDialog(person: Person?, onDismiss: () -> Unit, onStart: (EnrollmentSession) -> Unit) {
    var name by rememberSaveable(person?.id) { mutableStateOf(person?.name.orEmpty()) }
    var detail by rememberSaveable(person?.id) { mutableStateOf(person?.detail.orEmpty()) }
    var notes by rememberSaveable(person?.id) { mutableStateOf(person?.notes.orEmpty()) }
    var gender by rememberSaveable(person?.id) { mutableStateOf(person?.gender.orEmpty()) }
    var ageGroup by rememberSaveable(person?.id) { mutableStateOf(person?.ageGroup.orEmpty()) }
    var label by rememberSaveable(person?.id) { mutableStateOf(if (person == null) "Normal" else
        listOf("Smiling", "Angry", "Glasses").firstOrNull { choice -> person.appearances.none { it.label.equals(choice, true) } }.orEmpty()) }
    var consent by rememberSaveable { mutableStateOf(false) }
    var optionalDetails by rememberSaveable { mutableStateOf(false) }
    val duplicate = person?.appearances?.any { it.label.equals(label.trim(), true) } == true
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (person == null) "Register a person" else "Add an appearance") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (person != null) Text(person.name, color = Palette.Accent, fontWeight = FontWeight.Medium)
                Text("Keep the selected appearance while we capture six samples from three angles. Labels describe your captures, not detected emotions.",
                    color = Palette.Muted, fontSize = 12.sp)
                if (person == null) {
                    OutlinedTextField(name, { name = it.take(60) }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(detail, { detail = it.take(80) }, label = { Text("Role or relationship") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(notes, { notes = it.take(160) }, label = { Text("Notes (optional)") }, maxLines = 3, modifier = Modifier.fillMaxWidth())
                    TextButton(onClick = { optionalDetails = !optionalDetails }) { Text("Optional personal details") }
                    if (optionalDetails) {
                        ProvidedAttributes(gender, { gender = it }, ageGroup, { ageGroup = it })
                    }
                }
                OutlinedTextField(label, { label = it.take(40) }, label = { Text("Appearance label") },
                    singleLine = true, isError = duplicate, supportingText = { if (duplicate) Text("This label already exists") }, modifier = Modifier.fillMaxWidth())
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf("Normal", "Smiling", "Angry", "Glasses").forEach { choice ->
                        FilterChip(selected = label == choice, onClick = { label = choice }, label = { Text(choice, fontSize = 11.sp) },
                            enabled = person?.appearances?.none { it.label.equals(choice, true) } != false)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(consent, { consent = it }, modifier = Modifier.semantics {
                        contentDescription = "Consent to capture and save this person's appearance"
                    })
                    Text("This person agrees to capture and save this appearance.", fontSize = 12.sp)
                }
            }
        }, confirmButton = {
            Button(onClick = { onStart(EnrollmentSession(name.trim(), detail.trim(), notes.trim(), person?.id, label.trim(), gender.trim(), ageGroup.trim())) },
                enabled = name.isNotBlank() && label.isNotBlank() && !duplicate && consent) { Text("Start capture") }
        }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
internal fun EditPersonDialog(person: Person, onDismiss: () -> Unit, onSave: (Person) -> Unit) {
    var name by rememberSaveable(person.id) { mutableStateOf(person.name) }
    var detail by rememberSaveable(person.id) { mutableStateOf(person.detail) }
    var notes by rememberSaveable(person.id) { mutableStateOf(person.notes) }
    var gender by rememberSaveable(person.id) { mutableStateOf(person.gender) }
    var ageGroup by rememberSaveable(person.id) { mutableStateOf(person.ageGroup) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Personal details") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(name, { name = it.take(60) }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(detail, { detail = it.take(80) }, label = { Text("Role or relationship") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(notes, { notes = it.take(160) }, label = { Text("Notes") }, maxLines = 3, modifier = Modifier.fillMaxWidth())
            ProvidedAttributes(gender, { gender = it }, ageGroup, { ageGroup = it })
        }
    }, confirmButton = { Button(onClick = { onSave(person.copy(name = name, detail = detail, notes = notes, gender = gender, ageGroup = ageGroup)) },
        enabled = name.isNotBlank()) { Text("Save details") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun ProvidedAttributes(gender: String, onGender: (String) -> Unit, ageGroup: String, onAgeGroup: (String) -> Unit) {
    Text("Only enter details the person has provided. Leave blank when unknown.", color = Palette.Muted, fontSize = 12.sp)
    OutlinedTextField(gender, { onGender(it.take(40)) }, label = { Text("Gender (optional, provided)") },
        singleLine = true, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(ageGroup, { onAgeGroup(it.take(20)) }, label = { Text("Age category (optional, provided)") },
        placeholder = { Text("e.g. Child, Adult, Elderly") }, singleLine = true, modifier = Modifier.fillMaxWidth())
}
