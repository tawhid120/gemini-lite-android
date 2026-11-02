package com.lite.geminiapp;

public class ChatMessage {
    private long id;
    private long conversationId;
    private String sender; // "user" or "gemini"
    private String text;
    private String imagePath;
    private long timestamp;
    private boolean isLoading;

    public ChatMessage(long conversationId, String sender, String text, String imagePath, long timestamp) {
        this.conversationId = conversationId;
        this.sender = sender;
        this.text = text;
        this.imagePath = imagePath;
        this.timestamp = timestamp;
        this.isLoading = false;
    }

    public static ChatMessage createLoadingMessage(long conversationId) {
        return createLoadingMessage(conversationId, "✦ Gemini is thinking...");
    }

    public static ChatMessage createLoadingMessage(long conversationId, String text) {
        ChatMessage msg = new ChatMessage(conversationId, "gemini", text, null, System.currentTimeMillis());
        msg.isLoading = true;
        return msg;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public long getConversationId() { return conversationId; }
    public String getSender() { return sender; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public String getImagePath() { return imagePath; }
    public void setImagePath(String imagePath) { this.imagePath = imagePath; }

    public long getTimestamp() { return timestamp; }

    public boolean isLoading() { return isLoading; }
    public void setLoading(boolean loading) { isLoading = loading; }
}
