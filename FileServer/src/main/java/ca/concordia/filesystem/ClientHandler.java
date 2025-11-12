package ca.concordia.filesystem;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Base64;
import java.util.List;

/**
 * Handles communication with a single client in a separate thread.
 */
public class ClientHandler extends Thread {
    private Socket clientSocket;
    private PrintWriter out;
    private BufferedReader in;
    private FileSystemManager fsm;

    public ClientHandler(Socket socket, FileSystemManager fsm) {
        this.clientSocket = socket;
        this.fsm = fsm;
    }

    @Override
    public void run() {
        try {
            out = new PrintWriter(clientSocket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            
            String commandLine;
            
            // Loop to handle multiple commands from the same client
            while ((commandLine = in.readLine()) != null) {
                String response = processCommand(commandLine);
                out.println(response);
            }

        } catch (IOException e) {
            System.err.println("ClientHandler Error: " + e.getMessage());
        } finally {
            try {
                // Cleanup resources
                in.close();
                out.close();
                clientSocket.close();
                System.out.println("Client disconnected: " + clientSocket.getInetAddress());
            } catch (IOException e) {
                System.err.println("Error closing resources: " + e.getMessage());
            }
        }
    }
    
    /**
     * Parses the command string and executes the corresponding FileSystemManager method.
     */
    private String processCommand(String commandLine) {
        String[] parts = commandLine.trim().split("\\s+", 2);
        String command = parts[0].toUpperCase();

        try {
            switch (command) {
                case "CREATE":
                    if (parts.length < 2) return "ERROR: Missing filename.";
                    fsm.createFile(parts[1]);
                    return "OK: File '" + parts[1] + "' created.";

                case "DELETE":
                    if (parts.length < 2) return "ERROR: Missing filename.";
                    fsm.deleteFile(parts[1]);
                    return "OK: File '" + parts[1] + "' deleted.";

                case "READ":
                    if (parts.length < 2) return "ERROR: Missing filename.";
                    byte[] data = fsm.readFile(parts[1]);
                    String base64Data = Base64.getEncoder().encodeToString(data);
                    return "OK:" + base64Data;

                case "WRITE":
                    String[] writeArgs = parts[1].split("\\s+", 2);
                    if (writeArgs.length < 2) return "ERROR: Missing filename or data.";
                    
                    String writeFilename = writeArgs[0];
                    String base64Input = writeArgs[1];
                    
                    byte[] writeData = Base64.getDecoder().decode(base64Input);
                    fsm.writeFile(writeFilename, writeData);
                    return "OK: File '" + writeFilename + "' written. Size: " + writeData.length + " bytes.";

                case "LIST":
                    List<String> files = fsm.listFiles();
                    return "OK: Files: " + String.join(" | ", files);

                case "SHUTDOWN":
                    System.out.println("Received shutdown command from client. Server will terminate.");
                    System.exit(0);
                    return "OK: Server shutting down.";

                default:
                    return "ERROR: Unknown command: " + command;
            }
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }
}