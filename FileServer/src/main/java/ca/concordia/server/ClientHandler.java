package ca.concordia.server;

import ca.concordia.filesystem.FileSystemManager;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class ClientHandler implements Runnable {

    private final Socket clientSocket;
    private final FileSystemManager fsManager;
    private final Lock readLock;
    private final Lock writeLock;

    public ClientHandler(Socket clientSocket,
                         FileSystemManager fsManager,
                         ReentrantReadWriteLock rwLock) {
        this.clientSocket = clientSocket;
        this.fsManager = fsManager;
        this.readLock = rwLock.readLock();
        this.writeLock = rwLock.writeLock();
    }

    @Override
    public void run() {
        System.out.println("Client connected: " + clientSocket);

        try (
                BufferedReader in = new BufferedReader(
                        new InputStreamReader(clientSocket.getInputStream()));
                PrintWriter out = new PrintWriter(
                        new BufferedWriter(
                                new OutputStreamWriter(clientSocket.getOutputStream())), true)
        ) {
            String line;
            while ((line = in.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                if (line.equalsIgnoreCase("quit") || line.equalsIgnoreCase("exit")) {
                    out.println("OK: bye");
                    break;
                }

                String response = handleCommand(line);
                out.println(response);
            }
        } catch (IOException e) {
            System.err.println("I/O error with client " + clientSocket + ": " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
            } catch (IOException ignored) {}
            System.out.println("Client disconnected: " + clientSocket);
        }
    }

    private String handleCommand(String line) {
        try {
            String[] parts = line.split(" ", 3);
            String command = parts[0].toUpperCase();

            switch (command) {
                case "CREATE": {
                    if (parts.length < 2) return "ERROR: usage: CREATE <filename>";
                    String filename = parts[1];

                    writeLock.lock();
                    try {
                        fsManager.createFile(filename);
                    } finally {
                        writeLock.unlock();
                    }
                    return "OK: file " + filename + " created";
                }

                case "DELETE": {
                    if (parts.length < 2) return "ERROR: usage: DELETE <filename>";
                    String filename = parts[1];

                    writeLock.lock();
                    try {
                        fsManager.deleteFile(filename);
                    } finally {
                        writeLock.unlock();
                    }
                    return "OK: file " + filename + " deleted";
                }

                case "WRITE": {
                    if (parts.length < 3) return "ERROR: usage: WRITE <filename> <content>";
                    String filename = parts[1];
                    String content = parts[2];

                    byte[] data = content.getBytes(StandardCharsets.UTF_8);

                    writeLock.lock();
                    try {
                        fsManager.writeFile(filename, data);
                    } finally {
                        writeLock.unlock();
                    }

                    return "OK: wrote " + data.length + " bytes to " + filename;
                }

                case "READ": {
                    if (parts.length < 2) return "ERROR: usage: READ <filename>";
                    String filename = parts[1];

                    byte[] data;
                    readLock.lock();
                    try {
                        data = fsManager.readFile(filename);
                    } finally {
                        readLock.unlock();
                    }

                  
                    return new String(data, StandardCharsets.UTF_8);
                }

                case "LIST": {
                    String[] files;
                    readLock.lock();
                    try {
                        files = fsManager.listFiles();
                    } finally {
                        readLock.unlock();
                    }

                    if (files.length == 0) {
                        return "OK: no files";
                    }
                    
                    return String.join(",", files);
                }

                default:
                    return "ERROR: unknown command";
            }
        } catch (Exception e) {
            
            String msg = e.getMessage();
            return (msg != null && !msg.isEmpty()) ? msg : "ERROR: " + e;
        }
    }
}
