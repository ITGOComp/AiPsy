package com.example.aipsy;

public class Message {
    public enum Type { USER, BOT, TYPING }

    private final String text;
    private final Type type;

    public Message(String text, Type type) {
        this.text = text;
        this.type = type;
    }

    public String getText() { return text; }
    public Type getType() { return type; }
}
