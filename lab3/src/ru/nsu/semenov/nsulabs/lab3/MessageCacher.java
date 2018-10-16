package ru.nsu.semenov.nsulabs.lab3;

import org.jetbrains.annotations.NotNull;
import ru.nsu.semenov.nsulabs.lab3.packets.MessageSendPacket;

import java.util.*;

public class MessageCacher {
    private MessageCacher() {}

    public static @NotNull MessageCacher newInstance() {
        return new MessageCacher();
    }

    public void cacheMessage(@NotNull MessageSendPacket packet, int receiversCount) {
        messageLogs.add(packet.getMessageUuid());
        messageCache.put(packet.getMessageUuid(), new CachedData(packet, receiversCount));
    }

    public @NotNull MessageSendPacket getFromCache(@NotNull UUID uuid) throws NoSuchElementException {
        if (messageCache.containsKey(uuid)) {
            return messageCache.get(uuid).packet;
        }
        else throw new NoSuchElementException("No message with specified UUID in cache");
    }

    public void decreaseReceiversCount(@NotNull UUID uuid) throws NoSuchElementException {
        if (messageCache.containsKey(uuid)) {
            messageCache.get(uuid).receiversCount--;
            if (0 == messageCache.get(uuid).receiversCount) {
                messageCache.remove(uuid);
            }
        }
        else throw new NoSuchElementException("No message with specified UUID in cache");
    }

    public boolean logged(@NotNull UUID uuid) {
        return messageLogs.contains(uuid);
    }

    private static class CachedData {
        CachedData(MessageSendPacket packet, int receiversCount) {
            this.receiversCount = receiversCount;
            this.packet = packet;
        }

        int receiversCount;
        MessageSendPacket packet;
    }

    private final HashSet<UUID> messageLogs = new HashSet<>();
    private final HashMap<UUID, CachedData> messageCache = new HashMap<>();
}
