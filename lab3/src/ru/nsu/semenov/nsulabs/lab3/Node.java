package ru.nsu.semenov.nsulabs.lab3;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.nsu.semenov.nsulabs.lab3.messages.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

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
        // send message to all another nodes
        UUID uuid = UUID.randomUUID();

        for (SocketAddress address : nodes) {
            Message sendingMessage = new TextMessage(address, uuid, name, messageText);
            sendingMessagesQueue.add(sendingMessage);
            pendingStorage.addPendingBySentMessage(sendingMessage);
        }
    }

    public void connect(@NotNull SocketAddress address) {
        Message sendingMessage = new ConnReqMessage(address);
        sendingMessagesQueue.add(sendingMessage);
        pendingStorage.addPendingBySentMessage(sendingMessage);
    }

    private void handleRead(SelectionKey key) throws IOException {
        DatagramChannel channel = (DatagramChannel) key.channel();
        SocketAddress senderAddress = channel.receive(inputBuffer);
        Message message;
        try {
            message = Parser.parse(inputBuffer, new Parser.ParserContext(senderAddress));
        } catch (IllegalArgumentException | BufferUnderflowException e) {
            System.err.println(e.getMessage());
            System.err.println("Invalid packet received. Skipping.");
            return;
        }

        System.out.println("Received " + message.getMessageType().toString() +
                " message from " + senderAddress.toString());

        processPacket(message);
    }

    private void handleWrite(SelectionKey key) throws IOException {
        DatagramChannel channel = (DatagramChannel) key.channel();

        Message sendingMessage = sendingMessagesQueue.poll();
        if (null == sendingMessage) { // there is no messages in queue
            return;
        }

        System.out.println("Trying to send " + sendingMessage.getMessageType().toString() +
                " message to " + sendingMessage.getDestAddress().toString());

        ByteBuffer dataBuffer = Parser.extractData(sendingMessage);
        int res = channel.send(dataBuffer, sendingMessage.getDestAddress());

        if (0 == res) { // insufficient room for the datagram in the underlying output buffer
            System.out.println("insufficient room for the datagram in the underlying output buffer");
            sendingMessagesQueue.add(sendingMessage);
            return;
        }

        System.out.println("Successfully sent " + sendingMessage.getMessageType().toString() +
                " packet to " + sendingMessage.getDestAddress().toString());
    }

    private void processPacket(Message message) {
        // message loss imitation
        int rand = ThreadLocalRandom.current().nextInt(0, 100);
        if (rand < lossRate) {
            System.out.println(message.getMessageType().toString() + " message from " +
                    message.getDestAddress().toString() + " was rejected by packetLoss filter");
            return;
        }

        System.out.println(message.getMessageType().toString() + " message received from " +
                message.getDestAddress().toString());
        switch (message.getMessageType()) {
            case CONN_REQ:
                dataExecutor.submit(() -> processMessage((ConnReqMessage) message));
                break;
            case CONN_RESP:
                dataExecutor.submit(() -> processMessage((ConnRespMessage) message));
                break;
            case CONN_ACK:
                dataExecutor.submit(() -> processMessage((ConnAckMessage) message));
                break;
            case TEXT:
                dataExecutor.submit(() -> processMessage((TextMessage) message));
                break;
            case TEXT_ACK:
                dataExecutor.submit(() -> processMessage((TextAckMessage) message));
                break;
            default:
                throw new AssertionError("Invalid message type");
        }
    }

    private void processMessage(ConnAckMessage receivedMessage) {
        if (pendingStorage.containsPending(receivedMessage)) {
            pendingStorage.removePending(receivedMessage);

            System.out.println("Added node on " + receivedMessage.getDestAddress());
            nodes.add(receivedMessage.getDestAddress());
        }
    }

    private void processMessage(ConnReqMessage receivedMessage) {
        if (!nodes.contains(receivedMessage.getDestAddress())) {
            Message responseMessage = new ConnRespMessage(receivedMessage.getDestAddress());
            sendingMessagesQueue.add(responseMessage);

            pendingStorage.addPendingBySentMessage(responseMessage);
        }
    }

    private void processMessage(ConnRespMessage receivedMessage) {
        if (pendingStorage.containsPending(receivedMessage)) {
            pendingStorage.removePending(receivedMessage);
            Message responseMessage = new ConnAckMessage(receivedMessage.getDestAddress());

            sendingMessagesQueue.add(responseMessage);
            pendingStorage.addPendingBySentMessage(responseMessage);
        }
        // else ignore packet
    }

    private void processMessage(TextAckMessage receivedMessage) {
        if (pendingStorage.containsPending(receivedMessage)) {
            pendingStorage.removePending(receivedMessage);
        }
        // else ignore receivedMessage
    }

    private void processMessage(TextMessage receivedMessage) {
        if (logger.isLogged(receivedMessage.getUuid())) { // we have already received this package
            Optional<Message> optional = getPending(receivedMessage);
            // send acknowledge if it exists
            optional.ifPresent(sendingMessagesQueue::add);
        } else { // received new message
            showMessage(receivedMessage);
            logger.log(receivedMessage.getUuid());
            // send message to all another nodes
            for (SocketAddress address : nodes) {
                if (!address.equals(receivedMessage.getDestAddress())) {
                    Message responseMessage = new TextMessage(address,
                            receivedMessage.getUuid(), receivedMessage.getName(), receivedMessage.getData());

                    sendingMessagesQueue.add(responseMessage);
                    pendingStorage.addPendingBySentMessage(responseMessage);
                }
            }
        }
    }

    private void checkPendings() {
        Instant now = Instant.now();
        for (Message msg : pendingStorage.pendingInfos.keySet()) {
            if (MessageType.TEXT ==  msg.getMessageType() &&
                    !nodes.contains(msg.getDestAddress())) {

                Optional<Message> optional = getPending(msg);
                optional.ifPresent(pendingStorage::removePending);
                continue;
            }

            PendingStorage.PendingInfo pendingInfo = pendingStorage.pendingInfos.get(msg);
            if (Duration.between(pendingInfo.getLastSendTime(), now).compareTo(RESENDING_INTERVAL) > 0) {

                System.out.println(msg.getMessageType().toString() + " message from " +
                        msg.getDestAddress().toString() + " acknowledge receiving timeout.");

                if (pendingInfo.getResendingCount() >= MAX_RESENDING_COUNT) {
                    Optional<Message> optional = getPending(msg);
                    optional.ifPresent(pendingStorage::removePending);

                    if (MessageType.TEXT == msg.getMessageType()) {
                        System.out.println("Attempts limit reached. Disconnecting " + msg.getDestAddress() + " node.");
                        nodes.remove(msg.getDestAddress());
                    }

                } else { // send a copy of package
                    System.out.println("Trying to send new copy.. Attempts last: " +
                            pendingInfo.getResendingCount());
                    sendingMessagesQueue.add(msg);
                    pendingInfo.incrementResendingCount();
                    pendingInfo.updateLastTime(now);
                }
            }
        }

    }

    private void cleanup() {
        dataExecutor.shutdown();
    }

    private void showMessage(TextMessage message) {
        System.out.println(message.getName() + ": " + message.getData());
    }

    private static @NotNull Optional<Message> getPending(@NotNull Message message) {
        switch (message.getMessageType()) {
            case CONN_REQ:
                return Optional.of(new ConnRespMessage(message.getDestAddress()));
            case CONN_RESP:
                return Optional.of(new ConnAckMessage(message.getDestAddress()));
            case CONN_ACK:
                return Optional.empty();
            case TEXT:
                TextMessage textMessage = (TextMessage) message;
                return Optional.of(new TextAckMessage(textMessage.getDestAddress(), textMessage.getUuid()));
            case TEXT_ACK:
                return Optional.empty();
            default:
                throw new AssertionError("Invalid message type.");
        }
    }

private static class PendingStorage {
    PendingStorage() {
    }

    final HashMap<Message, Message> expectedAcksToSentMessages = new HashMap<>();
    final HashMap<Message, PendingInfo> pendingInfos = new HashMap<>();

    void addPendingBySentMessage(@NotNull Message sentMessage) {
        Optional<Message> optional = getPending(sentMessage);
        if (optional.isPresent()) {
            Message pendingMessage = optional.get();
            if (!expectedAcksToSentMessages.containsKey(pendingMessage)) {
                expectedAcksToSentMessages.put(pendingMessage, sentMessage);
                pendingInfos.put(sentMessage, new PendingInfo(0, Instant.now()));
            }
            // else skip message, it is already in storage
        }
        // else skip message, it doesn't need acknowledge
    }

    boolean containsPending(@NotNull Message receivedMessage) {
        return expectedAcksToSentMessages.containsKey(receivedMessage);
    }

    /**
     * @param receivedMessage represents received message by node
     */
    void removePending(@NotNull Message receivedMessage) {
        Message sentMessage = expectedAcksToSentMessages.get(receivedMessage);
        if (null != sentMessage) {
            pendingInfos.remove(sentMessage);
            expectedAcksToSentMessages.remove(receivedMessage);
        }
    }

    private static class PendingInfo {
        PendingInfo(int resendingCount, @NotNull Instant lastSendTime) {
            this.resendingCount = resendingCount;
            this.lastSendTime = lastSendTime;
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

        private int resendingCount;
        private Instant lastSendTime;
    }
}

private static class MessageLogger {
    MessageLogger() {
    }

    void log(@NotNull UUID uuid) {
        logs.add(uuid);
    }

    boolean isLogged(@NotNull UUID uuid) {
        return logs.contains(uuid);
    }

    private final HashSet<UUID> logs = new HashSet<>();
}

    private final String name;
    private final int lossRate;
    private final InetSocketAddress localAddress;
    private final InetSocketAddress parentAddress;

    private boolean isRunning = false;

    private final PendingStorage pendingStorage = new PendingStorage();
    private final MessageLogger logger = new MessageLogger();

    private final ByteBuffer inputBuffer = ByteBuffer.allocate(MAX_MSG_SIZE);
    private final ExecutorService dataExecutor = Executors.newSingleThreadExecutor();

    private final ConcurrentLinkedQueue<Message> sendingMessagesQueue = new ConcurrentLinkedQueue<>();

    private final LinkedList<SocketAddress> nodes = new LinkedList<>();
}

