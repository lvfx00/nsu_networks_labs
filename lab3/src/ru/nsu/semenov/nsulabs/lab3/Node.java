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
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

public class Node {
    private static final long SELECTOR_TIMEOUT = 1000; // in milliseconds
    private static final int MAX_MSG_SIZE = 32 * 1024; // in bytes

    private static final Duration CHECK_INTERVAL = Duration.ofMillis(500);

    private static final Duration RESENDING_INTERVAL = Duration.ofMillis(1000);
    private static final int MAX_SENDING_COUNT = 10;

    private static final Duration KEEP_ALIVE_INTERVAL = Duration.ofMillis(500);
    private static final Duration MAX_WITHOUT_KEEP_ALIVE = Duration.ofMillis(10000);

    private final String name;
    private final int lossRate;
    private final InetSocketAddress localAddress;
    private final InetSocketAddress parentAddress;

    private boolean isRunning = false;
    private final HashMap<UUID, TextMessage> messageLogs = new HashMap<>();
    private final ExecutorService dataExecutor = Executors.newSingleThreadExecutor();
    private final ConcurrentLinkedQueue<Message> sendingMessagesQueue = new ConcurrentLinkedQueue<>();
    private final HashMap<SocketAddress, NodeInfo> nodes = new HashMap<>();

    private Node(@NotNull String name,
                 int lossRate,
                 int localPort,
                 @Nullable InetSocketAddress parentAddress) {
        this.name = name;
        this.lossRate = lossRate;
        this.localAddress = new InetSocketAddress(localPort);
        this.parentAddress = parentAddress;
    }

    public static @NotNull Node newInstance(@NotNull String name,
                                            int lossRate,
                                            int localPort,
                                            @Nullable InetSocketAddress parentAddress) {
        return new Node(name, lossRate, localPort, parentAddress);
    }

    public void run() {
        Instant lastCheck = Instant.MIN;

        try (DatagramChannel datagramChannel = DatagramChannel.open();
             Selector selector = Selector.open()) {
            datagramChannel.bind(localAddress);
            datagramChannel.configureBlocking(false);

            datagramChannel.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);

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
                    if (Duration.between(lastCheck, now).compareTo(CHECK_INTERVAL) > 0) {
                        dataExecutor.submit(this::checkPendings);
                        lastCheck = now;
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
        // send message to all another connectedNodes
        UUID uuid = UUID.randomUUID();
        messageLogs.put(uuid, TextMessage.newInstance(localAddress, uuid, name, messageText));

        for (Map.Entry<SocketAddress, NodeInfo> entry : nodes.entrySet()) {
            Message sendingMessage = TextMessage.newInstance(entry.getKey(), uuid, name, messageText);

            sendingMessagesQueue.add(sendingMessage);
            entry.getValue().textPendingInfoMap.put(uuid, new PendingInfo(1, Instant.now()));
        }
    }

    private void connect(@NotNull SocketAddress address) {
        if (nodes.containsKey(address)) {
            System.out.println(address + " already connected or connection in progress");
            return;
        }

        Message sendingMessage = ConnReqMessage.newInstance(address);
        final NodeInfo node = new NodeInfo(address, NodeInfo.NodeState.OUTGOING_CONN);
        nodes.put(address, node);

        sendingMessagesQueue.add(sendingMessage);
        node.connectionPendingInfo = new PendingInfo(1, Instant.now());
    }

    private void cleanup() {
        dataExecutor.shutdown();
    }

    private void showMessage(TextMessage message) {
        System.out.println(message.getName() + ": " + message.getText());
    }

    private void handleRead(SelectionKey key) throws IOException {
        ByteBuffer inputBuffer = ByteBuffer.allocate(MAX_MSG_SIZE);

        DatagramChannel channel = (DatagramChannel) key.channel();
        SocketAddress senderAddress = channel.receive(inputBuffer);

        Optional<Message> message = Parser.parse(inputBuffer, new ParserContext(senderAddress));
        message.ifPresent(this::handleMessage);
    }

    private void handleWrite(SelectionKey key) throws IOException {
        DatagramChannel channel = (DatagramChannel) key.channel();

        Message message = sendingMessagesQueue.peek();
        if (null == message) {
            return;
        }

        ByteBuffer dataBuffer = Parser.extractData(message);
        int res = channel.send(dataBuffer, message.getAddress());
        if (0 == res) { // insufficient room for the datagram in the underlying output buffer
            return;
        }

        sendingMessagesQueue.poll(); // remove sent message
        if (message.getMessageType() != MessageType.KEEP_ALIVE) {
            System.out.println("Sent message " + message.getMessageType() + " to " + message.getAddress());
        }
    }

    private void handleMessage(Message message) {

        if (message.getMessageType() != MessageType.KEEP_ALIVE) {
            // message loss imitation
            int rand = ThreadLocalRandom.current().nextInt(0, 100);
            if (rand < lossRate) {
                System.out.println("Lost message " + message.getMessageType() + " from " + message.getAddress());
                return;
            }

            System.out.println("Received message " + message.getMessageType() + " from " + message.getAddress());
        }

        switch (message.getMessageType()) {
            case CONN_REQ:
                dataExecutor.submit(() -> handleConnReqMessage((ConnReqMessage) message));
                break;
            case CONN_ACK:
                dataExecutor.submit(() -> handleConnAckMessage((ConnAckMessage) message));
                break;
            case TEXT:
                dataExecutor.submit(() -> handleTextMessage((TextMessage) message));
                break;
            case TEXT_ACK:
                dataExecutor.submit(() -> handleTextAckMessage((TextAckMessage) message));
                break;
            case KEEP_ALIVE:
                dataExecutor.submit(() -> handleKeepAliveMessage((KeepAliveMessage) message));
                break;
            default:
                throw new AssertionError("Invalid message type");
        }
    }

    private void handleConnReqMessage(@NotNull ConnReqMessage message) {
        NodeInfo nodeInfo = nodes.computeIfAbsent(message.getAddress(), (address) ->
                new NodeInfo(address, NodeInfo.NodeState.INCOMING_CONN));

        if (nodeInfo.state == NodeInfo.NodeState.INCOMING_CONN) {
            sendingMessagesQueue.add(ConnAckMessage.newInstance(message.getAddress()));

            nodeInfo.connectionPendingInfo.updateLastTime(Instant.now());
            nodeInfo.connectionPendingInfo.incrementResendingCount();
        }
    }

    private void handleConnAckMessage(@NotNull ConnAckMessage message) {
        NodeInfo nodeInfo = nodes.get(message.getAddress());
        if (nodeInfo != null) {
            switch (nodeInfo.state) {
                case INCOMING_CONN:
                    nodeInfo.lastReceivedKeepAlive = Instant.now();
                    nodeInfo.state = NodeInfo.NodeState.CONNECTED;
                    System.out.println("Established connection with " + message.getAddress());
                    break;
                case OUTGOING_CONN:
                    nodeInfo.lastReceivedKeepAlive = Instant.now();
                    nodeInfo.state = NodeInfo.NodeState.CONNECTED;
                    System.out.println("Established connection with " + message.getAddress());
                    sendingMessagesQueue.add(ConnAckMessage.newInstance(message.getAddress()));
                    break;
                case CONNECTED:
                    break;
                default:
                    throw new AssertionError("Invalid node state");
            }
        }
    }

    private void handleTextMessage(@NotNull TextMessage message) {
        NodeInfo nodeInfo = nodes.get(message.getAddress());
        if (nodeInfo != null) {
            if (messageLogs.containsKey(message.getUuid())) {
                sendingMessagesQueue.add(TextAckMessage.newInstance(message.getAddress(), message.getUuid()));
            } else {
                showMessage(message);
                messageLogs.put(message.getUuid(), message);

                // send acknowledge to sender
                sendingMessagesQueue.add(TextAckMessage.newInstance(message.getAddress(), message.getUuid()));

                // send message to all another connectedNodes
                for (Map.Entry<SocketAddress, NodeInfo> entry : nodes.entrySet()) {
                    if (!entry.getKey().equals(message.getAddress())) {

                        TextMessage textMessage = TextMessage.newInstance(entry.getKey(), message.getUuid(),
                                message.getName(), message.getText());

                        entry.getValue().textPendingInfoMap.put(textMessage.getUuid(), new PendingInfo(1, Instant.now()));
                        sendingMessagesQueue.add(textMessage);
                    }
                }
            }
        }
    }

    private void handleTextAckMessage(@NotNull TextAckMessage message) {
        NodeInfo nodeInfo = nodes.get(message.getAddress());
        if (nodeInfo != null) {
            nodeInfo.textPendingInfoMap.remove(message.getUuid());
        }
    }

    private void handleKeepAliveMessage(@NotNull KeepAliveMessage message) {
        NodeInfo nodeInfo = nodes.get(message.getAddress());
        if (nodeInfo != null) {
            nodeInfo.lastReceivedKeepAlive = Instant.now();
        }
    }

    private void checkPendings() {
        Instant now = Instant.now();
        LinkedList<SocketAddress> toRemove = new LinkedList<>();

        for (Map.Entry<SocketAddress, NodeInfo> entry : nodes.entrySet()) {
            NodeInfo nodeInfo = entry.getValue();
            switch (nodeInfo.state) {
                case CONNECTED:
                    for (Map.Entry<UUID, PendingInfo> textEntry : nodeInfo.textPendingInfoMap.entrySet()) {
                        if (Duration.between(textEntry.getValue().lastSendTime, now).compareTo(RESENDING_INTERVAL) > 0) {
                            if (textEntry.getValue().sendCount < MAX_SENDING_COUNT) {
                                textEntry.getValue().updateLastTime(now);
                                textEntry.getValue().incrementResendingCount();

                                TextMessage message = messageLogs.get(textEntry.getKey());
//                                System.out.println(message.getText());

                                sendingMessagesQueue.add(TextMessage.newInstance(entry.getKey(),
                                        message.getUuid(), message.getName(), message.getText()));

                            } else {
                                toRemove.add(entry.getKey());
                                System.out.println("Severed connection with " + entry.getKey());
                            }
                        }
                    }
                    break;
                case INCOMING_CONN:
                    if (Duration.between(nodeInfo.connectionPendingInfo.lastSendTime, now).compareTo(RESENDING_INTERVAL) > 0) {
                        if (nodeInfo.connectionPendingInfo.sendCount < MAX_SENDING_COUNT) {
                            sendingMessagesQueue.add(ConnAckMessage.newInstance(entry.getKey()));
                            nodeInfo.connectionPendingInfo.incrementResendingCount();
                            nodeInfo.connectionPendingInfo.updateLastTime(now);
                        } else {
                            toRemove.add(entry.getKey());
                            System.out.println("Unable to connect to " + entry.getKey());
                        }
                    }
                    break;
                case OUTGOING_CONN:
                    if (Duration.between(nodeInfo.connectionPendingInfo.lastSendTime, now).compareTo(RESENDING_INTERVAL) > 0) {
                        if (nodeInfo.connectionPendingInfo.sendCount < MAX_SENDING_COUNT) {
                            sendingMessagesQueue.add(ConnReqMessage.newInstance(entry.getKey()));
                            nodeInfo.connectionPendingInfo.incrementResendingCount();
                            nodeInfo.connectionPendingInfo.updateLastTime(now);
                        } else {
                            toRemove.add(entry.getKey());
                            System.out.println("Unable to connect to " + entry.getKey());
                        }
                    }
                    break;
                default:
                    throw new AssertionError("Invalid node state");
            }

        }

        for (Map.Entry<SocketAddress, NodeInfo> entry : nodes.entrySet()) {
            NodeInfo nodeInfo = entry.getValue();
            switch (nodeInfo.state) {
                case CONNECTED:
                    if (Duration.between(nodeInfo.lastReceivedKeepAlive, now).compareTo(MAX_WITHOUT_KEEP_ALIVE) > 0) {
                        toRemove.add(entry.getKey());
                        System.out.println("Severed connection with " + entry.getKey() + ". No keep alive received");
                    }
                    if (Duration.between(nodeInfo.lastSentKeepAlive, now).compareTo(KEEP_ALIVE_INTERVAL) > 0) {
                        sendingMessagesQueue.add(KeepAliveMessage.newInstance(entry.getKey()));
                        nodeInfo.lastSentKeepAlive = now;
                    }
                    break;
                default:
                    break;
            }
        }

        for (SocketAddress address : toRemove) {
            nodes.remove(address);
        }
    }

    private static class NodeInfo {
        public enum NodeState {
            CONNECTED,
            INCOMING_CONN,
            OUTGOING_CONN,
        }

        private NodeInfo(SocketAddress address, NodeState nodeState) {
            this.address = address;
            state = nodeState;
        }

        final HashMap<UUID, PendingInfo> textPendingInfoMap = new HashMap<>();
        PendingInfo connectionPendingInfo = new PendingInfo();
        Instant lastReceivedKeepAlive = Instant.MIN;
        Instant lastSentKeepAlive = Instant.MIN;

        NodeState state;
        private final SocketAddress address;
    }

    private static class PendingInfo {
        PendingInfo() {
            lastSendTime = Instant.MIN;
            sendCount = 0;
        }

        PendingInfo(int sendCount, Instant lastSendTime) {
            this.sendCount = sendCount;
            this.lastSendTime = lastSendTime;
        }

        int getSendCount() {
            return sendCount;
        }

        @NotNull Instant getLastSendTime() {
            return lastSendTime;
        }

        void incrementResendingCount() {
            sendCount++;
        }

        void updateLastTime(@NotNull Instant instant) {
            lastSendTime = instant;
        }

        private int sendCount;
        private Instant lastSendTime;
    }
}

