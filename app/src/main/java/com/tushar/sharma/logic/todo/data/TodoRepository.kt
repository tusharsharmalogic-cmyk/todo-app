package com.tushar.sharma.logic.todo.data

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "todo_prefs")

class TodoRepository(private val context: Context) {

    companion object {
        const val PREFS = "todo_storage_prefs"
        const val KEY_TREE_URI = "tree_uri"
    }

    private val TODOS_KEY = stringPreferencesKey("todos_json")
    private val SETTINGS_KEY = stringPreferencesKey("settings_json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    val hasExternalFolder: Boolean
        get() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_TREE_URI, null) != null

    private fun treePrefs() =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun setExternalFolder(uri: Uri) {
        val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
            android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            context.contentResolver.takePersistableUriPermission(uri, flags)
        } catch (_: Exception) {}
        treePrefs().edit().putString(KEY_TREE_URI, uri.toString()).apply()
    }

    fun clearExternalFolder() {
        treePrefs().edit().remove(KEY_TREE_URI).apply()
    }

    private fun getRootDoc(): DocumentFile? {
        val uriStr = treePrefs().getString(KEY_TREE_URI, null) ?: return null
        return try {
            DocumentFile.fromTreeUri(context, Uri.parse(uriStr))
        } catch (_: Exception) { null }
    }

    /** Read a file's text from SAF (returns null if not found / not readable). */
    private fun readExternal(name: String): String? {
        val root = getRootDoc() ?: return null
        val file = root.findFile(name) ?: return null
        return try {
            context.contentResolver.openInputStream(file.uri)?.use {
                it.bufferedReader().readText()
            }
        } catch (_: Exception) { null }
    }

    /** Write a file's text to SAF (best-effort; never throws). */
    private fun writeExternal(name: String, text: String): Boolean {
        val root = getRootDoc() ?: return false
        return try {
            val file = root.findFile(name)
                ?: root.createFile("application/json", name)
                ?: return false
            context.contentResolver.openOutputStream(file.uri, "wt")?.use {
                it.bufferedWriter().write(text)
            }
            true
        } catch (_: Exception) { false }
    }

    /** Called after the user picks a folder: imports existing data if present. */
    suspend fun importFromExternal(): Boolean = withContext(Dispatchers.IO) {
        val todosRaw = readExternal("todos.json")
        val settingsRaw = readExternal("settings.json")
        if (todosRaw == null && settingsRaw == null) return@withContext false
        try {
            context.dataStore.edit { prefs ->
                // validate todos
                if (todosRaw != null) {
                    json.decodeFromString<List<Todo>>(todosRaw)
                    prefs[TODOS_KEY] = todosRaw
                }
                if (settingsRaw != null) {
                    json.decodeFromString<AppSettings>(settingsRaw)
                    prefs[SETTINGS_KEY] = settingsRaw
                }
            }
            true
        } catch (_: Exception) { false }
    }

    /** Push current DataStore contents to the external folder (initial mirror). */
    suspend fun exportToExternal() = withContext(Dispatchers.IO) {
        if (!hasExternalFolder) return@withContext
        try {
            val prefs = context.dataStore.data.first()
            prefs[TODOS_KEY]?.let { writeExternal("todos.json", it) }
            prefs[SETTINGS_KEY]?.let { writeExternal("settings.json", it) }
        } catch (_: Exception) {}
    }

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
        // Best-effort async mirror to SAF (never blocks UI, never throws)
        withContext(Dispatchers.IO) {
            if (hasExternalFolder) writeExternal("todos.json", payload)
        }
    }

    suspend fun saveSettings(s: AppSettings) {
        val payload = json.encodeToString(s)
        context.dataStore.edit { prefs -> prefs[SETTINGS_KEY] = payload }
        withContext(Dispatchers.IO) {
            if (hasExternalFolder) writeExternal("settings.json", payload)
        }
    }
}