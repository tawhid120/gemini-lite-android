package com.lite.geminiapp;

public class Conversation {
    private long id;
    private String title;
    private long createdAt;

    public Conversation(long id, String title, long createdAt) {
        this.id = id;
        this.title = title;
        this.createdAt = createdAt;
    }

    public long getId() { return id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public long getCreatedAt() { return createdAt; }
}
