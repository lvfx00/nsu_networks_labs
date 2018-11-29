package ru.nsu.fit.semenov.restchat.message;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.nsu.fit.semenov.restchat.user.UserInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.google.gson.internal.$Gson$Preconditions.checkArgument;
import static com.google.gson.internal.$Gson$Preconditions.checkNotNull;

public class MessageManager {
    private final ArrayList<Message> messages = new ArrayList<>();
    private int messagesCount = 0;

    public @Nullable Message getMessageById(int id) {
        try {
            return messages.get(id - 1);
        } catch (IndexOutOfBoundsException e) {
            return null;
        }
    }

    public @NotNull List<Message> getMessages(int offset, int count) {
        checkArgument(offset >= 0);
        checkArgument(count >= 0);

        if (offset >= messages.size()) {
            return Collections.emptyList();
        }

        if (offset + count > messages.size()) {
            // return not count but all remaining elements in the list
            return Collections.unmodifiableList(messages.subList(offset, messages.size()));
        }
        else {
            return Collections.unmodifiableList(messages.subList(offset, offset + count));
        }
    }

    public @NotNull Message sendMessage(@NotNull String messageText, @NotNull UserInfo sender) {
        Message msg = new Message(++messagesCount, checkNotNull(sender).getId(), checkNotNull(messageText));
        messages.add(msg);
        return msg;
    }
}
