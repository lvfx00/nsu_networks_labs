package ru.nsu.fit.semenov.restchat.message;

import org.jetbrains.annotations.NotNull;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class Message {
    private final int id;
    private final int authorId;
    private final String messageText;

    public Message(int id, int authorId, @NotNull String messageText) {
        checkArgument(id > 0);
        this.id = id;
        checkArgument(authorId > 0);
        this.authorId = authorId;
        this.messageText = checkNotNull(messageText);
    }

    public int getId() {
        return id;
    }

    public int getAuthorId() {
        return authorId;
    }

    public @NotNull String getMessageText() {
        return messageText;
    }
}
