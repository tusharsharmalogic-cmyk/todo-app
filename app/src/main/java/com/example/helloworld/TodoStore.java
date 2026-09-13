package com.example.helloworld;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class TodoStore {
    private static final String PREF = "todo_prefs";
    private static final String KEY = "todos_json";

    private final SharedPreferences prefs;

    public TodoStore(Context ctx) {
        this.prefs = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public List<Todo> load() {
        List<Todo> list = new ArrayList<>();
        String raw = prefs.getString(KEY, "[]");
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                list.add(new Todo(
                        o.getString("id"),
                        o.getString("title"),
                        o.getBoolean("done"),
                        o.getLong("createdAt")
                ));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public void save(List<Todo> todos) {
        JSONArray arr = new JSONArray();
        try {
            for (Todo t : todos) {
                JSONObject o = new JSONObject();
                o.put("id", t.getId());
                o.put("title", t.getTitle());
                o.put("done", t.isDone());
                o.put("createdAt", t.getCreatedAt());
                arr.put(o);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        prefs.edit().putString(KEY, arr.toString()).apply();
    }
}