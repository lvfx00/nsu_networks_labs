package ru.nsu.fit.semenov.socksproxy;

import org.jetbrains.annotations.Nullable;
import ru.nsu.fit.semenov.socksproxy.socksprotocolspecs.ConnectionRequest;
import ru.nsu.fit.semenov.socksproxy.socksprotocolspecs.GreetingMessage;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

public class SocksClient {
    private static final int BUF_SIZE = 1024;

    private InetSocketAddress destAddress;

    private SocksClientState socksClientState;

    private final SocketChannel clientSocketChannel;
    private SocketChannel destSocketChannel;

    private final SelectionKey clientSelectionKey;
    private SelectionKey destSelectionKey;

    private final ByteBuffer clientToDestBuffer = ByteBuffer.allocate(BUF_SIZE);
    private final ByteBuffer destToClientBuffer = ByteBuffer.allocate(BUF_SIZE);

    private boolean closeUponSending;

    public SocksClient(SocketChannel clientSocketChannel,
                       SelectionKey clientSelectionKey) {

        this.clientSocketChannel = clientSocketChannel;
        this.clientSelectionKey = clientSelectionKey;

        socksClientState = SocksClientState.RECV_INIT_GREETING;

        closeUponSending = false;
    }

    public ByteBuffer getClientToDestBuffer() {
        return clientToDestBuffer;
    }

    public ByteBuffer getDestToClientBuffer() {
        return destToClientBuffer;
    }

    public SocketChannel getClientSocketChannel() {
        return clientSocketChannel;
    }

    public SocketChannel getDestSocketChannel() {
        return destSocketChannel;
    }

    public SelectionKey getClientSelectionKey() {
        return clientSelectionKey;
    }

    public SelectionKey getDestSelectionKey() {
        return destSelectionKey;
    }

    public void setDestSocketChannel(SocketChannel destSocketChannel) {
        this.destSocketChannel = destSocketChannel;
    }

    public void setDestSelectionKey(SelectionKey destSelectionKey) {
        this.destSelectionKey = destSelectionKey;
    }

    public SocksClientState getSocksClientState() {
        return socksClientState;
    }

    public void setSocksClientState(SocksClientState socksClientState) {
        this.socksClientState = socksClientState;
    }

    public @Nullable GreetingMessage extractClientGreeting() throws IllegalArgumentException {
        clientToDestBuffer.mark();
        try {
            return GreetingMessage.fromByteBuffer(clientToDestBuffer);

        } catch (BufferUnderflowException bue) {
            clientToDestBuffer.reset();
            return null;
        }
    }

    public @Nullable ConnectionRequest extractClientConnectionRequest() throws IllegalArgumentException {
        clientToDestBuffer.mark();
        try {
            return ConnectionRequest.fromByteBuffer(clientToDestBuffer);

        } catch (BufferUnderflowException bue) {
            clientToDestBuffer.reset();
            return null;
        }
    }

    public void closeClientSide() throws IOException {
        System.out.println("Closed connection with " + clientSocketChannel.getRemoteAddress() + " (client)");

        clientSelectionKey.cancel();
        clientSocketChannel.close();

        setSocksClientState(SocksClientState.CLOSED);
    }

    public void closeDestSide() throws IOException {
        System.out.println("Closed connection with " + destSocketChannel.getRemoteAddress() + " (destination)");

        destSelectionKey.cancel();
        destSocketChannel.close();

        setSocksClientState(SocksClientState.CLOSED);

    }

    public boolean isCloseUponSending() {
        return closeUponSending;
    }

    public void setCloseUponSending(boolean closeUponSending) {
        this.closeUponSending = closeUponSending;
    }

    public InetSocketAddress getDestAddress() {
        return destAddress;
    }

    public void setDestAddress(InetSocketAddress destAddress) {
        this.destAddress = destAddress;
    }
}

