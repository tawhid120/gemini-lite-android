package com.lite.geminiapp;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class ConversationAdapter extends RecyclerView.Adapter<ConversationAdapter.ViewHolder> {

    public interface OnConversationClickListener {
        void onConversationClick(Conversation conversation);
        void onConversationDelete(Conversation conversation);
    }

    private final Context context;
    private final List<Conversation> conversationList;
    private final OnConversationClickListener listener;

    public ConversationAdapter(Context context, List<Conversation> conversationList, OnConversationClickListener listener) {
        this.context = context;
        this.conversationList = conversationList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_conversation, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Conversation conversation = conversationList.get(position);
        holder.tvTitle.setText(conversation.getTitle());

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onConversationClick(conversation);
        });

        holder.btnDelete.setOnClickListener(v -> {
            if (listener != null) listener.onConversationDelete(conversation);
        });
    }

    @Override
    public int getItemCount() {
        return conversationList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle;
        ImageButton btnDelete;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvConversationTitle);
            btnDelete = itemView.findViewById(R.id.btnDeleteConversation);
        }
    }
}
