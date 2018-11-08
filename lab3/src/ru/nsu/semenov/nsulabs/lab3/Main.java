package ru.nsu.semenov.nsulabs.lab3;

import java.net.InetSocketAddress;
import java.util.Scanner;

public class Main {
    private static final String EXIT_STRING = "exit";

    public static void main(String[] args) {
        String name;
        int lossRate;
        int localPort;
        InetSocketAddress parentAddress;

        if (3 == args.length || 5 == args.length) {
            name = args[0];

            try {
                lossRate = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid loss rate specified");
                return;
            }
            if (lossRate < 0 || lossRate > 100) {
                System.err.println("Loss rate must be integer from 0 to 100)");
                return;
            }

            try {
                localPort = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid local port specified");
                return;
            }

            if (5 == args.length) {
                try {
                    int port = Integer.parseInt(args[4]);
                    parentAddress = new InetSocketAddress(args[3], port);
                } catch (NumberFormatException e) {
                    System.err.println("Invalid parent port specified");
                    return;
                } catch (IllegalArgumentException e) {
                    System.err.println("Invalid parent address specified");
                    return;
                }
            } else {
                parentAddress = null;
            }
        } else {
            System.err.println("Usage: node-name loss-rate node-port [parent-ip parent-port]");
            return;
        }

        Node myNode = Node.newInstance(name, lossRate, localPort, parentAddress);
        Thread nodeThread = new Thread(myNode::run);
        nodeThread.start();

        Scanner inputScanner = new Scanner(System.in);
        while (true) {
            String inputMessage = inputScanner.nextLine();
            if (EXIT_STRING.equals(inputMessage)) {
                myNode.stop();
                try {
                    nodeThread.join();
                    return;
                } catch (InterruptedException e) {
                    System.err.println(e.getMessage());
                }
            } else {
                myNode.sendMessage(inputMessage);
            }
        }
    }
}
