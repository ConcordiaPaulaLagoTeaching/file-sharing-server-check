package ca.concordia.server;

import ca.concordia.filesystem.FileSystemManager;
import java.net.Socket;
import java.io.*;

public class ClientHandler implements Runnable {

    private final Socket clientSocket;
    // Shared FSM instance for synchronized file operations
    private final FileSystemManager fileSystemManager; 

    public ClientHandler(Socket socket, FileSystemManager fsm) {
        this.clientSocket = socket;
        this.fileSystemManager = fsm;
    }

    @Override
    public void run() {
        try (
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            // Autoflush writer for immediate feedback
            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true); 
        ) {
            String commandLine;
            out.println("200 Ready. Welcome to the File Sharing Server.");

            while ((commandLine = in.readLine()) != null) {
                if (commandLine.trim().equalsIgnoreCase("QUIT")) {
                    out.println("200 OK. Disconnecting.");
                    break;
                }
                
                String response = executeCommand(commandLine);
                out.println(response);
            }

        } catch (IOException e) {
            // Client disconnect/I/O error handled silently
        } catch (Exception e) {
            System.err.println("Unexpected error in client session: " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                // Ignore close error
            }
        }
    }
    
    // Command execution logic
    private String executeCommand(String commandLine) {
        
        String[] tokens = commandLine.trim().split("\\s+", 2);
        String command = tokens[0].toUpperCase();
        String args = tokens.length > 1 ? tokens[1].trim() : "";
        
        try {
            switch (command) {
                case "CREATE":
                    // Format: CREATE filename
                    fileSystemManager.createFile(args);
                    return "200 OK. File '" + args + "' created.";

                case "READ":
                    // Format: READ filename length offset
                    String[] readArgs = args.split("\\s+");
                    String rFilename = readArgs[0];
                    int rLength = Integer.parseInt(readArgs[1]);
                    int rOffset = Integer.parseInt(readArgs[2]);
                    
                    byte[] readData = fileSystemManager.read(rFilename, rLength, rOffset);
                    
                    // Encode binary data (bytes) to Base64 (string)
                    String dataString = java.util.Base64.getEncoder().encodeToString(readData);
                    return "200 OK. " + readData.length + " bytes read. Data (Base64): " + dataString; 
                    
                case "DELETE":
                    // Format: DELETE filename
                    fileSystemManager.deleteFile(args);
                    return "200 OK. File '" + args + "' deleted.";
                    
                case "WRITE":
                    // Format: WRITE filename offset data(Base64)
                    String[] writeArgs = args.split("\\s+", 3); 
                    String wFilename = writeArgs[0];
                    int wOffset = Integer.parseInt(writeArgs[1]);
                    String dataBase64 = writeArgs[2];
                    
                    // Decode Base64 string back to binary data (bytes)
                    byte[] writeData = java.util.Base64.getDecoder().decode(dataBase64);
                    fileSystemManager.write(wFilename, writeData, wOffset);
                    return "200 OK. Wrote " + writeData.length + " bytes to '" + wFilename + "'.";

                case "LIST":
                    // Format: LIST
                    String[] files = fileSystemManager.listFiles();
                    String fileList = String.join(", ", files);
                    return "200 OK. Files: [" + (files.length > 0 ? fileList : "No files") + "]";

                default:
                    return "400 ERROR. Unknown command: " + command;
            }
        } catch (NumberFormatException e) {
            return "400 ERROR. Invalid numeric argument.";
        } catch (ArrayIndexOutOfBoundsException e) {
             return "400 ERROR. Missing arguments for command: " + command;
        } catch (Exception e) {
            // Catching FSM errors (e.g., file not found, no space)
            return "500 ERROR. File System failure: " + e.getMessage();
        }
    }
}