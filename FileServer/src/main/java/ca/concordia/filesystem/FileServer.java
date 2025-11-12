package ca.concordia.filesystem;

import java.net.ServerSocket;
import java.net.Socket;
import java.io.IOException;

public class FileServer {
    private static final int PORT = 12345;
    private static final String DISK_FILE = "virtual_disk.bin";
    
    public static void main(String[] args) {
        FileSystemManager fsm = null;
        
        try {
            // Initialize the file system manager (This creates/loads the disk file)
            fsm = new FileSystemManager(DISK_FILE);
            
            try (ServerSocket serverSocket = new ServerSocket(PORT)) {
                System.out.println("File Server started. Listening on port " + PORT);
                
                // Main loop to accept client connections
                while (true) {
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("New client connected: " + clientSocket.getInetAddress());
                    
                    // Create a new thread (ClientHandler) for each client
                    ClientHandler handler = new ClientHandler(clientSocket, fsm);
                    handler.start();
                }
            } catch (IOException e) {
                System.err.println("Could not start server on port " + PORT + ": " + e.getMessage());
            }
            
        } catch (Exception e) {
            System.err.println("Error initializing FileSystemManager: " + e.getMessage());
        }
    }
}