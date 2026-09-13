package com.example.helloworld;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Toast;

import com.example.helloworld.databinding.ActivityMainBinding;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class MainActivity extends AppCompatActivity implements TodoAdapter.Listener {

    private ActivityMainBinding binding;
    private TodoStore store;
    private final List<Todo> todos = new ArrayList<>();
    private TodoAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        store = new TodoStore(this);
        todos.addAll(store.load());

        adapter = new TodoAdapter(todos, this);
        binding.recycler.setLayoutManager(new LinearLayoutManager(this));
        binding.recycler.setAdapter(adapter);

        binding.btnAdd.setOnClickListener(v -> addTask());
        binding.editTask.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                addTask();
                return true;
            }
            return false;
        });

        binding.fabClear.setOnClickListener(v -> clearCompleted());
        updateEmptyState();
    }

    private void addTask() {
        String text = binding.editTask.getText().toString().trim();
        if (text.isEmpty()) {
            Toast.makeText(this, "Enter a task first", Toast.LENGTH_SHORT).show();
            return;
        }
        Todo t = new Todo(text);
        todos.add(0, t);
        adapter.notifyItemInserted(0);
        binding.recycler.scrollToPosition(0);
        binding.editTask.setText("");
        persist();
        updateEmptyState();
    }

    private void clearCompleted() {
        boolean removed = false;
        Iterator<Todo> it = todos.iterator();
        while (it.hasNext()) {
            if (it.next().isDone()) {
                it.remove();
                removed = true;
            }
        }
        if (removed) {
            adapter.notifyDataSetChanged();
            persist();
            updateEmptyState();
            Toast.makeText(this, "Completed tasks cleared", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "No completed tasks", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateEmptyState() {
        binding.textEmpty.setVisibility(todos.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void persist() {
        store.save(todos);
    }

    @Override
    public void onToggle(Todo todo, boolean done) {
        todo.setDone(done);
        int idx = todos.indexOf(todo);
        if (idx >= 0) adapter.notifyItemChanged(idx);
        persist();
    }

    @Override
    public void onDelete(Todo todo) {
        int idx = todos.indexOf(todo);
        if (idx >= 0) {
            todos.remove(idx);
            adapter.notifyItemRemoved(idx);
            persist();
            updateEmptyState();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        this.binding = null;
    }
}