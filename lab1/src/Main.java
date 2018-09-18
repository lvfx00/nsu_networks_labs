import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static final int port = 6787;
    public static final int BUF_SIZE = 100;

    private static List<InetAddress> joinedCopies = new ArrayList<>(); // list for storing joined copies

    private static void printList() {
        System.out.println("Joined copies:");
        for (InetAddress addr : joinedCopies) {
            System.out.println(addr.toString());
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
        try (MulticastSocket sock = new MulticastSocket(port)) {

            sock.joinGroup(groupAddr);

            // send "leave" message when SIGINT received
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                String msg = "leave";
                DatagramPacket sndDg = new DatagramPacket(msg.getBytes(), msg.length(), groupAddr, port);
                try {
                    sock.send(sndDg);
                    sock.leaveGroup(groupAddr);
                } catch (IOException e) {
                    System.err.println(e.getMessage());
                }
            }));

            // send message to the group about that we have joined the group
            String msg = "join";
            DatagramPacket sndDg = new DatagramPacket(msg.getBytes(), msg.length(), groupAddr, port);
            sock.send(sndDg);

            // get their responses!
            byte[] buf = new byte[BUF_SIZE];
            DatagramPacket recvDg = new DatagramPacket(buf, BUF_SIZE);

            while (true) {
                sock.receive(recvDg);
                String recvMsg = new String(recvDg.getData(), recvDg.getOffset(), recvDg.getLength());

                if (recvMsg.equals("join")) {
                    joinedCopies.add(recvDg.getAddress());
                    printList();
                } else if (recvMsg.equals("leave")) {
                    joinedCopies.remove(recvDg.getAddress());
                    printList(); // print only if list was changed
                }
                // else ignore invalid message
            }

        } catch (IOException e) {
            System.err.println(e.getMessage());
        }
    }
}
