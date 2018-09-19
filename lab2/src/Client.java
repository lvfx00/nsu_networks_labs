import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Client {
    private static final int BUF_SIZE = 4096;

    public static void main(String[] args) {
        if (args.length < 3) {
            System.err.println("Usage: client filename server-address server-port");
            return;
        }

        // validate args
        Path filepath = Paths.get(args[0]);
        if (!Files.exists(filepath)) {
            System.err.println("No such file exists");
            return;
        }

        InetAddress servAddress;
        try {
            servAddress = InetAddress.getByName(args[1]);
        } catch (UnknownHostException e) {
            System.err.println(e.getMessage());
            return;
        }

        int serverPort;
        try {
            serverPort = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            System.err.println(e.getMessage());
            return;
        }
        if (serverPort < 0 || serverPort > 0xFFFF) {
            System.err.println("Invalid port number");
            return;
        }

        try (Socket clientSocket = new Socket(servAddress, serverPort);
             InputStream socketInputStream = clientSocket.getInputStream();
             OutputStream socketOutputStream = clientSocket.getOutputStream())
        {
            BufferedReader socketBufferedReader = new BufferedReader(new InputStreamReader(socketInputStream));
            PrintWriter socketPrintWriter = new PrintWriter(new OutputStreamWriter(socketOutputStream));

            // send file info
            JSONObject fileInfo = new JSONObject();
            fileInfo.put("name", filepath.getFileName());
            fileInfo.put("size", Files.size(filepath));

            socketPrintWriter.println(fileInfo.toString());
            socketPrintWriter.flush();

            // get response
            JSONObject serverResponse = receiveStatusMessage(socketBufferedReader);
            if (serverResponse.getString("status").equals("ERROR")) {
                System.err.println("ERROR: " + serverResponse.getString("details"));
                return;
            }

            // open file
            InputStream fileInputStream;
            try {
                fileInputStream = Files.newInputStream(filepath);
            } catch (IOException e) {
                System.err.println(e.getMessage());
                return;
            }

            // upload file data
            byte[] buffer = new byte[BUF_SIZE];
            int sndNum;
            while ((sndNum = fileInputStream.read(buffer, 0, buffer.length)) > 0) {
                socketOutputStream.write(buffer, 0, sndNum);
            }

            serverResponse = receiveStatusMessage(socketBufferedReader);
            if (serverResponse.getString("status").equals("ERROR")) {
                System.err.println("ERROR: " + serverResponse.getString("details"));
            }

            System.out.println("Successful uploading");

        } catch (IOException|JSONException e) {
            System.err.println(e.getMessage());
        }
    }

    private static JSONObject receiveStatusMessage(BufferedReader in) throws JSONException, IOException {
        String serverRespStr = in.readLine();

        if (null != serverRespStr) {
            JSONObject servResponse = new JSONObject(serverRespStr);
            if (servResponse.has("status") && servResponse.has("details")) {
                return servResponse;
            }
        }
        throw new JSONException("Invalid JSON received");
    }
}
