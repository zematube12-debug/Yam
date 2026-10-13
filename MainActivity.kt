package com.example.notes

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.json.JSONArray

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("notes", Context.MODE_PRIVATE)
        setContent {
            val colors = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
            MaterialTheme(colorScheme = colors) {
                Surface(modifier = Modifier.fillMaxSize()) { NotesApp(prefs) }
            }
        }
    }
}

private fun load(prefs: SharedPreferences): List<String> {
    val arr = JSONArray(prefs.getString("n", "[]"))
    return List(arr.length()) { arr.getString(it) }
}

private fun save(prefs: SharedPreferences, list: List<String>) {
    prefs.edit().putString("n", JSONArray(list).toString()).apply()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesApp(prefs: SharedPreferences) {
    val notes = remember { mutableStateListOf<String>().apply { addAll(load(prefs)) } }
    var showDialog by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }

    Scaffold(
        topBar = { TopAppBar(title = { Text("My Notes") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { text = ""; showDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add note")
            }
        }
    ) { padding ->
        if (notes.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No notes yet. Tap + to add one.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(notes) { index, note ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(note, modifier = Modifier.weight(1f))
                            IconButton(onClick = {
                                notes.removeAt(index)
                                save(prefs, notes)
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("New note") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Write something") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (text.isNotBlank()) {
                        notes.add(0, text.trim())
                        save(prefs, notes)
                    }
                    showDialog = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showDialog = false }) { Text("Cancel") } }
        )
    }
}
