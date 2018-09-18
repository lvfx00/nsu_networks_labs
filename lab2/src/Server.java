import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class Server {
    private static final int CORE_POOL_SIZE = 5;
    private static final int MAX_POOL_SIZE = 10;
    private static final int KEEP_ALIVE_TIME = 5000;

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
            System.err.println("You need to specify listening port for server");
            return;
        }

        Path saveDir = Paths.get("./uploads");
        if (!Files.exists(saveDir)) {

        }


        ThreadPoolExecutor threadPool = new ThreadPoolExecutor(
                        CORE_POOL_SIZE,
                        MAX_POOL_SIZE,
                        KEEP_ALIVE_TIME,
                        TimeUnit.MILLISECONDS,
                        new LinkedBlockingDeque<>());

        try (ServerSocket serverSocket = new ServerSocket(serverPort)) {
            while(true) {
                Socket clientSocket = serverSocket.accept();

                threadPool.execute(() -> {

                });
            }
        } catch (IOException e) {
            System.err.println(e.getMessage());
            return;
        }
    }
}
