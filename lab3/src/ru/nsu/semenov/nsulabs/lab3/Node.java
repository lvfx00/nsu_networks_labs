package ru.nsu.semenov.nsulabs.lab3;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.nsu.semenov.nsulabs.lab3.Parser.ParserContext;
import ru.nsu.semenov.nsulabs.lab3.messages.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

public class Node {
    private static final long SELECTOR_TIMEOUT = 1000; // in milliseconds
    private static final int MAX_MSG_SIZE = 32 * 1024; // in bytes
    private static final Duration PENDING_CHECK_INTERVAL = Duration.ofMillis(1000);
    private static final Duration RESENDING_INTERVAL = Duration.ofMillis(3000);
    private static final int MAX_RESENDING_COUNT = 3;

    private Node(@NotNull String name,
                 int lossRate,
                 @NotNull InetSocketAddress localAddress,
                 @Nullable InetSocketAddress parentAddress) {
        this.name = name;
        this.lossRate = lossRate;
        this.localAddress = localAddress;
        this.parentAddress = parentAddress;
    }

    public static @NotNull Node newInstance(@NotNull String name,
                                            int lossRate,
                                            @NotNull InetSocketAddress localAddress,
                                            @Nullable InetSocketAddress parentAddress) {
        return new Node(name, lossRate, localAddress, parentAddress);
    }

    public void run() {
        Instant lastPendingCheck = Instant.MIN;

        try (DatagramChannel datagramChannel = DatagramChannel.open();
             Selector selector = Selector.open()) {
            datagramChannel.bind(localAddress);
            datagramChannel.configureBlocking(false);

            datagramChannel.register(selector, SelectionKey.OP_WRITE | SelectionKey.OP_READ);

            if (null != parentAddress) {
                connect(parentAddress);
            }

            isRunning = true;
            try {
                while (isRunning) {
                    // TODO think about timeout
                    selector.select(SELECTOR_TIMEOUT);

                    for (SelectionKey key : selector.keys()) {
                        try {
                            if (key.isValid() && key.isReadable()) {
                                handleRead(key);
                            }
                            if (key.isValid() && key.isWritable()) {
                                handleWrite(key);
                            }
                        } catch (IOException e) { // catch if errors while data processing occurs
                            System.err.println(e.getMessage());
                            e.printStackTrace();
                        }
                    }

                    // perform checking pendings
                    Instant now = Clock.systemUTC().instant();
                    if (Duration.between(lastPendingCheck, now).compareTo(PENDING_CHECK_INTERVAL) > 0) {
                        dataExecutor.submit(this::checkPendings);
                        lastPendingCheck = now;
                    }
                }
                // catch if error in selector occurs
            } catch (IOException e) {
                System.err.println(e.getMessage());
                e.printStackTrace();
            }

            cleanup();

            // catch if error in socket creation occurs occurs
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }
    }

    public void stop() {
        isRunning = false;
    }

    public void sendMessage(@NotNull String messageText) {
        // send message to all another knownNodes
        UUID uuid = UUID.randomUUID();

        for (SocketAddress address : knownNodes) {
            Message sendingMessage = TextMessage.newInstance(address, uuid, name, messageText);

            sendingMessagesQueue.add(sendingMessage);
            pendingsInfoMap.put(new PendingKey(uuid, address),
                    new PendingInfo(sendingMessage, Instant.MAX, 0));
        }
    }

    private void connect(@NotNull SocketAddress address) {
        Message sendingMessage = ConnectionMessage.newInstance(address, UUID.randomUUID());
        sendingMessagesQueue.add(sendingMessage);
        pendingsInfoMap.put(new PendingKey(sendingMessage.getUuid(), address),
                new PendingInfo(sendingMessage, Instant.MAX, 0));
    }

    private void cleanup() {
        dataExecutor.shutdown();
    }

    private void showMessage(TextMessage message) {
        System.out.println(message.getName() + ": " + message.getText());
    }


    private void handleRead(SelectionKey key) throws IOException {
        DatagramChannel channel = (DatagramChannel) key.channel();
        SocketAddress senderAddress = channel.receive(inputBuffer);

        if (knownNodes.contains(senderAddress)) {
            Optional<Message> message = Parser.parse(inputBuffer, new ParserContext(senderAddress));
            message.ifPresent(this::handleMessage);
        }
    }

    private void handleWrite(SelectionKey key) throws IOException {
        DatagramChannel channel = (DatagramChannel) key.channel();

        Message sendingMessage = sendingMessagesQueue.poll();
        if (null == sendingMessage) { // there is no messages in queue
            return;
        }

        ByteBuffer dataBuffer = Parser.extractData(sendingMessage);
        int res = channel.send(dataBuffer, sendingMessage.getDestAddress());

        if (0 == res) { // insufficient room for the datagram in the underlying output buffer
            sendingMessagesQueue.add(sendingMessage);
            return;
        }

        PendingKey pendingKey = new PendingKey(sendingMessage.getUuid(), sendingMessage.getDestAddress());
        if (pendingsInfoMap.containsKey(pendingKey)) {
            pendingsInfoMap.get(pendingKey).updateLastTime(Instant.now());
        }
    }

    private void handleMessage(Message message) {
        // message loss imitation
        int rand = ThreadLocalRandom.current().nextInt(0, 100);
        if (rand < lossRate) {
            return;
        }

        switch (message.getMessageType()) {
            case TEXT:
                dataExecutor.submit(() -> handleTextMessage((TextMessage) message));
                break;
            case ACKNOWLEDGE:
                dataExecutor.submit(() -> handleAcknowledgeMessage((AcknowledgeMessage) message));
                break;
            case CONNECTION:
                dataExecutor.submit(() -> handleConnectionMessage((ConnectionMessage) message));
                break;
            default:
                throw new AssertionError("Invalid message type");
        }
    }

    private void handleConnectionMessage(@NotNull ConnectionMessage message) {
        if (!knownNodes.contains(message.getDestAddress())) {
            Message responseMessage = AcknowledgeMessage.newInstance(message.getDestAddress(), UUID.randomUUID(), message.getUuid());

            sendingMessagesQueue.add(responseMessage);
            pendingsInfoMap.put(new PendingKey(responseMessage.getUuid(), message.getDestAddress()),
                    new PendingInfo(responseMessage, Instant.MAX, 0));
        }
    }

    private void handleAcknowledgeMessage(@NotNull AcknowledgeMessage message) {
        PendingKey key = new PendingKey(message.getAcknowledgeUuid(), message.getDestAddress());
        if (pendingsInfoMap.containsKey(key)) {
            Message originalMessage = pendingsInfoMap.get(key).getOriginalMessage();
            // remove pending corresponding to received acknowledge
            pendingsInfoMap.remove(key);

            switch (originalMessage.getMessageType()) {
                // got acknowledge for text message
                case TEXT:
                    break;
                case ACKNOWLEDGE:
                    // got acknowledge for connection response
                    knownNodes.add(message.getDestAddress());
                    break;
                case CONNECTION:
                    // got acknowledge for connection request
                    // send acknowledge for connection response
                    sendingMessagesQueue.add(AcknowledgeMessage.newInstance(message.getDestAddress(), UUID.randomUUID(), message.getUuid()));
                    knownNodes.add(message.getDestAddress());
                    break;
                default:
                    throw new AssertionError("Invalid message type");
            }
        }
    }

    private void handleTextMessage(@NotNull TextMessage message) {
        if (messageLogs.contains(message.getUuid())) {
            // resend acknowledge
            sendingMessagesQueue.add(AcknowledgeMessage.newInstance(message.getDestAddress(), UUID.randomUUID(), message.getUuid()));
        } else {
            showMessage(message);
            messageLogs.add(message.getUuid());

            // send message to all another knownNodes
            for (SocketAddress address : knownNodes) {
                if (!address.equals(message.getDestAddress())) {
                    Message responseMessage = TextMessage.newInstance(address,
                            message.getUuid(), message.getName(), message.getText());

                    sendingMessagesQueue.add(responseMessage);
                    pendingsInfoMap.put(new PendingKey(responseMessage.getUuid(), address),
                            new PendingInfo(responseMessage, Instant.MAX, 0));
                }
            }
        }
    }

    private void checkPendings() {
        Instant now = Instant.now();
        List<PendingKey> toRemove = new ArrayList<>();

        for (Map.Entry<PendingKey, PendingInfo> entry : pendingsInfoMap.entrySet()) {
            PendingInfo pendingInfo = entry.getValue();

            if (!knownNodes.contains(pendingInfo.getOriginalMessage().getDestAddress())) {
                toRemove.add(entry.getKey());
                continue;
            }

            if (Duration.between(pendingInfo.getLastSendTime(), now).compareTo(RESENDING_INTERVAL) > 0) {
                if (pendingInfo.getResendingCount() >= MAX_RESENDING_COUNT) {
                    toRemove.add(entry.getKey());
                    knownNodes.remove(pendingInfo.getOriginalMessage().getDestAddress());

                } else {
                    sendingMessagesQueue.add(pendingInfo.getOriginalMessage());
                    pendingInfo.incrementResendingCount();
                    pendingInfo.updateLastTime(now);
                }
            }
        }

        for (PendingKey key : toRemove) {
            pendingsInfoMap.remove(key);
        }
    }

    private static class PendingInfo {
        PendingInfo(@NotNull Message originalMessage, @NotNull Instant lastSendTime, int resendingCount) {
            this.originalMessage = originalMessage;
            this.lastSendTime = lastSendTime;
            this.resendingCount = resendingCount;
        }

        int getResendingCount() {
            return resendingCount;
        }

        @NotNull Instant getLastSendTime() {
            return lastSendTime;
        }

        void incrementResendingCount() {
            if (resendingCount < Integer.MAX_VALUE)
                resendingCount++;
        }

        void updateLastTime(@NotNull Instant instant) {
            lastSendTime = instant;
        }


        @NotNull Message getOriginalMessage() {
            return originalMessage;
        }

        private final Message originalMessage;
        private int resendingCount;
        private Instant lastSendTime;
    }

    private static class PendingKey {
        PendingKey(UUID uuid, SocketAddress socketAddress) {
            this.uuid = uuid;
            this.socketAddress = socketAddress;
        }

        public UUID getUuid() {
            return uuid;
        }

        public SocketAddress getSocketAddress() {
            return socketAddress;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PendingKey)) return false;
            PendingKey that = (PendingKey) o;
            return Objects.equals(uuid, that.uuid) &&
                    Objects.equals(socketAddress, that.socketAddress);
        }

        @Override
        public int hashCode() {
            return Objects.hash(uuid, socketAddress);
        }

        private final UUID uuid;
        private final SocketAddress socketAddress;
    }

    private final String name;
    private final int lossRate;
    private final InetSocketAddress localAddress;
    private final InetSocketAddress parentAddress;

    private boolean isRunning = false;

    private final ConcurrentHashMap<PendingKey, PendingInfo> pendingsInfoMap = new ConcurrentHashMap<>();
    private final HashSet<UUID> messageLogs = new HashSet<>();

    private final ByteBuffer inputBuffer = ByteBuffer.allocate(MAX_MSG_SIZE);
    private final ExecutorService dataExecutor = Executors.newSingleThreadExecutor();

    private final ConcurrentLinkedQueue<Message> sendingMessagesQueue = new ConcurrentLinkedQueue<>();

    private final HashSet<SocketAddress> knownNodes = new HashSet<>();
}

