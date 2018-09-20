import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

public class Main {
    private static final int port = 6787;
    private static final int BUF_SIZE = 100;
    private static final int UPDATE_INTERVAL = 1000; // in millisecond
    private static final String KEEP_ALIVE_STRING = "keep_alive";
    private static final int KEEP_ALIVE_MSG_LEN =
            KEEP_ALIVE_STRING.getBytes(StandardCharsets.UTF_8).length + Long.BYTES;
    private static final Clock clock = Clock.systemUTC();

    private static ConcurrentHashMap<InstanceInfo, Instant> instanceInfoMap = new ConcurrentHashMap<>();

    private static void printList() {
        System.out.println("Joined copies:");
        if (0 == instanceInfoMap.size()) {
            System.out.println("(none)");
            return;
        }
        for (InstanceInfo inst : instanceInfoMap.keySet()) {
            System.out.println(inst.getAddress().toString() + " (id " + inst.getId() + ")");
        }
    }

    public static void main(String[] args) {
        if (0 == args.length) {
            System.err.println("You need to specify multicast IP address of the group");
            return;
        }

        // fetch InetAddr from cmd arg
        InetAddress groupAddr;
        try {
            groupAddr = InetAddress.getByName(args[0]);
        } catch (UnknownHostException e) {
            System.err.println(e.getMessage());
            return;
        }

        // check whether specified address is multicast
        if (!groupAddr.isMulticastAddress()) {
            System.err.println("Specified address is not from multicast address space");
            return;
        }

        // create multicast socket
        try {
            MulticastSocket sock = new MulticastSocket(port);
            sock.joinGroup(groupAddr);

            // process ID. Using as unique on local machine
            long pid = ProcessHandle.current().pid();

            Thread inputThread = new Thread(() -> checkInput(sock, groupAddr));
            Thread sendThread = new Thread(() -> sendKeepAlive(sock, groupAddr, pid));
            Thread recvThread = new Thread(() -> readGroupMessages(sock));
            Thread checkThread = new Thread(() -> checkKeepAlive(sock));

            inputThread.start();
            recvThread.start();
            sendThread.start();
            checkThread.start();

            try {
                inputThread.join();
            } catch (InterruptedException e) {
                System.err.println(e.getMessage());
                return;
            }

            sock.close();
            sendThread.interrupt(); // to don't wait until sending thread awakes

        } catch (IOException e) {
            System.err.println(e.getMessage());
        }
    }

    private static void checkInput(MulticastSocket sock, InetAddress groupAddr) {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        // check input
        String inputString;
        do {
            try {
                inputString = br.readLine();
            } catch (IOException e) {
                System.err.println(e.getMessage());
                return;
            }
        } while (inputString != null && !inputString.equals("exit"));
    }

    private static void readGroupMessages(MulticastSocket sock) {
        byte[] buf = new byte[BUF_SIZE];
        DatagramPacket recvDg = new DatagramPacket(buf, BUF_SIZE);

        while (true) {
            try {
                sock.receive(recvDg);
            } catch (IOException e) {
                if (!sock.isClosed())
                    System.err.println(e.getMessage());
                return;
            }

            long id = parseGroupInput(recvDg.getData());
            if (id != -1) {
                InstanceInfo inst = new InstanceInfo(recvDg.getAddress(), id);
                if (instanceInfoMap.containsKey(inst)) {
                    instanceInfoMap.replace(inst, clock.instant()); // update last keep alive time
                } else {
                    instanceInfoMap.put(inst, clock.instant()); // add new instance
                    printList();
                }
            }
            // else ignore invalid message
        }
    }

    private static void checkKeepAlive(MulticastSocket sock) {
        while (true) {
            if (sock.isClosed())
                return;

            Instant now = clock.instant();
            boolean anyRemoved = false;
            for (InstanceInfo info : instanceInfoMap.keySet()) {
                if (Duration.between(instanceInfoMap.get(info), now).toMillis() > UPDATE_INTERVAL * 3) {
                    instanceInfoMap.remove(info);
                    anyRemoved = true;
                }
            }
            if (anyRemoved) {
                printList();
            }
        }
    }

    // tries to parse argument in following format: "<id>keep_alive"
    // upon successful parsing returns id value, otherwise -1
    private static long parseGroupInput(byte[] data) {
        if (data.length < KEEP_ALIVE_MSG_LEN)
            return -1;

        ByteBuffer buffer = ByteBuffer.wrap(data, 0, KEEP_ALIVE_MSG_LEN);
        long id = buffer.getLong();

        byte[] arr = new byte[buffer.remaining()];
        buffer.get(arr);
        String msg = new String(arr, StandardCharsets.UTF_8);

        return (msg.equals(KEEP_ALIVE_STRING)) ? id : -1;
    }

    // send keep alive messages to the group
    private static void sendKeepAlive(MulticastSocket sock, InetAddress groupAddr, long id) {
        ByteBuffer buffer = ByteBuffer.allocate(BUF_SIZE);
        buffer.putLong(id);
        buffer.put(KEEP_ALIVE_STRING.getBytes(StandardCharsets.UTF_8));

        DatagramPacket sndDg = new DatagramPacket(buffer.array(), KEEP_ALIVE_MSG_LEN, groupAddr, port);
        for (; ; ) {
            try {
                sock.send(sndDg);
            } catch (IOException e) {
                if (!sock.isClosed())
                    System.err.println(e.getMessage());
                return;
            }
            try {
                Thread.sleep(UPDATE_INTERVAL);
            } catch (InterruptedException e) {
                return;
            }
        }
    }
}

class InstanceInfo {
    InstanceInfo(InetAddress address, long id) {
        this.address = address;
        this.id = id;
    }

    InetAddress getAddress() {
        return address;
    }

    long getId() {
        return id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (null == o || o.getClass() != getClass()) return false;

        InstanceInfo inst = (InstanceInfo) o;
        if ((address != null) ? !address.equals(inst.address) : inst.address != null) return false;
        if (id != inst.id) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = (int) (id ^ (id >>> 32));
        result = 31 * result + (address != null ? address.hashCode() : 0);
        return result;
    }

    private InetAddress address;
    private long id;
}
