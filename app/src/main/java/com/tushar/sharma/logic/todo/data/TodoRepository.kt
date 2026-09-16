package com.tushar.sharma.logic.todo.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "todo_prefs")

@Serializable
private data class BackupData(
    val todos: List<Todo> = emptyList(),
    val settings: AppSettings = AppSettings()
)

class TodoRepository(private val context: Context) {

    private val TODOS_KEY = stringPreferencesKey("todos_json")
    private val SETTINGS_KEY = stringPreferencesKey("settings_json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    // -------- flows (DataStore-primary, always fast) --------

    val todos: Flow<List<Todo>> = context.dataStore.data.map { prefs ->
        val raw = prefs[TODOS_KEY] ?: "[]"
        try {
            json.decodeFromString<List<Todo>>(raw)
        } catch (_: Exception) { emptyList() }
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        val raw = prefs[SETTINGS_KEY] ?: "{}"
        try {
            json.decodeFromString<AppSettings>(raw)
        } catch (_: Exception) { AppSettings() }
    }

    suspend fun save(list: List<Todo>) {
        val payload = json.encodeToString(list)
        context.dataStore.edit { prefs -> prefs[TODOS_KEY] = payload }
    }

    suspend fun saveSettings(s: AppSettings) {
        val payload = json.encodeToString(s)
        context.dataStore.edit { prefs -> prefs[SETTINGS_KEY] = payload }
    }

    // -------- Manual export / import (user picks the file location) --------

    /** Builds a single JSON blob with all tasks + settings, ready to write to a user-chosen file. */
    suspend fun exportBackupJson(): String = withContext(Dispatchers.IO) {
        val prefs = context.dataStore.data.first()
        val currentTodos = try {
            json.decodeFromString<List<Todo>>(prefs[TODOS_KEY] ?: "[]")
        } catch (_: Exception) { emptyList() }
        val currentSettings = try {
            json.decodeFromString<AppSettings>(prefs[SETTINGS_KEY] ?: "{}")
        } catch (_: Exception) { AppSettings() }
        json.encodeToString(BackupData(currentTodos, currentSettings))
    }

    /** Parses a backup JSON blob (from a user-picked file) and overwrites current data. Returns true on success. */
    suspend fun importBackupJson(text: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val data = json.decodeFromString<BackupData>(text)
            context.dataStore.edit { prefs ->
                prefs[TODOS_KEY] = json.encodeToString(data.todos)
                prefs[SETTINGS_KEY] = json.encodeToString(data.settings)
            }
            true
        } catch (_: Exception) { false }
    }
}