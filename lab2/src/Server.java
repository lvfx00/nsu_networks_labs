import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static java.nio.file.StandardOpenOption.WRITE;

public class Server {
    private static final int CORE_POOL_SIZE = 5;
    private static final int MAX_POOL_SIZE = 10;
    private static final int KEEP_ALIVE_TIME = 5000;
    public static final int BUF_SIZE = 4096;

    public static void main(String[] args) {
        if (0 == args.length) {
            System.err.println("You need to specify listening port for server");
            return;
        }

        int serverPort;
        try {
            serverPort = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            System.err.println(e.getMessage());
            return;
        }

        if (serverPort < 0 || serverPort > 0xFFFF) {
            System.err.println("Invalid port number");
            return;
        }

        // create directory for storing files
        Path saveDir = Paths.get("./uploads");
        if (!Files.exists(saveDir)) {
            try {
                Files.createDirectory(saveDir);
            } catch (IOException e) {
                System.err.println(e.getMessage());
                return;
            }
        }

        // create worker threads
        ThreadPoolExecutor threadPool = new ThreadPoolExecutor(
                CORE_POOL_SIZE,
                MAX_POOL_SIZE,
                KEEP_ALIVE_TIME,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingDeque<>());

        try (ServerSocket serverSocket = new ServerSocket(serverPort)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();

                threadPool.execute(() -> {
                    System.out.println("Connected " + clientSocket.getInetAddress().toString());

                    try (InputStream socketInputStream = clientSocket.getInputStream();
                         OutputStream socketOutputStream = clientSocket.getOutputStream()) {

                        BufferedReader socketBufferedReader =
                                new BufferedReader(new InputStreamReader(socketInputStream));
                        PrintWriter socketPrintWriter =
                                new PrintWriter(new OutputStreamWriter(socketOutputStream));


                        // get filename and file size from client
                        String clientInfo;
                        clientInfo = socketBufferedReader.readLine();

                        JSONObject fileInfo = new JSONObject(clientInfo);

                        String filename;
                        long size;
                        try {
                            filename = fileInfo.getString("name");
                            size = fileInfo.getLong("size");
                        } catch (JSONException e) {
                            sendStatusMessage(socketPrintWriter, "ERROR", "Invalid file info");
                            clientSocket.close();
                            return;
                        }

                        // create file and stream on it
                        Path filePath = Paths.get("./uploads/" + filename);
                        OutputStream fileOutputStream;
                        try {
                            fileOutputStream = Files.newOutputStream(filePath, CREATE_NEW, WRITE);
                        } catch (IOException e) {
                            sendStatusMessage(socketPrintWriter, "ERROR", "Unable to create file");
                            clientSocket.close();
                            return;
                        }

                        sendStatusMessage(socketPrintWriter, "SUCCESS", "Valid file info");

                        // download and save file data
                        long remain = size;
                        int recvNum;
                        byte[] buffer = new byte[BUF_SIZE];
                        // TODO add speed status
                        while (remain > 0)  {
                            recvNum = socketInputStream.read(buffer, 0, buffer.length);
                            if (recvNum == -1) { // unexpected end of stream
                                clientSocket.close();
                                return;
                            }
                            fileOutputStream.write(buffer, 0, recvNum);
                            remain -= recvNum;
                        }

                        // send response
                        sendStatusMessage(socketPrintWriter, "SUCCESS", "Successful uploading");
                        clientSocket.close();

                    } catch (IOException e) {
                        System.err.println(e.getMessage());
                        return;
                    }
                });
            }
        } catch (IOException e) {
            System.err.println(e.getMessage());
            return;
        }
    }

    private static void sendStatusMessage(PrintWriter out, String status, String details) throws IOException {
        JSONObject errorInfo = new JSONObject();
        errorInfo.put("status", status);
        errorInfo.put("details", details);

        out.println(errorInfo.toString());
        out.flush();
    }
}
