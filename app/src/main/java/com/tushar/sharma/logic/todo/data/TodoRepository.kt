package com.tushar.sharma.logic.todo.data

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "todo_prefs")

class TodoRepository(private val context: Context) {

    companion object {
        const val PREFS = "todo_storage_prefs"
        const val KEY_TREE_URI = "tree_uri"
        const val KEY_SAVED_TODOS = "saved_todos_json"
        const val KEY_SAVED_SETTINGS = "saved_settings_json"
    }

    private val TODOS_KEY = stringPreferencesKey("todos_json")
    private val SETTINGS_KEY = stringPreferencesKey("settings_json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    /** Whether the user has granted a folder for external backup. */
    val hasExternalFolder: Boolean
        get() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_TREE_URI, null) != null

    /** Persist the picked folder's SAF tree URI. */
    fun setExternalFolder(uri: Uri) {
        val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
            android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            context.contentResolver.takePersistableUriPermission(uri, flags)
        } catch (_: Exception) {}
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_TREE_URI, uri.toString()).apply()
    }

    fun clearExternalFolder() {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(KEY_TREE_URI).apply()
    }

    private fun getRootDoc(): DocumentFile? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val uriStr = prefs.getString(KEY_TREE_URI, null) ?: return null
        return try {
            DocumentFile.fromTreeUri(context, Uri.parse(uriStr))
        } catch (_: Exception) { null }
    }

    private fun findOrCreateFile(parent: DocumentFile, name: String): DocumentFile? {
        val existing = parent.findFile(name)
        if (existing != null) return existing
        return parent.createFile("application/json", name)
    }

    private fun readFileText(name: String): String? {
        val root = getRootDoc() ?: return null
        val file = root.findFile(name) ?: return null
        return try {
            context.contentResolver.openInputStream(file.uri)?.use {
                it.bufferedReader().readText()
            }
        } catch (_: Exception) { null }
    }

    private fun writeFileText(name: String, text: String): Boolean {
        val root = getRootDoc() ?: return false
        val file = findOrCreateFile(root, name) ?: return false
        return try {
            context.contentResolver.openOutputStream(file.uri, "wt")?.use {
                it.bufferedWriter().write(text)
            }
            true
        } catch (_: Exception) { false }
    }

    // -------- flows --------

    val todos: Flow<List<Todo>> = context.dataStore.data.map { prefs ->
        // Try external first (if configured)
        val extRaw = readFileText("todos.json")
        val raw = extRaw ?: prefs[TODOS_KEY] ?: "[]"
        try {
            json.decodeFromString<List<Todo>>(raw)
        } catch (_: Exception) { emptyList() }
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        val extRaw = readFileText("settings.json")
        val raw = extRaw ?: prefs[SETTINGS_KEY] ?: "{}"
        try {
            json.decodeFromString<AppSettings>(raw)
        } catch (_: Exception) { AppSettings() }
    }

    suspend fun save(list: List<Todo>) {
        val payload = json.encodeToString(list)
        // Always mirror to DataStore (reliable)
        context.dataStore.edit { prefs -> prefs[TODOS_KEY] = payload }
        // Also write to external folder if configured
        withContext(Dispatchers.IO) {
            if (hasExternalFolder) writeFileText("todos.json", payload)
        }
    }

    suspend fun saveSettings(s: AppSettings) {
        val payload = json.encodeToString(s)
        context.dataStore.edit { prefs -> prefs[SETTINGS_KEY] = payload }
        withContext(Dispatchers.IO) {
            if (hasExternalFolder) writeFileText("settings.json", payload)
        }
    }
}