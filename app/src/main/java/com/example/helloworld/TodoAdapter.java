package com.example.helloworld;

import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class TodoAdapter extends RecyclerView.Adapter<TodoAdapter.VH> {

    public interface Listener {
        void onToggle(Todo todo, boolean done);
        void onDelete(Todo todo);
    }

    private final List<Todo> data;
    private final Listener listener;

    public TodoAdapter(List<Todo> data, Listener listener) {
        this.data = data;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_todo, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Todo t = data.get(position);
        h.title.setText(t.getTitle());
        h.check.setOnCheckedChangeListener(null);
        h.check.setChecked(t.isDone());
        applyStrike(h.title, t.isDone());
        h.check.setOnCheckedChangeListener((btn, checked) -> listener.onToggle(t, checked));
        h.delete.setOnClickListener(v -> listener.onDelete(t));
    }

    private void applyStrike(TextView tv, boolean done) {
        if (done) {
            tv.setPaintFlags(tv.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            tv.setAlpha(0.6f);
        } else {
            tv.setPaintFlags(tv.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
            tv.setAlpha(1f);
        }
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        CheckBox check;
        TextView title;
        ImageButton delete;

        VH(@NonNull View itemView) {
            super(itemView);
            check = itemView.findViewById(R.id.checkDone);
            title = itemView.findViewById(R.id.textTitle);
            delete = itemView.findViewById(R.id.btnDelete);
        }
    }
}