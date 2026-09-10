package org.redis_server;

import org.redis_server.models.RespParser;
import org.redis_server.models.RespValue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        int port = 6379;

        try (ServerSocket serverSocket = new ServerSocket(port)){
            serverSocket.setReuseAddress(true);

            System.out.println("Redis Lite server started and listening on port " + port + "...");

            boolean running = true;

            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    Thread.ofVirtual().start(() -> handleClient(clientSocket));
                } catch (IOException e) {
                    if (!running) {
                        System.out.println("Server shutting down...");
                        break;
                    }
                    // Handle other accept errors
                }
            }
        }
        catch (IOException e) {
            System.out.println("Server Exception: " + e.getMessage());
        }
    }

    private static void handleClient(Socket clientSocket) {
        try (
                BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                OutputStream outputStream = clientSocket.getOutputStream()
        ) {
            RespParser parser = new RespParser(reader);

            while (true) {
                try {
                    RespValue request = parser.parse();
                    if (request == null) break;

                    List<RespValue> elements = request.getArrayValue();
                    if (elements == null || elements.isEmpty()) continue;

                    String command = elements.get(0).getStringValue().toUpperCase();
                    RespValue response;

                    switch (command) {
                        case "PING":
                            response = RespValue.createSimpleString("PONG");
                            break;

                        case "ECHO":
                            if (elements.size() > 1) {
                                String message = elements.get(1).getStringValue();
                                response = RespValue.createBulkString(message);
                            } else {
                                response = RespValue.createError("ERR wrong number of arguments for 'echo' command");
                            }
                            break;

                        default:
                            response = RespValue.createError("ERR unknown command '" + command + "'");
                            break;
                    }

                    outputStream.write(response.serialize().getBytes());
                    outputStream.flush();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        } catch (IOException e) {
            System.out.println("Client disconnected or error: " + e.getMessage());
        }
        finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                System.out.println("An Error occurred while closing connection");
            }
        }
    }
}