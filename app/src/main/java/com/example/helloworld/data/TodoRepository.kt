package com.example.helloworld.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "todo_prefs")

class TodoRepository(private val context: Context) {

    private val KEY = stringPreferencesKey("todos_json")
    private val json = Json { ignoreUnknownKeys = true }

    val todos: Flow<List<Todo>> = context.dataStore.data.map { prefs ->
        val raw = prefs[KEY] ?: "[]"
        try {
            json.decodeFromString<List<Todo>>(raw)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun save(list: List<Todo>) {
        context.dataStore.edit { prefs ->
            prefs[KEY] = json.encodeToString(list)
        }
    }
}