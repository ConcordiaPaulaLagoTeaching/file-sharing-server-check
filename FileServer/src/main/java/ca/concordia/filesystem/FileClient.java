package ca.concordia.filesystem;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;
import java.util.Base64;
import java.nio.charset.StandardCharsets;

public class FileClient {
    private static final String SERVER_IP = "127.0.0.1";
    private static final int SERVER_PORT = 12345;

    public static void main(String[] args) {
        try (
            Socket socket = new Socket(SERVER_IP, SERVER_PORT);
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            Scanner scanner = new Scanner(System.in)
        ) {
            System.out.println("Connected to File Server. Available commands: CREATE, WRITE, READ, DELETE, LIST, SHUTDOWN, EXIT.");
            System.out.println("Note: WRITE command requires Base64 data (e.g., WRITE test.txt SGVsbG8= for 'Hello').");

            String userInput;
            while (true) {
                System.out.print("Client> ");
                userInput = scanner.nextLine();
                if (userInput.equalsIgnoreCase("exit")) break;

                // 1. Send command to server
                out.println(userInput);

                // 2. Read response from server
                String response = in.readLine();
                if (response != null) {
                    // Handle READ command response and decode Base64 data
                    if (response.startsWith("OK:") && userInput.toUpperCase().startsWith("READ")) {
                        String base64Data = response.substring(3);
                        try {
                            byte[] data = Base64.getDecoder().decode(base64Data);
                            String content = new String(data, StandardCharsets.UTF_8); 
                            System.out.println("Server Response: OK (Read " + data.length + " bytes)");
                            System.out.println("--- FILE CONTENT ---\n" + content + "\n--------------------");
                        } catch (IllegalArgumentException e) {
                             System.out.println("Server Response: ERROR decoding data (Invalid Base64).");
                        }
                    } else {
                        System.out.println("Server Response: " + response);
                    }
                }
            }

        } catch (IOException e) {
            System.err.println("Client Error: Could not connect to server or connection lost: " + e.getMessage());
        }
    }
}