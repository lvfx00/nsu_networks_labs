import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.*;
import java.util.ArrayList;
import java.util.List;

public class Main {
    private static final int port = 6787;
    private static final int BUF_SIZE = 100;
    private static final int UPDATE_INTERVAL = 3000; // in millisecond

    private static List<InetSocketAddress> joinedCopies = new ArrayList<>(); // list for storing joined copies

    private static void printList() {
        System.out.println("Joined copies:");
        if (0 == joinedCopies.size()) {
            System.out.println("(none)");
            return;
        }
        for (InetSocketAddress addr : joinedCopies) {
            System.out.println(addr.getAddress().toString() + ":" + addr.getPort());
        }
    }

    public static void main(String[] args) {
        if (0 == args.length) {
            System.err.println("You need to specify multicast IP address of the group");
            // TODO add multicast address hint
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

            Thread inputThread = new Thread(() -> checkInput(sock, groupAddr));
            Thread sendThread = new Thread(() -> sendMessagesToGroup(sock, groupAddr));
            Thread recvThread = new Thread(() -> readGroupMessages(sock));

            inputThread.start();
            recvThread.start();
            sendThread.start();

            try {
                inputThread.join();
            } catch (InterruptedException e) {
                System.err.println(e.getMessage());
                return;
            }

            sock.close();
            sendThread.interrupt();

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
        } while (!inputString.equals("exit"));

        // if "exit" received send "leave" message and shutdown
        String msg = "leave";
        DatagramPacket sndDg = new DatagramPacket(msg.getBytes(), msg.length(), groupAddr, port);
        try {
            sock.send(sndDg);
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }

    }

    private static void readGroupMessages(MulticastSocket sock) {
//        System.out.println("Recv thread started");
        // get their responses!
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
            String recvMsg = new String(recvDg.getData(), recvDg.getOffset(), recvDg.getLength());
            InetSocketAddress sockAddr = new InetSocketAddress(recvDg.getAddress(), recvDg.getPort());
            if (recvMsg.equals("join") && !joinedCopies.contains(sockAddr)){
                joinedCopies.add(sockAddr);
                printList();
            } else if (recvMsg.equals("leave") && joinedCopies.contains(sockAddr)) {
                joinedCopies.remove(sockAddr);
                printList();
            }
            // else ignore invalid message
        }
    }

    // send messages to the group about that we have joined the group
    private static void sendMessagesToGroup(MulticastSocket sock, InetAddress groupAddr) {
        String msg = "join";
        DatagramPacket sndDg = new DatagramPacket(msg.getBytes(), msg.length(), groupAddr, port);
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
