package org.redis_server;

import org.redis_server.models.CommandHandler;
import org.redis_server.models.RedisValue;
import org.redis_server.models.RespParser;
import org.redis_server.models.RespValue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;

public class Main {

    private static final ConcurrentHashMap<String, RedisValue> redisStore = new ConcurrentHashMap<>();
    private static final CommandHandler commandHandler = new CommandHandler(redisStore);

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

                    RespValue response = commandHandler.handle(request.getArrayValue());

                    outputStream.write(response.serialize().getBytes());
                    outputStream.flush();
                } catch (IOException e) {
                    break;
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