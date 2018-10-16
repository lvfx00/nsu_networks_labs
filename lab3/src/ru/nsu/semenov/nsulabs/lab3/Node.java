package ru.nsu.semenov.nsulabs.lab3;

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;
import ru.nsu.semenov.nsulabs.lab3.packets.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class Node {
    static final long SELECTOR_TIMEOUT = 1000; // in milliseconds
    static final int MAX_MSG_SIZE = 32 * 1024; // in bytes

    private Node(@NotNull String name,
                 int lossRate,
                 @NotNull InetSocketAddress localAddress,
                 @Nullable InetSocketAddress parentAddress) {
        this.name = name;
        this.lossRate = lossRate;
        this.localAddress = localAddress;
        this.parentAddress = parentAddress;
    }

    public static @NotNull
    Node newInstance(@NotNull String name,
                     int lossRate,
                     @NotNull InetSocketAddress localAddress,
                     @NotNull InetSocketAddress parentAddress) {
        return new Node(name, lossRate, localAddress, parentAddress);
    }

    public static @NotNull
    Node newInstance(@NotNull String name,
                     int lossRate,
                     @NotNull InetSocketAddress localAddress) {
        return new Node(name, lossRate, localAddress, null);
    }

    public void run() {
        try (DatagramChannel datagramChannel = DatagramChannel.open();
             Selector selector = Selector.open()) {
            datagramChannel.bind(localAddress);
            datagramChannel.configureBlocking(false);

            datagramChannel.register(selector, SelectionKey.OP_CONNECT | SelectionKey.OP_WRITE | SelectionKey.OP_READ);

            if (null != parentAddress) {
                connect(parentAddress);
            }

            try {
                while (true) {
                    // TODO think about timeout
                    selector.select(SELECTOR_TIMEOUT);
                    Iterator<SelectionKey> keyIterator = selector.keys().iterator();

                    while (keyIterator.hasNext()) {
                        try {
                            SelectionKey key = keyIterator.next();

                            if (key.isValid() && key.isWritable()) {
                                handleWrite(key);
                            }

                            if (key.isValid() && key.isReadable()) {
                                handleRead(key);
                            }

                            // catch if errors while data processing occurs
                        } catch (IOException e) {
                            System.err.println(e.getMessage());
                            e.printStackTrace();
                        }
                    }
                }
                // catch if error in selector occurs
            } catch (IOException e) {
                System.err.println(e.getMessage());
                e.printStackTrace();
            }

            // catch if error in socket creation occurs occurs
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }
    }

    private void handleRead(SelectionKey key) throws IOException {
        DatagramChannel channel = (DatagramChannel) key.channel();
        SocketAddress senderAddress = channel.receive(inputBuffer);
        Packet packet;
        try {
            packet = Parser.parse(inputBuffer);
        } catch (IllegalArgumentException | BufferUnderflowException e) {
            System.err.println(e.getMessage());
            System.err.println("Invalid packet received. Skipping.");
            return;
        }
        processPacket(packet, senderAddress);
    }

    private void handleWrite(SelectionKey key) throws IOException {
        DatagramChannel channel = (DatagramChannel) key.channel();

        // can use peek in case if only one thread retrieves elements from queue as in our situation
        AddressedPacket addressedPacket = sendingMessagesQueue.peek();
        if (null == addressedPacket) { // there is no messages in queue
            return;
        }

        ByteBuffer dataBuffer = Parser.extractData(addressedPacket.packet);
        int res = channel.send(dataBuffer, addressedPacket.address);

        if(0 == res) { // insufficient room for the datagram in the underlying output buffer
            return;
        }
        sendingMessagesQueue.poll(); // remove sent message from the queue
    }

    private void processPacket(Packet packet, SocketAddress senderAddress) {
        switch (packet.getPacketType()) {
            case CONN_REQ:
                dataExecutor.execute(() -> processConnectionRequestPacket((ConnectionRequestPacket) packet, senderAddress));
                break;
            case CONN_RESP:
                dataExecutor.execute(() -> processConnectionResponsePacket((ConnectionResponsePacket) packet, senderAddress));
                break;
            case CONN_ACK:
                dataExecutor.execute(() -> processConnectionAcknowledgePacket((ConnectionAcknowledgePacket) packet, senderAddress));
                break;
            case MSG_SND:
                dataExecutor.execute(() -> processMessageSendPacket((MessageSendPacket) packet, senderAddress));
                break;
            case MSG_ACK:
                dataExecutor.execute(() -> processMessageAcknowledgePacket((MessageAcknowledgePacket) packet, senderAddress));
                break;
            default:
                System.err.println("Invalid packet received. Skipping.");
                break;
        }

    }

    private void processConnectionAcknowledgePacket(ConnectionAcknowledgePacket packet, SocketAddress senderAddress) {
        AddressedPacket sentPacket = new AddressedPacket(ConnectionResponsePacket.newInstance(), senderAddress);

        if (pendings.containsKey(sentPacket)) {
            pendings.remove(sentPacket);
            nodeInfos.add(new NodeInfo(senderAddress));
        }
        // else ignore packet
    }

    private void processConnectionRequestPacket(ConnectionRequestPacket packet, SocketAddress senderAddress) {
        sendingMessagesQueue.add(new AddressedPacket(ConnectionResponsePacket.newInstance(), senderAddress));
        pendings.put(new AddressedPacket(ConnectionAcknowledgePacket.newInstance(), senderAddress), PendingInfo.newInstance());
    }

    private void processConnectionResponsePacket(ConnectionResponsePacket packet, SocketAddress senderAddress) {
        AddressedPacket sentPacket = new AddressedPacket(ConnectionRequestPacket.newInstance(), senderAddress);

        if (pendings.containsKey(sentPacket)) {
            pendings.remove(sentPacket);
            sendingMessagesQueue.add(new AddressedPacket(ConnectionAcknowledgePacket.newInstance(), senderAddress));
            nodeInfos.add(new NodeInfo(senderAddress));
        }
        // else ignore packet
    }

    private void processMessageAcknowledgePacket(MessageAcknowledgePacket packet, SocketAddress senderAddress) {
        AddressedPacket sentPacket =
                new AddressedPacket(messageCacher.getFromCache(packet.getMessageUuid()), senderAddress);

        if (pendings.containsKey(sentPacket)) {
            pendings.remove(sentPacket);
            messageCacher.decreaseReceiversCount(packet.getMessageUuid());
        }
        // else ignore packet
    }

    private void processMessageSendPacket(MessageSendPacket packet, SocketAddress senderAddress) {
        // we have already received this package
        UUID messageUuid = packet.getMessageUuid();

        if (messageCacher.logged(messageUuid)) {
            // send acknowledge
            MessageAcknowledgePacket ackPacket = MessageAcknowledgePacket.newInstance(messageUuid);
            sendingMessagesQueue.add(new AddressedPacket(ackPacket, senderAddress));
        }
        // received new message
        else {
            showMessage(packet);

            // send message to all another nodes
            for (NodeInfo ni : nodeInfos) {
                if (!ni.address.equals(senderAddress)) {
                    sendingMessagesQueue.add(new AddressedPacket(packet, ni.address));
                }
            }

            messageCacher.cacheMessage(packet, nodeInfos.size() - 1);
        }
    }

    private void showMessage(MessageSendPacket message) {
        System.out.println(message.getName() + ": " + message.getData());
    }

    private static class AddressedPacket {
        AddressedPacket(Packet packet, SocketAddress address) {
            this.packet = packet;
            this.address = address;
        }

        final Packet packet;
        final SocketAddress address;
    }

    private static class NodeInfo {
        NodeInfo(SocketAddress address) {
            this.address = address;
        }

        SocketAddress address;
    }

    private final String name;
    private final int lossRate;
    private final InetSocketAddress localAddress;
    private final InetSocketAddress parentAddress;

    private final HashMap<AddressedPacket, PendingInfo> pendings = new HashMap<>();
    private final MessageCacher messageCacher = MessageCacher.newInstance();

    private final ByteBuffer inputBuffer = ByteBuffer.allocate(MAX_MSG_SIZE);
    private final Executor dataExecutor = Executors.newSingleThreadExecutor();

    // TODO think about implementing high-priority and low-priority message queues
    private final ConcurrentLinkedQueue<AddressedPacket> sendingMessagesQueue = new ConcurrentLinkedQueue<>();
    private final LinkedList<NodeInfo> nodeInfos = new LinkedList<>();
}

