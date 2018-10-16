package ru.nsu.semenov.nsulabs.lab3.packets;

import com.sun.istack.internal.NotNull;

import java.util.UUID;

public class MessageSendPacket implements Packet {

    @Override
    public PacketType getPacketType() {
        return PacketType.MSG_SND;
    }

    private MessageSendPacket(@NotNull UUID messageUuid,
                              @NotNull String name,
                              @NotNull String data) {
        this.messageUuid = messageUuid;
        this.name = name;
        this.data = data;
    }

    public static MessageSendPacket newInstance(@NotNull UUID messageUuid,
                                                @NotNull String name,
                                                @NotNull String data) {
        return new MessageSendPacket(messageUuid, name, data);
    }

    public @NotNull UUID getMessageUuid() {
        return messageUuid;
    }

    public @NotNull String getData() {
        return data;
    }

    public @NotNull String getName() {
        return name;
    }

    private final UUID messageUuid;
    private final String name;
    private final String data;
}
