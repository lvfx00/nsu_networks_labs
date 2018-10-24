package ru.nsu.semenov.nsulabs.lab3;

import com.sun.istack.internal.NotNull;
import ru.nsu.semenov.nsulabs.lab3.messages.*;

import java.net.SocketAddress;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

class Parser {
    private static final Charset encodingCharset = StandardCharsets.UTF_8;

    private Parser() {}

    static @NotNull Optional<Message> parse(@NotNull ByteBuffer buffer, @NotNull ParserContext context) {

        int msgTypeValue = buffer.getInt();
        Optional<MessageType> optionalPacketType = MessageType.fromInt(msgTypeValue);
        if (!optionalPacketType.isPresent()) {
            return Optional.empty();
        }

        MessageType messageType = optionalPacketType.get();

        try {
            switch (messageType) {
                case TEXT: {
                    long mostSignificantBits = buffer.getLong();
                    long leastSignificantBits = buffer.getLong();
                    UUID messageUuid = new UUID(mostSignificantBits, leastSignificantBits);

                    int nameLength = buffer.getInt();
                    if (buffer.remaining() < nameLength) {
                        return Optional.empty();
                    }

                    byte[] nameBuffer = new byte[nameLength];
                    buffer.get(nameBuffer);
                    String name = new String(nameBuffer, encodingCharset);

                    int textLength = buffer.getInt();
                    if (buffer.remaining() < textLength) {
                        return Optional.empty();
                    }

                    byte[] textBuffer = new byte[textLength];
                    buffer.get(textBuffer);
                    String text = new String(textBuffer, encodingCharset);

                    return Optional.of(TextMessage.newInstance(context.getAddress(), messageUuid, name, text));
                }
                case ACKNOWLEDGE: {
                    long mostSignificantBits = buffer.getLong();
                    long leastSignificantBits = buffer.getLong();
                    UUID messageUuid = new UUID(mostSignificantBits, leastSignificantBits);

                    mostSignificantBits = buffer.getLong();
                    leastSignificantBits = buffer.getLong();
                    UUID acknowledgeUuid = new UUID(mostSignificantBits, leastSignificantBits);

                    return Optional.of(AcknowledgeMessage.newInstance(context.getAddress(), messageUuid, acknowledgeUuid));
                }
                case CONNECTION: {
                    long mostSignificantBits = buffer.getLong();
                    long leastSignificantBits = buffer.getLong();
                    UUID messageUuid = new UUID(mostSignificantBits, leastSignificantBits);

                    return Optional.of(ConnectionMessage.newInstance(context.getAddress(), messageUuid));
                }
                default:
                    return Optional.empty();
            }

        } catch (BufferUnderflowException e) {
            return Optional.empty();
        }
    }

    static @NotNull ByteBuffer extractData(@NotNull Message message) {
        switch (message.getMessageType()) {
            case TEXT: {
                TextMessage textMessage = (TextMessage) message;

                byte[] encodedName = textMessage.getName().getBytes(encodingCharset);
                byte[] encodedData = textMessage.getText().getBytes(encodingCharset);

                ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES * 3 + Long.BYTES * 2
                        + encodedName.length + encodedData.length);

                buffer.putInt(textMessage.getMessageType().getValue());

                buffer.putLong(textMessage.getUuid().getMostSignificantBits());
                buffer.putLong(textMessage.getUuid().getLeastSignificantBits());

                buffer.putInt(encodedName.length);
                buffer.put(encodedName);

                buffer.putInt(encodedData.length);
                buffer.put(encodedData);

                buffer.rewind();
                return buffer.asReadOnlyBuffer();
            }
            case ACKNOWLEDGE: {
                AcknowledgeMessage acknowledgeMessage = (AcknowledgeMessage) message;

                ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES + Long.BYTES * 4);

                buffer.putInt(message.getMessageType().getValue());

                buffer.putLong(message.getUuid().getMostSignificantBits());
                buffer.putLong(message.getUuid().getLeastSignificantBits());

                buffer.putLong(acknowledgeMessage.getAcknowledgeUuid().getMostSignificantBits());
                buffer.putLong(acknowledgeMessage.getAcknowledgeUuid().getLeastSignificantBits());

                buffer.rewind();
                return buffer.asReadOnlyBuffer();
            }
            case CONNECTION: {
                ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES + Long.BYTES * 2);

                buffer.putInt(message.getMessageType().getValue());

                buffer.putLong(message.getUuid().getMostSignificantBits());
                buffer.putLong(message.getUuid().getLeastSignificantBits());

                buffer.rewind();
                return buffer.asReadOnlyBuffer();
            }
            default:
                throw new IllegalArgumentException("Can't resolve packet's type");
        }
    }

    static class ParserContext {
        @NotNull SocketAddress getAddress() {
            return address;
        }

        ParserContext(@NotNull SocketAddress address) {
            this.address = address;
        }

        private final SocketAddress address;
    }
}
