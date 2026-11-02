package com.lite.geminiapp;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.io.File;
import java.util.List;

import io.noties.markwon.Markwon;

public class ChatMessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_USER = 1;
    private static final int VIEW_TYPE_GEMINI = 2;

    private final Context context;
    private final List<ChatMessage> messageList;
    private final Markwon markwon;

    public ChatMessageAdapter(Context context, List<ChatMessage> messageList) {
        this.context = context;
        this.messageList = messageList;
        this.markwon = Markwon.create(context);
    }

    @Override
    public int getItemViewType(int position) {
        ChatMessage message = messageList.get(position);
        return "user".equals(message.getSender()) ? VIEW_TYPE_USER : VIEW_TYPE_GEMINI;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_USER) {
            View view = LayoutInflater.from(context).inflate(R.layout.item_chat_user, parent, false);
            return new UserViewHolder(view);
        } else {
            View view = LayoutInflater.from(context).inflate(R.layout.item_chat_gemini, parent, false);
            return new GeminiViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatMessage message = messageList.get(position);

        if (holder instanceof UserViewHolder) {
            UserViewHolder userHolder = (UserViewHolder) holder;
            userHolder.tvUserMessage.setText(message.getText());

            if (message.getImagePath() != null && !message.getImagePath().isEmpty()) {
                userHolder.ivUserImage.setVisibility(View.VISIBLE);
                Glide.with(context)
                        .load(new File(message.getImagePath()))
                        .centerCrop()
                        .into(userHolder.ivUserImage);
            } else {
                userHolder.ivUserImage.setVisibility(View.GONE);
            }
        } else if (holder instanceof GeminiViewHolder) {
            GeminiViewHolder geminiHolder = (GeminiViewHolder) holder;

            if (message.isLoading()) {
                geminiHolder.tvGeminiMessage.setText("✦ Gemini is thinking...");
                geminiHolder.actionLayout.setVisibility(View.GONE);
            } else {
                markwon.setMarkdown(geminiHolder.tvGeminiMessage, message.getText());
                geminiHolder.actionLayout.setVisibility(View.VISIBLE);

                geminiHolder.btnCopy.setOnClickListener(v -> {
                    ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cm != null) {
                        ClipData clip = ClipData.newPlainText("Gemini Response", message.getText());
                        cm.setPrimaryClip(clip);
                        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }
    }

    @Override
    public int getItemCount() {
        return messageList.size();
    }

    static class UserViewHolder extends RecyclerView.ViewHolder {
        TextView tvUserMessage;
        ImageView ivUserImage;

        public UserViewHolder(@NonNull View itemView) {
            super(itemView);
            tvUserMessage = itemView.findViewById(R.id.tvUserMessage);
            ivUserImage = itemView.findViewById(R.id.ivUserImage);
        }
    }

    static class GeminiViewHolder extends RecyclerView.ViewHolder {
        TextView tvGeminiMessage;
        View actionLayout;
        ImageButton btnCopy;

        public GeminiViewHolder(@NonNull View itemView) {
            super(itemView);
            tvGeminiMessage = itemView.findViewById(R.id.tvGeminiMessage);
            actionLayout = itemView.findViewById(R.id.actionLayout);
            btnCopy = itemView.findViewById(R.id.btnCopy);
        }
    }
}
