import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static java.nio.file.StandardOpenOption.WRITE;

public class Server {
    private static final int CORE_POOL_SIZE = 5;
    private static final int MAX_POOL_SIZE = 10;
    private static final int KEEP_ALIVE_TIME = 5000;

    private static final int BUF_SIZE = 4096;
    private static final Duration UPDATE_INTERVAL = Duration.ofMillis(3000);

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
                threadPool.execute(() -> processConnection(clientSocket));
            }
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }
    }

    private static void processConnection(Socket clientSocket) {
        String addressString = "[" + clientSocket.getInetAddress().toString() + ":" + clientSocket.getPort() + "]";

        try (InputStream socketInputStream = clientSocket.getInputStream();
             OutputStream socketOutputStream = clientSocket.getOutputStream()) {

            BufferedReader socketBufferedReader =
                    new BufferedReader(new InputStreamReader(socketInputStream));
            PrintWriter socketPrintWriter =
                    new PrintWriter(new OutputStreamWriter(socketOutputStream));


            // get filename and file size from client
            String clientInfo;
            clientInfo = socketBufferedReader.readLine();
            if(null == clientInfo) {
                System.err.println(addressString + " disconnected");
                return;
            }

            JSONObject fileInfo = new JSONObject(clientInfo);

            String filename;
            long size;
            try {
                filename = fileInfo.getString("name");
                size = fileInfo.getLong("size");
            } catch (JSONException e) {
                sendStatusMessage(socketPrintWriter, "ERROR", "Invalid file info");
                System.err.println(addressString + " sent invalid file info. Aborting connection");
                clientSocket.close();
                return;
            }
            String filenameString = "file " + filename + " (" + size + " bytes)";
            System.out.println(addressString + " requested to upload " + filenameString);

            // create file and stream on it
            Path filePath = Paths.get("./uploads/" + filename);
            OutputStream fileOutputStream;
            try {
                fileOutputStream = Files.newOutputStream(filePath, CREATE_NEW, WRITE);
            } catch (IOException e) {
                sendStatusMessage(socketPrintWriter, "ERROR", "File exists");
                System.err.println(addressString + " file " + filename + " exists. Aborting connection");
                clientSocket.close();
                return;
            }

            sendStatusMessage(socketPrintWriter, "SUCCESS", "Info accepted");

            Clock clock = Clock.systemUTC(); // init clocks to calculate uploading speed
            Instant beginning = clock.instant();

            // download and save file data
            long remain = size;
            int recvNum;
            byte[] buffer = new byte[BUF_SIZE];

            Instant lastCheckInstant = beginning;
            long lastUploadedSize = 0;

            while (remain > 0)  {
                recvNum = socketInputStream.read(buffer, 0, buffer.length);
                if (recvNum == -1) { // unexpected end of stream
                    System.err.println(addressString + " error occurred during downloading "
                            + filename + ". Aborting connection");
                    clientSocket.close();
                    // remove corrupted file
                    Files.delete(filePath);
                    return;
                }
                fileOutputStream.write(buffer, 0, recvNum);
                remain -= recvNum;

                // need to print new instant speed
                Duration deltaTime = Duration.between(lastCheckInstant, clock.instant());
                if (deltaTime.compareTo(UPDATE_INTERVAL) > 0) {
                    long deltaUploadedSize = size - remain - lastUploadedSize;
                    long instantSpeed = deltaUploadedSize / deltaTime.toMillis() * 1000;
                    System.out.println(addressString + " uploading file " + filenameString +
                            ". Uploading speed: " + instantSpeed + " bytes/sec");
                    lastUploadedSize = deltaUploadedSize;
                    lastCheckInstant = clock.instant();
                }
            }

            Instant ending = clock.instant();
            long averageSpeed = size / Duration.between(beginning, ending).toMillis() * 1000;

            // send response and print log
            sendStatusMessage(socketPrintWriter, "SUCCESS", "Successful uploading");
            clientSocket.close();
            System.out.println(addressString + " successfully uploaded " + filenameString +
                    ". Average speed: " + averageSpeed + " bytes/sec");

        } catch (IOException e) {
            System.err.println(e.getMessage());
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
