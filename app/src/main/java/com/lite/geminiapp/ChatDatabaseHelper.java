package com.lite.geminiapp;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class ChatDatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "gemini_native_chats.db";
    private static final int DATABASE_VERSION = 1;

    private static final String TABLE_CONVERSATIONS = "conversations";
    private static final String COL_CONV_ID = "id";
    private static final String COL_CONV_TITLE = "title";
    private static final String COL_CONV_CREATED = "created_at";

    private static final String TABLE_MESSAGES = "messages";
    private static final String COL_MSG_ID = "id";
    private static final String COL_MSG_CONV_ID = "conversation_id";
    private static final String COL_MSG_SENDER = "sender";
    private static final String COL_MSG_TEXT = "text";
    private static final String COL_MSG_IMAGE = "image_path";
    private static final String COL_MSG_TIME = "timestamp";

    public ChatDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_CONVERSATIONS + " (" +
                COL_CONV_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_CONV_TITLE + " TEXT, " +
                COL_CONV_CREATED + " INTEGER)");

        db.execSQL("CREATE TABLE " + TABLE_MESSAGES + " (" +
                COL_MSG_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_MSG_CONV_ID + " INTEGER, " +
                COL_MSG_SENDER + " TEXT, " +
                COL_MSG_TEXT + " TEXT, " +
                COL_MSG_IMAGE + " TEXT, " +
                COL_MSG_TIME + " INTEGER)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_MESSAGES);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_CONVERSATIONS);
        onCreate(db);
    }

    public long createConversation(String title) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_CONV_TITLE, title);
        cv.put(COL_CONV_CREATED, System.currentTimeMillis());
        return db.insert(TABLE_CONVERSATIONS, null, cv);
    }

    public List<Conversation> getAllConversations() {
        List<Conversation> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE_CONVERSATIONS + " ORDER BY " + COL_CONV_CREATED + " DESC", null);
        if (cursor.moveToFirst()) {
            do {
                long id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_CONV_ID));
                String title = cursor.getString(cursor.getColumnIndexOrThrow(COL_CONV_TITLE));
                long created = cursor.getLong(cursor.getColumnIndexOrThrow(COL_CONV_CREATED));
                list.add(new Conversation(id, title, created));
            } while (cursor.moveToNext());
        }
        cursor.close();
        return list;
    }

    public void updateConversationTitle(long id, String newTitle) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_CONV_TITLE, newTitle);
        db.update(TABLE_CONVERSATIONS, cv, COL_CONV_ID + " = ?", new String[]{String.valueOf(id)});
    }

    public void deleteConversation(long id) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_MESSAGES, COL_MSG_CONV_ID + " = ?", new String[]{String.valueOf(id)});
        db.delete(TABLE_CONVERSATIONS, COL_CONV_ID + " = ?", new String[]{String.valueOf(id)});
    }

    public long saveMessage(long conversationId, String sender, String text, String imagePath) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_MSG_CONV_ID, conversationId);
        cv.put(COL_MSG_SENDER, sender);
        cv.put(COL_MSG_TEXT, text);
        cv.put(COL_MSG_IMAGE, imagePath);
        cv.put(COL_MSG_TIME, System.currentTimeMillis());
        return db.insert(TABLE_MESSAGES, null, cv);
    }

    public List<ChatMessage> getMessages(long conversationId) {
        List<ChatMessage> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE_MESSAGES + " WHERE " + COL_MSG_CONV_ID + " = ? ORDER BY " + COL_MSG_TIME + " ASC",
                new String[]{String.valueOf(conversationId)});
        if (cursor.moveToFirst()) {
            do {
                long id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_MSG_ID));
                String sender = cursor.getString(cursor.getColumnIndexOrThrow(COL_MSG_SENDER));
                String text = cursor.getString(cursor.getColumnIndexOrThrow(COL_MSG_TEXT));
                String img = cursor.getString(cursor.getColumnIndexOrThrow(COL_MSG_IMAGE));
                long time = cursor.getLong(cursor.getColumnIndexOrThrow(COL_MSG_TIME));

                ChatMessage msg = new ChatMessage(conversationId, sender, text, img, time);
                msg.setId(id);
                list.add(msg);
            } while (cursor.moveToNext());
        }
        cursor.close();
        return list;
    }
}
