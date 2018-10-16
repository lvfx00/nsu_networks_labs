package ru.nsu.semenov.nsulabs.lab3;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

public class Main {
    public static void main(String[] args) {
        String name;
        int lossRate;
        InetSocketAddress localAddress;
        InetSocketAddress parentAddress;

        if (5 == args.length || 3 == args.length) {
            name = args[0];

            try {
                lossRate = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid loss rate specified");
            }
            if (lossRate < 0 || lossRate > 100)
                throw new IllegalArgumentException("Loss rate must be integer from 0 to 100)");

            try {
                int port = Integer.parseInt(args[2]);
                InetAddress localhost = InetAddress.getByName(args[3]);
                localAddress = new InetSocketAddress(localhost, port);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid local port specified");
            } catch (UnknownHostException e) {
                throw new IllegalArgumentException("Unable to resolve localhost");
            }

            if (5 == args.length) {
                try {
                    int port = Integer.parseInt(args[4]);
                    InetAddress localhost = InetAddress.getByName(args[3]);
                    parentAddress = new InetSocketAddress(localhost, port);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Invalid parent port specified");
                } catch (UnknownHostException e) {
                    throw new IllegalArgumentException("Unable to resolve parent address");
                }
            } else {
                parentAddress = null;
            }
        } else {
            throw new IllegalArgumentException("Usage: node-name loss-rate node-port [parent-ip parent-port]");
        }
    }
}
