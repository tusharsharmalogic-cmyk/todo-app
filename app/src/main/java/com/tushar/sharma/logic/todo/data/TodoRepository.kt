package com.tushar.sharma.logic.todo.data

import android.content.Context
import android.os.Environment
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

private val Context.dataStore by preferencesDataStore(name = "todo_prefs")

class TodoRepository(private val context: Context) {

    companion object {
        const val EXTERNAL_DIR_NAME = "Todo-app"
        const val TODOS_FILE = "todos.json"
        const val SETTINGS_FILE = "settings.json"
    }

    private val TODOS_KEY = stringPreferencesKey("todos_json")
    private val SETTINGS_KEY = stringPreferencesKey("settings_json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    /** Path: /sdcard/Todo-app/ (or app-private if permission denied) */
    private val externalDir: File?
        get() {
            return try {
                val root = Environment.getExternalStorageDirectory()
                val dir = File(root, EXTERNAL_DIR_NAME)
                if (!dir.exists()) dir.mkdirs()
                if (dir.canWrite()) dir else null
            } catch (_: Exception) {
                null
            }
        }

    /** True when /sdcard/Todo-app/ is writable. */
    val usingExternalStorage: Boolean get() = externalDir != null

    // -------- In-memory mirrors (updated on every save) --------

    private val _todosCache = MutableStateFlow<List<Todo>>(emptyList())
    private val _settingsCache = MutableStateFlow(AppSettings())

    init {
        // Load from external file (if present) synchronously at start
        externalDir?.let { dir ->
            val tf = File(dir, TODOS_FILE)
            val sf = File(dir, SETTINGS_FILE)
            if (tf.exists()) {
                try {
                    _todosCache.value = json.decodeFromString<List<Todo>>(tf.readText())
                } catch (_: Exception) {}
            }
            if (sf.exists()) {
                try {
                    _settingsCache.value = json.decodeFromString<AppSettings>(sf.readText())
                } catch (_: Exception) {}
            }
        }
    }

    val todos: Flow<List<Todo>> = context.dataStore.data.map { prefs ->
        // Prefer external file if it exists
        val ext = externalDir
        if (ext != null) {
            val tf = File(ext, TODOS_FILE)
            if (tf.exists()) {
                try {
                    val list = json.decodeFromString<List<Todo>>(tf.readText())
                    _todosCache.value = list
                    return@map list
                } catch (_: Exception) {}
            }
        }
        // Fallback to DataStore
        val raw = prefs[TODOS_KEY] ?: "[]"
        val list = try {
            json.decodeFromString<List<Todo>>(raw)
        } catch (_: Exception) { emptyList() }
        _todosCache.value = list
        list
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        val ext = externalDir
        if (ext != null) {
            val sf = File(ext, SETTINGS_FILE)
            if (sf.exists()) {
                try {
                    val s = json.decodeFromString<AppSettings>(sf.readText())
                    _settingsCache.value = s
                    return@map s
                } catch (_: Exception) {}
            }
        }
        val raw = prefs[SETTINGS_KEY] ?: "{}"
        val s = try {
            json.decodeFromString<AppSettings>(raw)
        } catch (_: Exception) { AppSettings() }
        _settingsCache.value = s
        s
    }

    suspend fun save(list: List<Todo>) {
        _todosCache.value = list
        val payload = json.encodeToString(list)
        // external
        withContext(Dispatchers.IO) {
            externalDir?.let { dir ->
                try {
                    File(dir, TODOS_FILE).writeText(payload)
                } catch (_: Exception) {}
            }
        }
        // always mirror to DataStore too (so app restores even if SD deleted)
        context.dataStore.edit { prefs ->
            prefs[TODOS_KEY] = payload
        }
    }

    suspend fun saveSettings(s: AppSettings) {
        _settingsCache.value = s
        val payload = json.encodeToString(s)
        withContext(Dispatchers.IO) {
            externalDir?.let { dir ->
                try {
                    File(dir, SETTINGS_FILE).writeText(payload)
                } catch (_: Exception) {}
            }
        }
        context.dataStore.edit { prefs ->
            prefs[SETTINGS_KEY] = payload
        }
    }
}