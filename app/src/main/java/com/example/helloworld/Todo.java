package com.example.helloworld;

import java.util.UUID;

public class Todo {
    private String id;
    private String title;
    private boolean done;
    private long createdAt;

    public Todo(String title) {
        this.id = UUID.randomUUID().toString();
        this.title = title;
        this.done = false;
        this.createdAt = System.currentTimeMillis();
    }

    public Todo(String id, String title, boolean done, long createdAt) {
        this.id = id;
        this.title = title;
        this.done = done;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public boolean isDone() { return done; }
    public long getCreatedAt() { return createdAt; }

    public void setId(String id) { this.id = id; }
    public void setTitle(String title) { this.title = title; }
    public void setDone(boolean done) { this.done = done; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}