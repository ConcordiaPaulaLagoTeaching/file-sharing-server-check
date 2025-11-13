package ca.concordia;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        
        System.out.println("Hello and welcome!");
        Scanner scanner = new Scanner(System.in);

        try{
            Socket clientSocket = new Socket("localhost", 8080);
            System.out.println("Connected to the server at localhost:12345");

       
            String userInput = scanner.nextLine();
            try (
                    BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                    PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true)
            ) {
                while (userInput != null && !userInput.isEmpty() && !userInput.equalsIgnoreCase("exit") && !userInput.equalsIgnoreCase("quit")) {
                    writer.println(userInput);
                    System.out.println("Message sent to the server: " + userInput);
                    
                    String response = reader.readLine();
                    System.out.println("Response from server: " + response);

                    userInput = scanner.nextLine(); 
                }

                
                clientSocket.close();
                System.out.println("Connection closed.");
            }catch (Exception e) {
                e.printStackTrace();
            } finally {
                scanner.close();
            }


        }catch(Exception e) {
            e.printStackTrace();
        }
    }
}