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
        buffer.rewind();
        try {
            int msgTypeValue = buffer.getInt();
            Optional<MessageType> optionalPacketType = MessageType.fromInt(msgTypeValue);
            if (!optionalPacketType.isPresent()) {
                return Optional.empty();
            }

            MessageType messageType = optionalPacketType.get();
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
                case TEXT_ACK: {
                    long mostSignificantBits = buffer.getLong();
                    long leastSignificantBits = buffer.getLong();
                    UUID messageUuid = new UUID(mostSignificantBits, leastSignificantBits);

                    return Optional.of(TextAckMessage.newInstance(context.getAddress(), messageUuid));
                }
                case CONN_REQ: {
                    return Optional.of(ConnReqMessage.newInstance(context.getAddress()));
                }
                case CONN_ACK: {
                    return Optional.of(ConnAckMessage.newInstance(context.getAddress()));
                }
                case KEEP_ALIVE: {
                    return Optional.of(KeepAliveMessage.newInstance(context.getAddress()));
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
            case TEXT_ACK: {
                TextAckMessage textAckMessage = (TextAckMessage) message;

                ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES + Long.BYTES * 4);

                buffer.putInt(message.getMessageType().getValue());

                buffer.putLong(textAckMessage.getUuid().getMostSignificantBits());
                buffer.putLong(textAckMessage.getUuid().getLeastSignificantBits());

                buffer.rewind();
                return buffer.asReadOnlyBuffer();
            }
            case CONN_REQ:
            case CONN_ACK:
            case KEEP_ALIVE: {
                ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES);

                buffer.putInt(message.getMessageType().getValue());

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
