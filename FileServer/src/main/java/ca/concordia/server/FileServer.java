package ca.concordia.server;
import ca.concordia.filesystem.FileSystemManager;

import java.net.ServerSocket;
import java.net.Socket;

public class FileServer {

    private FileSystemManager fsManager;
    private int port;

    public static void main(String[] args) {
        // Define default parameters for the file system and server
        int port = 8080;
        String fileSystemName = "server_filesystem.bin";
        // Define the total size (e.g., 10 blocks * 128 bytes/block)
        int totalSize = 10 * 128; 

        try {
            // 1. Create the server instance
            FileServer server = new FileServer(port, fileSystemName, totalSize);
            
            // 2. Start the server's listening loop
            server.start();
            
        } catch (Exception e) {
            System.err.println("Fatal error during server startup: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public FileServer(int port, String fileSystemName, int totalSize){
        try {
            // FIX 1: Correctly initialize FSM using the provided totalSize argument
            this.fsManager = new FileSystemManager(fileSystemName, totalSize); 
            this.port = port;
        } catch (Exception e) {
             System.err.println("FATAL: Failed to init FSM: " + e.getMessage());
             this.fsManager = null;
             this.port = port;
        }
    }

    public void start(){
        if (fsManager == null) {
             System.err.println("Server cannot start. FSM initialization failed.");
             return;
        }
        
        // FIX 2: Use the instance's 'port' variable instead of hardcoding 12345
        try (ServerSocket serverSocket = new ServerSocket(this.port)) { 
            System.out.println("Server started. Listening on port " + this.port + "...");

            while (true) {
                // Wait for a client connection
                Socket clientSocket = serverSocket.accept();
                System.out.println("Handling new client: " + clientSocket.getInetAddress().getHostAddress());
                
                // CRITICAL CHANGE: Implement Multithreading
                // Delegate the entire client session to a new ClientHandler thread.
               new Thread(new ClientHandler(clientSocket, fsManager)).start();
                
                
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Could not start server on port " + this.port);
        }
    }
    
    
}