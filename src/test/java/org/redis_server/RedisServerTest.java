package org.redis_server;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RedisServerTest {

    @BeforeAll
    public static void startServer() {
        // Start your server on a background thread for testing
        Thread serverThread = new Thread(() -> Main.main(new String[]{}));
        serverThread.start();

        // Give the server a moment to boot up
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String sendCommand(String respPayload) throws IOException {
        try (Socket socket = new Socket("localhost", 6379);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            out.print(respPayload);
            out.flush();

            char[] buffer = new char[1024];
            int read = in.read(buffer);
            return new String(buffer, 0, read);
        }
    }

    @Test
    public void testPingCommand() throws IOException {
        String response = sendCommand("*1\r\n$4\r\nPING\r\n");
        assertEquals("+PONG\r\n", response);
    }

    @Test
    public void testSetAndGetCommand() throws IOException {
        // SET Name John
        String setResult = sendCommand("*3\r\n$3\r\nSET\r\n$4\r\nName\r\n$4\r\nJohn\r\n");
        assertEquals("+OK\r\n", setResult);

        // GET Name
        String getResult = sendCommand("*2\r\n$3\r\nGET\r\n$4\r\nName\r\n");
        assertEquals("$4\r\nJohn\r\n", getResult);
    }

    @Test
    public void testGetNonExistentKey() throws IOException {
        String getResult = sendCommand("*2\r\n$3\r\nGET\r\n$9\r\nMissingKey\r\n");
        assertEquals("$-1\r\n", getResult);
    }
}
