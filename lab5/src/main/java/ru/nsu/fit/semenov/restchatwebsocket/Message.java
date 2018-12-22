package ru.nsu.fit.semenov.restchatwebsocket;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class Message {
    private final int id;
    private final int author;
    private final String messageText;

    public static @Nullable Message checkForNulls(@Nullable Message message) {
        if (message == null ||
                message.id < 1 ||
                message.author < 1 ||
                message.messageText == null) {
            return null;
        }
        return message;
    }

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
