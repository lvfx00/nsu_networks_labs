package ru.nsu.fit.semenov.restchatutil;

import org.jetbrains.annotations.NotNull;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class Message {
    private final int id;
    private final int author;
    private final String messageText;

    public Message(int id, int author, @NotNull String messageText) {
        checkArgument(id > 0);
        this.id = id;
        checkArgument(author > 0);
        this.author = author;
        this.messageText = checkNotNull(messageText);
    }

    public int getId() {
        return id;
    }

    public int getAuthor() {
        return author;
    }

    public @NotNull String getMessageText() {
        return messageText;
    }
}
