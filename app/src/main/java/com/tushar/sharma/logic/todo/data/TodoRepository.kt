package com.tushar.sharma.logic.todo.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

private val Context.dataStore by preferencesDataStore(name = "todo_prefs")

class TodoRepository(private val context: Context) {

    companion object {
        const val PREFS = "todo_storage_prefs"
        const val KEY_DEVICE_STORAGE_ENABLED = "device_storage_enabled"
        private const val SUBFOLDER = "Todo-app"
        private const val TODOS_FILE = "todos.json"
        private const val SETTINGS_FILE = "settings.json"
    }

    private val TODOS_KEY = stringPreferencesKey("todos_json")
    private val SETTINGS_KEY = stringPreferencesKey("settings_json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private fun storagePrefs() =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Whether tasks/settings are mirrored to public device storage (Downloads/Todo-app). */
    val isDeviceStorageEnabled: Boolean
        get() = storagePrefs().getBoolean(KEY_DEVICE_STORAGE_ENABLED, false)

    /** Turns device-storage sync on/off. When turning on: import existing files if present, else push current data out. */
    suspend fun setDeviceStorageEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        storagePrefs().edit().putBoolean(KEY_DEVICE_STORAGE_ENABLED, enabled).apply()
        if (enabled) {
            val imported = importFromDeviceStorage()
            if (!imported) exportToDeviceStorage()
        }
    }

    // -------- Legacy (API < 29): direct file in public Downloads/Todo-app --------

    private fun legacyDir(): File? {
        return try {
            val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val dir = File(downloads, SUBFOLDER)
            if (!dir.exists()) dir.mkdirs()
            dir
        } catch (_: Exception) { null }
    }

    private fun legacyRead(name: String): String? {
        val dir = legacyDir() ?: return null
        val file = File(dir, name)
        return try {
            if (file.exists()) file.readText() else null
        } catch (_: Exception) { null }
    }

    private fun legacyWrite(name: String, text: String): Boolean {
        val dir = legacyDir() ?: return false
        return try {
            File(dir, name).writeText(text)
            true
        } catch (_: Exception) { false }
    }

    // -------- Modern (API 29+): MediaStore Downloads collection --------

    private fun mediaStoreRelativePath(): String =
        Environment.DIRECTORY_DOWNLOADS + File.separator + SUBFOLDER + File.separator

    private fun mediaStoreFindUri(name: String): android.net.Uri? {
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?"
        val args = arrayOf(name, mediaStoreRelativePath())
        return try {
            resolver.query(collection, projection, selection, args, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    ContentUris.withAppendedId(collection, cursor.getLong(0))
                } else null
            }
        } catch (_: Exception) { null }
    }

    private fun mediaStoreRead(name: String): String? {
        val uri = mediaStoreFindUri(name) ?: return null
        return try {
            context.contentResolver.openInputStream(uri)?.use {
                it.bufferedReader().readText()
            }
        } catch (_: Exception) { null }
    }

    private fun mediaStoreWrite(name: String, text: String): Boolean {
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        return try {
            val uri = mediaStoreFindUri(name) ?: run {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, mediaStoreRelativePath())
                }
                resolver.insert(collection, values)
            } ?: return false
            resolver.openOutputStream(uri, "wt")?.use {
                it.bufferedWriter().write(text)
            }
            true
        } catch (_: Exception) { false }
    }

    private fun readDeviceFile(name: String): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) mediaStoreRead(name) else legacyRead(name)

    private fun writeDeviceFile(name: String, text: String): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) mediaStoreWrite(name, text) else legacyWrite(name, text)

    /** Called after device storage is turned on: imports existing data if present. */
    suspend fun importFromDeviceStorage(): Boolean = withContext(Dispatchers.IO) {
        val todosRaw = readDeviceFile(TODOS_FILE)
        val settingsRaw = readDeviceFile(SETTINGS_FILE)
        if (todosRaw == null && settingsRaw == null) return@withContext false
        try {
            context.dataStore.edit { prefs ->
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

    /** Push current DataStore contents to device storage (initial mirror). */
    suspend fun exportToDeviceStorage() = withContext(Dispatchers.IO) {
        if (!isDeviceStorageEnabled) return@withContext
        try {
            val prefs = context.dataStore.data.first()
            prefs[TODOS_KEY]?.let { writeDeviceFile(TODOS_FILE, it) }
            prefs[SETTINGS_KEY]?.let { writeDeviceFile(SETTINGS_FILE, it) }
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
        // Best-effort async mirror to device storage (never blocks UI, never throws)
        withContext(Dispatchers.IO) {
            if (isDeviceStorageEnabled) writeDeviceFile(TODOS_FILE, payload)
        }
    }

    suspend fun saveSettings(s: AppSettings) {
        val payload = json.encodeToString(s)
        context.dataStore.edit { prefs -> prefs[SETTINGS_KEY] = payload }
        withContext(Dispatchers.IO) {
            if (isDeviceStorageEnabled) writeDeviceFile(SETTINGS_FILE, payload)
        }
    }
}