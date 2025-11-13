package ca.concordia.server;

import ca.concordia.filesystem.FileSystemManager;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class FileServer {

    private final FileSystemManager fsManager;
    private final int port;

  
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock(true);

    public static void main(String[] args) {
        int port = 8080;
        String fileSystemName = "server_filesystem.bin";
        int totalSize = 10 * 128; 

        try {
            FileServer server = new FileServer(port, fileSystemName, totalSize);
            server.start();
        } catch (Exception e) {
            System.err.println("Fatal error during server startup: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public FileServer(int port, String fileSystemName, int totalSize) throws Exception {
        this.fsManager = new FileSystemManager(fileSystemName, totalSize);
        this.port = port;
    }

    public void start() {
        if (fsManager == null) {
            System.err.println("Server cannot start. FSM initialization failed.");
            return;
        }

        try (ServerSocket serverSocket = new ServerSocket(this.port)) {
            System.out.println("Server started. Listening on port " + this.port + "...");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Handling new client: " + clientSocket.getInetAddress().getHostAddress());

               
                new Thread(new ClientHandler(clientSocket, fsManager, rwLock)).start();
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Could not start server on port " + this.port);
        }
    }
}
