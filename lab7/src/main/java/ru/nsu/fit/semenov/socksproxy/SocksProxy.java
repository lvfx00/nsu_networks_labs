package ru.nsu.fit.semenov.socksproxy;

import org.jetbrains.annotations.Nullable;
import org.omg.PortableInterceptor.ACTIVE;
import org.xbill.DNS.*;
import ru.nsu.fit.semenov.socksproxy.socksprotocolspecs.*;

import java.io.IOException;
import java.net.*;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.*;

import static com.google.common.base.Preconditions.checkArgument;
import static java.nio.channels.SelectionKey.*;
import static ru.nsu.fit.semenov.socksproxy.SocketChannelRef.SocketChannelSide.CLIENT;
import static ru.nsu.fit.semenov.socksproxy.SocketChannelRef.SocketChannelSide.DESTINATION;

public class SocksProxy implements Runnable {
    private static final String DNS_SERVER_ADDR = "8.8.8.8";
    private static final int DNS_SERVER_PORT = 53;

    private final SimpleResolver simpleResolver;

    {
        try {
            simpleResolver = new SimpleResolver(DNS_SERVER_ADDR);
        } catch (UnknownHostException e) {
            throw new ExceptionInInitializerError();
        }
        simpleResolver.setPort(DNS_SERVER_PORT);
    }

    private static final byte VERSION = 0x05;
    private static final int BACKLOG = 10;

    private final int proxyPort;

    public SocksProxy(int port) {
        checkArgument(port > 0 && port < 65536);
        proxyPort = port;
    }

    @Override
    public void run() {
        try (ServerSocketChannel serverSocket = ServerSocketChannel.open();
             Selector selector = Selector.open()) {

            serverSocket.bind(new InetSocketAddress(proxyPort), BACKLOG);
            serverSocket.configureBlocking(false);

            serverSocket.register(selector, OP_ACCEPT);

            while (true) {
                selector.select();

                Set<SelectionKey> selectedKeys = selector.selectedKeys();
                Iterator<SelectionKey> iter = selectedKeys.iterator();
                while (iter.hasNext()) {
                    SelectionKey key = iter.next();

                    if (key.isValid() && key.isAcceptable()) {
                        handleAccept(key);
                    }
                    if (key.isValid() && key.isConnectable()) {
                        handleConnect(key);
                    }
                    if (key.isValid() && key.isReadable()) {
                        handleRead(key);
                    }
                    if (key.isValid() && key.isWritable()) {
                        handleWrite(key);
                    }

                    iter.remove();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println(e.getMessage());
        }
    }

    private void handleAccept(SelectionKey selectionKey) throws IOException {
        ServerSocketChannel serverSocketChannel = (ServerSocketChannel) selectionKey.channel();
        SocketChannel clientSocketChannel = serverSocketChannel.accept();

        System.out.println("Accepted incoming connection from " + clientSocketChannel.getRemoteAddress());

        clientSocketChannel.configureBlocking(false);
        SelectionKey clientSelectionKey = clientSocketChannel.register(selectionKey.selector(), OP_READ);

        SocksClient socksClient = new SocksClient(clientSocketChannel, clientSelectionKey);
        clientSelectionKey.attach(new SocketChannelRef(socksClient, CLIENT));
    }

    private void connectToDestination(SocksClient socksClient, InetSocketAddress address) throws IOException {
        SocketChannel destSocketChannel = SocketChannel.open();
        destSocketChannel.configureBlocking(false);
        destSocketChannel.connect(address);

        SelectionKey destSelectionKey = destSocketChannel.register(socksClient.getClientSelectionKey().selector(), OP_CONNECT);
        destSelectionKey.attach(new SocketChannelRef(socksClient, DESTINATION));

        socksClient.setDestSelectionKey(destSelectionKey);
        socksClient.setDestSocketChannel(destSocketChannel);
    }

    private void handleConnect(SelectionKey selectionKey) throws IOException {
        SocketChannel destSocketChannel = (SocketChannel) selectionKey.channel();
        SocketChannelRef socketChannelRef = (SocketChannelRef) selectionKey.attachment();
        SocksClient socksClient = socketChannelRef.getSocksClient();

        try {
            if (!destSocketChannel.finishConnect()) {
                throw new AssertionError("Unexpected behaviour: true or exception were expected");
            }

            System.out.println("Connected to " + destSocketChannel.getRemoteAddress());

            selectionKey.interestOps(0);
            socksClient.getClientSelectionKey().interestOps(OP_WRITE);
            socksClient.setSocksClientState(SocksClientState.SEND_CONN_RESP);

            ConnectionResponse response = new ConnectionResponse(VERSION, ConnRespCode.REQUEST_GRANTED,
                    AddressType.IPV4_ADDRESS, socksClient.getDestAddress().getAddress(), socksClient.getDestAddress().getPort());
            socksClient.getDestToClientBuffer().put(response.toByteArray());

        } catch (IOException e) {
            System.out.println("Unable to connect to " + destSocketChannel.getRemoteAddress());

            ConnectionResponse response = new ConnectionResponse(VERSION, ConnRespCode.HOST_UNREACHABLE,
                    AddressType.IPV4_ADDRESS, socksClient.getDestAddress().getAddress(), socksClient.getDestAddress().getPort());
            socksClient.getDestToClientBuffer().put(response.toByteArray());

            socksClient.setCloseUponSending(true);
            socksClient.getClientSelectionKey().interestOps(OP_WRITE);
            socksClient.setSocksClientState(SocksClientState.SEND_CONN_RESP);
        }
    }

    private void handleRead(SelectionKey selectionKey) throws IOException {
        SocketChannel socketChannel = (SocketChannel) selectionKey.channel();
        SocketChannelRef socketChannelRef = (SocketChannelRef) selectionKey.attachment();
        SocksClient socksClient = socketChannelRef.getSocksClient();

        switch (socketChannelRef.getSocketChannelSide()) {
            case CLIENT: {
                handleReadFromClient(socketChannel, socksClient, selectionKey);
                break;
            }
            case DESTINATION: {
                handleReadFromDestination(socketChannel, socksClient, selectionKey);
                break;
            }
            default:
                throw new AssertionError("Unexpected socket channel side received");
        }
    }

    private void handleReadFromClient(SocketChannel socketChannel, SocksClient socksClient, SelectionKey selectionKey) throws IOException {
        if (socksClient.getSocksClientState() == SocksClientState.CLOSED) {
            socksClient.closeClientSide();
            return;
        }

        // TODO add exception handling ???
        long recvNum;
        try {
            recvNum = socketChannel.read(socksClient.getClientToDestBuffer());
        } catch (IOException e) {
            System.err.println(e.getMessage());
            return;
        }

        if (recvNum == -1) {
            socksClient.closeClientSide();
            if (socksClient.getDestSelectionKey() != null) {
                socksClient.getDestSelectionKey().interestOps(socksClient.getDestSelectionKey().interestOps() & ~OP_READ);
            }
            return;
        }

        System.out.println("Read " + recvNum + " bytes from " + socketChannel.getRemoteAddress() + " (client)");

        switch (socksClient.getSocksClientState()) {
            case RECV_INIT_GREETING:
                processGreeting(socketChannel, socksClient);
                break;
            case RECV_CONN_REQ:
                processConnectionRequest(socketChannel, socksClient);
                break;
            case ACTIVE:
                // there is no empty space to read something from client socket channel
                if (socksClient.getClientToDestBuffer().remaining() == 0) {
                    selectionKey.interestOps(selectionKey.interestOps() & ~OP_READ);
                }

                // if we have read something from client, we can write it to destination socket channel
                if (recvNum > 0) {
                    socksClient.getDestSelectionKey().interestOps(
                            socksClient.getDestSelectionKey().interestOps() | OP_WRITE);
                }
                break;

            case SEND_GREETING_RESP:
            case CONNECTING_TO_DEST:
            case SEND_CONN_RESP:
            case CLOSED:
            default:
                throw new AssertionError("Invalid SocketClientState " + socksClient.getSocksClientState());
        }
    }

    private void handleReadFromDestination(SocketChannel socketChannel, SocksClient socksClient, SelectionKey selectionKey)
            throws IOException {
        if (socksClient.getSocksClientState() == SocksClientState.CLOSED) {
            socksClient.closeDestSide();
            return;
        }


        long recvNum;
        try {
            recvNum = socketChannel.read(socksClient.getDestToClientBuffer());
        } catch (IOException e) {
            System.err.println(e.getMessage());
            return;
        }

        System.out.println("Read " + recvNum + " bytes from " + socketChannel.getRemoteAddress());

        if (recvNum == -1) { // socket was closed by destination side
            System.out.println(socketChannel.getRemoteAddress() + " (destination) closed connection");
            socksClient.closeDestSide();
            socksClient.getClientSelectionKey().interestOps(socksClient.getClientSelectionKey().interestOps() & ~OP_READ);
            return;
        }

        // there is no empty space to read something from destination socket channel
        if (socksClient.getDestToClientBuffer().remaining() == 0) {
            selectionKey.interestOps(selectionKey.interestOps() & ~OP_READ);
        }

        // if we have read something from dest, we can write it to client socket channel
        if (recvNum > 0) {
            socksClient.getClientSelectionKey().interestOps(
                    socksClient.getClientSelectionKey().interestOps() | OP_WRITE);
        }
    }

    private void processGreeting(SocketChannel socketChannel, SocksClient socksClient) throws IOException {
        GreetingMessage greeting;

        try {
            greeting = socksClient.extractClientGreeting();

        } catch (IllegalArgumentException iae) {
            System.out.println("Invalid greeting message received from " + socketChannel.getRemoteAddress() +
                    ". Closing connection.");
            socksClient.closeClientSide();
            return;
        }

        if (greeting != null) {
            if (greeting.getSocksVersion() != VERSION) {
                System.out.println("Unsupported version of socks protocol from client " + socketChannel.getRemoteAddress());
                socksClient.closeClientSide();
                return;
            }

            GreetingResponse response;
            if (greeting.hasAuthMethod(AuthMethod.NO_AUTHENTICATION)) {
                response = new GreetingResponse(VERSION, AuthMethod.NO_AUTHENTICATION);

            } else {
                System.out.println("Client doesn't supports required auth method " + socketChannel.getRemoteAddress());
                response = new GreetingResponse(VERSION, AuthMethod.NO_ACCEPTABLE_METHOD);
                socksClient.setCloseUponSending(true);
            }

            socksClient.getDestToClientBuffer().put(response.toByteArray());
            socksClient.getClientSelectionKey().interestOps(OP_WRITE);
            socksClient.setSocksClientState(SocksClientState.SEND_GREETING_RESP);
        }
    }

    private void processConnectionRequest(SocketChannel socketChannel, SocksClient socksClient) throws IOException {
        ConnectionRequest request;

        try {
            request = socksClient.extractClientConnectionRequest();
        } catch (IllegalArgumentException iae) {
            System.out.println("Invalid connection request received from " + socketChannel.getRemoteAddress());
            socksClient.closeClientSide();
            return;
        }

        if (request != null) {
            if (request.getSocksVersion() != VERSION) {
                System.out.println("Unsupported version of socks protocol from client " + socketChannel.getRemoteAddress());
                socksClient.closeClientSide();
                return;
            }

            switch (request.getCommandNumber()) {
                case ESTABLISH_STREAM_CONNECTION:
                    InetAddress address;
                    switch (request.getAddressType()) {
                        case IPV4_ADDRESS: {
                            address = (Inet4Address) request.getAddress();
                            break;
                        }
                        case DOMAIN_NAME: {
                            address = resolveDomainName(socksClient, request);
                            break;
                        }
                        case IPV6_ADDRESS: {
                            address = (Inet6Address) request.getAddress();
                            break;
                        }
                        default:
                            throw new AssertionError("Invalid address type received");
                    }

                    if (address == null) {
                        System.out.println("Unable to resolve hostname " + request.getAddress());

                        ConnectionResponse response = new ConnectionResponse(VERSION, ConnRespCode.HOST_UNREACHABLE,
                                AddressType.IPV4_ADDRESS, InetAddress.getLocalHost(), proxyPort);

                        socksClient.setCloseUponSending(true);
                        socksClient.getClientSelectionKey().interestOps(OP_WRITE);
                        socksClient.setSocksClientState(SocksClientState.SEND_CONN_RESP);

                        socksClient.getDestToClientBuffer().put(response.toByteArray());
                    }

                    socksClient.getClientSelectionKey().interestOps(0);
                    socksClient.setSocksClientState(SocksClientState.CONNECTING_TO_DEST);

                    InetSocketAddress inetSocketAddress = new InetSocketAddress(address, request.getPort());
                    socksClient.setDestAddress(inetSocketAddress);
                    connectToDestination(socksClient, inetSocketAddress);

                    break;

                // only one command is supported now
                case ASSOCIATE_UDP_PORT:
                case ESTABLISH_PORT_BINDING:
                default:
                    System.out.println("Unsupported method received from " + socketChannel.getRemoteAddress());

                    ConnectionResponse response = new ConnectionResponse(VERSION, ConnRespCode.CMD_NOT_SUPPORTEED,
                            AddressType.IPV4_ADDRESS, InetAddress.getLocalHost(), proxyPort);

                    socksClient.setCloseUponSending(true);
                    socksClient.getClientSelectionKey().interestOps(OP_WRITE);
                    socksClient.setSocksClientState(SocksClientState.SEND_CONN_RESP);

                    socksClient.getDestToClientBuffer().put(response.toByteArray());
            }
        }
    }

    private @Nullable InetAddress resolveDomainName(SocksClient socksClient, ConnectionRequest connectionRequest) {
        try {
            Lookup lookup = new Lookup((String) connectionRequest.getAddress(), Type.A);
            lookup.setResolver(simpleResolver);

            Record[] result = lookup.run();
            if (result.length > 0) {
                return ((ARecord) result[0]).getAddress();
            } else {
                return null;
            }

        } catch (TextParseException e) {
            System.err.println(e.getMessage());
            return null;
        }
    }

    private void handleWrite(SelectionKey selectionKey) throws IOException {
        SocketChannel socketChannel = (SocketChannel) selectionKey.channel();
        SocketChannelRef socketChannelRef = (SocketChannelRef) selectionKey.attachment();
        SocksClient socksClient = socketChannelRef.getSocksClient();

        switch (socketChannelRef.getSocketChannelSide()) {
            case CLIENT: {
                handleWriteToClient(socketChannel, socksClient, selectionKey);
                break;
            }
            case DESTINATION: {
                socksClient.getClientToDestBuffer().flip();

                long writeNum = socketChannel.write(socksClient.getClientToDestBuffer());
                System.out.println("Write " + writeNum + " bytes to " + socketChannel.getRemoteAddress() + " (destination)");

                // there is no data to write to destination socket anymore
                if (socksClient.getClientToDestBuffer().remaining() == 0) {
                    selectionKey.interestOps(selectionKey.interestOps() & ~OP_WRITE);

                    if (socksClient.getSocksClientState() == SocksClientState.CLOSED) {
                        socksClient.closeDestSide();
                        return;
                    }
                }

                // if we write something to dest socket channel, we can read something from client socket channel
                if (writeNum > 0) {
                    socksClient.getClientSelectionKey().interestOps(
                            socksClient.getClientSelectionKey().interestOps() | OP_READ);
                }

                socksClient.getClientToDestBuffer().compact();
                break;
            }
            default:
                throw new AssertionError("Unexpected socket channel side received");
        }
    }

    private void handleWriteToClient(SocketChannel socketChannel, SocksClient socksClient, SelectionKey selectionKey) throws IOException {
        socksClient.getDestToClientBuffer().flip();

        long writeNum = socketChannel.write(socksClient.getDestToClientBuffer());
        System.out.println("Write " + writeNum + " bytes to " + socketChannel.getRemoteAddress() + " (client)");

        switch (socksClient.getSocksClientState()) {
            case SEND_GREETING_RESP:
                if (socksClient.getDestToClientBuffer().remaining() == 0) {
                    if (socksClient.isCloseUponSending()) {
                        socksClient.closeClientSide();
                    } else {
                        socksClient.setSocksClientState(SocksClientState.RECV_CONN_REQ);
                        socksClient.getClientSelectionKey().interestOps(OP_READ);
                    }
                }
                break;
            case SEND_CONN_RESP:
                if (socksClient.getDestToClientBuffer().remaining() == 0) {
                    if (socksClient.isCloseUponSending()) {
                        socksClient.closeClientSide();
                    } else {
                        socksClient.setSocksClientState(SocksClientState.ACTIVE);

                        socksClient.getClientSelectionKey().interestOps(OP_READ);
                        socksClient.getDestSelectionKey().interestOps(OP_READ);
                    }
                }
                break;
            case ACTIVE:
                // there is no data to write to client socket anymore
                if (socksClient.getDestToClientBuffer().remaining() == 0) {
                    selectionKey.interestOps(selectionKey.interestOps() & ~OP_WRITE);

                    if (socksClient.getSocksClientState() == SocksClientState.CLOSED) {
                        socksClient.closeDestSide();
                        return;
                    }
                }

                // if we write something to client socket channel, we can read something from dest socket channel
                if (writeNum > 0) {
                    socksClient.getDestSelectionKey().interestOps(
                            socksClient.getDestSelectionKey().interestOps() | OP_READ);
                }
                break;

            case CLOSED: {
                if (socksClient.getDestToClientBuffer().remaining() == 0) {
                    socksClient.closeClientSide();
                }
                break;
            }

            case RECV_INIT_GREETING:
            case RECV_CONN_REQ:
            case CONNECTING_TO_DEST:
            default:
                throw new AssertionError("Invalid SocketClientState");
        }

        socksClient.getDestToClientBuffer().compact();
    }
}

