package com.example.aipsy;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_USER   = 0;
    private static final int TYPE_BOT    = 1;
    private static final int TYPE_TYPING = 2;

    private final List<Message> messages = new ArrayList<>();

    // --- ViewHolders ---

    static class UserViewHolder extends RecyclerView.ViewHolder {
        final TextView tvMessage;
        UserViewHolder(View v) {
            super(v);
            tvMessage = v.findViewById(R.id.tvMessage);
        }
    }

    static class BotViewHolder extends RecyclerView.ViewHolder {
        final TextView tvMessage;
        BotViewHolder(View v) {
            super(v);
            tvMessage = v.findViewById(R.id.tvMessage);
        }
    }

    static class TypingViewHolder extends RecyclerView.ViewHolder {
        TypingViewHolder(View v) { super(v); }
    }

    // --- Adapter methods ---

    @Override
    public int getItemViewType(int position) {
        switch (messages.get(position).getType()) {
            case USER:   return TYPE_USER;
            case TYPING: return TYPE_TYPING;
            default:     return TYPE_BOT;
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_USER) {
            return new UserViewHolder(inf.inflate(R.layout.item_message_user, parent, false));
        } else if (viewType == TYPE_TYPING) {
            return new TypingViewHolder(inf.inflate(R.layout.item_typing, parent, false));
        } else {
            return new BotViewHolder(inf.inflate(R.layout.item_message_bot, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Message msg = messages.get(position);
        if (holder instanceof UserViewHolder) {
            ((UserViewHolder) holder).tvMessage.setText(msg.getText());
        } else if (holder instanceof BotViewHolder) {
            ((BotViewHolder) holder).tvMessage.setText(msg.getText());
        }
    }

    @Override
    public int getItemCount() { return messages.size(); }


    public void addMessage(Message msg) {
        messages.add(msg);
        notifyItemInserted(messages.size() - 1);
    }

    public void showTyping() {
        messages.add(new Message("", Message.Type.TYPING));
        notifyItemInserted(messages.size() - 1);
    }

    public void hideTyping() {
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i).getType() == Message.Type.TYPING) {
                messages.remove(i);
                notifyItemRemoved(i);
                return;
            }
        }
    }
}
