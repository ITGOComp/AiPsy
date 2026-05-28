package com.example.aipsy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ChatHistory {

    public static class Entry {
        public final String role; // "user" или "bot"
        public final String text;
        public final String time;

        Entry(String role, String text) {
            this.role = role;
            this.text = text;
            this.time = new java.text.SimpleDateFormat(
                    "HH:mm", java.util.Locale.getDefault())
                    .format(new java.util.Date());
        }
    }

    private static final List<Entry> entries = new ArrayList<>();

    public static void add(String role, String text) {
        entries.add(new Entry(role, text));
    }

    public static List<Entry> getAll() {
        return Collections.unmodifiableList(entries);
    }

    public static void clear() {
        entries.clear();
    }
}
