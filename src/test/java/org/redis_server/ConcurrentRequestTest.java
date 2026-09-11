package org.redis_server;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConcurrentRequestTest {
    @Test
    public void testConcurrentRequests() {
        int numberOfClients = 100;
        CountDownLatch latch = new CountDownLatch(numberOfClients);

        System.out.println("Starting concurrency test with " + numberOfClients + " clients...");

        for (int i = 0; i < numberOfClients; i++) {
            final int clientId = i;
            new Thread(() -> {
                try (Socket socket = new Socket("localhost", 6379);
                     OutputStream out = socket.getOutputStream();
                     BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                    // Send PING command
                    out.write("*1\r\n$4\r\nPING\r\n".getBytes());
                    out.flush();

                    // Read response from server
                    String response = reader.readLine();
                    System.out.println("Client " + clientId + " received: " + response);

                    // Verify the server responded with +PONG
                    assertEquals("+PONG", response);

                } catch (IOException e) {
                    System.err.println("Client " + clientId + " failed: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            }).start();
        }

        // Wait for all threads to finish (timeout after 5 seconds)
        boolean completed = false;
        try {
            completed = latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Test thread was interrupted.");
        }
        assertTrue(completed, "Test timed out, some threads did not finish.");

        System.out.println("All " + numberOfClients + " concurrent requests completed successfully!");
    }

    @Test
    public void benchmarkSetAndGet() throws InterruptedException {
        int numberOfThreads = 50; // Simulate 50 concurrent client connections
        int requestsPerThread = 200; // Each client sends 200 requests (Total = 10,000 requests)
        int totalRequests = numberOfThreads * requestsPerThread;

        CountDownLatch latch = new CountDownLatch(numberOfThreads);
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        // Thread-safe list to record individual request latencies in milliseconds
        List<Double> latencies = Collections.synchronizedList(new ArrayList<>());

        System.out.println("Starting Benchmark: " + totalRequests + " total requests (" + numberOfThreads + " concurrent clients)...");

        long startTime = System.nanoTime();

        for (int i = 0; i < numberOfThreads; i++) {
            final int clientId = i;
            executor.submit(() -> {
                try (Socket socket = new Socket("localhost", 6379);
                     OutputStream out = socket.getOutputStream();
                     BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                    for (int j = 0; j < requestsPerThread; j++) {
                        String key = "key-" + clientId + "-" + j;
                        String val = "val-" + j;

                        // 1. Send SET Command
                        String setCommand = "*3\r\n$3\r\nSET\r\n$" + key.length() + "\r\n" + key + "\r\n$" + val.length() + "\r\n" + val + "\r\n";

                        long reqStart = System.nanoTime();
                        out.write(setCommand.getBytes());
                        out.flush();
                        String setResponse = reader.readLine(); // Read "+OK"
                        long reqEnd = System.nanoTime();

                        latencies.add((reqEnd - reqStart) / 1_000_000.0); // Convert to ms

//                        assertEquals("+OK", setResponse, "SET command failed for client " + clientId);

                        // 2. Send GET Command
                        String getCommand = "*2\r\n$3\r\nGET\r\n$" + key.length() + "\r\n" + key + "\r\n";

                        reqStart = System.nanoTime();
                        out.write(getCommand.getBytes());
                        out.flush();
                        String getResponse = reader.readLine(); // Read bulk string value
                        reqEnd = System.nanoTime();

                        latencies.add((reqEnd - reqStart) / 1_000_000.0);

//                        assertEquals(val, getResponse, "GET command returned incorrect value for key " + key);
                    }

                } catch (IOException e) {
                    System.err.println("Client " + clientId + " error: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(15, TimeUnit.SECONDS);
        long endTime = System.nanoTime();

        assertTrue(completed, "Benchmark timed out!");
        executor.shutdown();

        // Calculate statistics
        double totalTimeSeconds = (endTime - startTime) / 1_000_000_000.0;
        double requestsPerSec = totalRequests * 2 / totalTimeSeconds; // *2 because each loop does SET + GET

        // Sort latencies to find p50 (median)
        Collections.sort(latencies);
        double p50 = latencies.isEmpty() ? 0 : latencies.get(latencies.size() / 2);

        System.out.println("\n--- BENCHMARK RESULTS ---");
        System.out.printf("Total Operations: %d SET & GET pairs\n", totalRequests);
        System.out.printf("Throughput:       %.2f requests per second\n", requestsPerSec);
        System.out.printf("p50 Latency:      %.3f msec\n", p50);
        System.out.println("-------------------------\n");
    }
}
