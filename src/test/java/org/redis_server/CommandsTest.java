package org.redis_server;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redis_server.enums.RespValueType;
import org.redis_server.models.CommandHandler;
import org.redis_server.models.RedisValue;
import org.redis_server.models.RespValue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CommandsTest {
    private CommandHandler commandHandler;

    @BeforeEach
    void setUp() {
        ConcurrentHashMap<String, RedisValue> redisStore = new ConcurrentHashMap<>();
        commandHandler = new CommandHandler(redisStore);
    }

    private RespValue runCommand(String... args) {
        List<RespValue> elements = new ArrayList<>();
        for (String arg : args) {
            elements.add(RespValue.createBulkString(arg));
        }
        return commandHandler.handle(elements);
    }

    @Test
    void testExistsCommand() {
        // Key does not exist initially
        RespValue res1 = runCommand("EXISTS", "mykey");
        assertEquals(0, getIntegerVal(res1));

        // Set key and check again
        runCommand("SET", "mykey", "hello");
        RespValue res2 = runCommand("EXISTS", "mykey");
        assertEquals(1, getIntegerVal(res2));

        // Multiple keys check (one exists, one doesn't)
        RespValue res3 = runCommand("EXISTS", "mykey", "nonexistent");
        assertEquals(1, getIntegerVal(res3));
    }

    @Test
    void testDelCommand() {
        runCommand("SET", "key1", "val1");
        runCommand("SET", "key2", "val2");

        // Delete two keys (one existing, one non-existing)
        RespValue res = runCommand("DEL", "key1", "nonexistent", "key2");
        assertEquals(2, getIntegerVal(res));

        // Verify keys are gone
        RespValue check = runCommand("EXISTS", "key1");
        assertEquals(0, getIntegerVal(check));
    }

    @Test
    void testIncrAndDecrCommands() {
        // INCR on non-existent key should initialize to 0 and become 1
        RespValue res1 = runCommand("INCR", "counter");
        assertEquals(1, getIntegerVal(res1));

        // INCR again should result in 2
        RespValue res2 = runCommand("INCR", "counter");
        assertEquals(2, getIntegerVal(res2));

        // DECR should bring it back to 1
        RespValue res3 = runCommand("DECR", "counter");
        assertEquals(1, getIntegerVal(res3));

        // INCR on a non-integer string should return an error
        runCommand("SET", "strkey", "notanumber");
        RespValue errRes = runCommand("INCR", "strkey");
        assertTrue(isError(errRes));
    }

    @Test
    void testLpushAndRpushCommands() {
        // LPUSH elements
        RespValue res1 = runCommand("LPUSH", "mylist", "world");
        assertEquals(1, getIntegerVal(res1));

        RespValue res2 = runCommand("LPUSH", "mylist", "hello");
        assertEquals(2, getIntegerVal(res2)); // List should be: ["hello", "world"]

        // RPUSH elements
        RespValue res3 = runCommand("RPUSH", "mylist", "tail");
        assertEquals(3, getIntegerVal(res3)); // List should be: ["hello", "world", "tail"]

        // Pushing to a key that already holds a string should cause an error
        runCommand("SET", "stringkey", "iamastring");
        RespValue errRes = runCommand("LPUSH", "stringkey", "val");
        assertTrue(isError(errRes));
    }

    @Test
    void testSaveAndLoadCommand() {
        // 1. Populate some data
        runCommand("SET", "save_key1", "hello_save");
        runCommand("LPUSH", "save_list", "item1");

        // 2. Trigger SAVE command (or call save method directly)
        // If your handler parses "SAVE", you can use runCommand("SAVE")
        // Or call commandHandler's save mechanism if exposed. Let's assume runCommand("SAVE") works:
        RespValue saveRes = runCommand("SAVE");
        assertEquals(org.redis_server.enums.RespValueType.SIMPLE_STRING, saveRes.getType());
        assertEquals("OK", saveRes.getStringValue());

        // 3. Verify file was created
        java.io.File file = new java.io.File("dump.rdb"); // or testFilename if custom path
        assertTrue(file.exists(), "RDB dump file should be created on disk");

        // 4. Test loading into a fresh store
        ConcurrentHashMap<String, RedisValue> newStore = new ConcurrentHashMap<>();
        Main.loadDatabaseFromFile("dump.rdb", newStore);

        // 5. Verify data persisted correctly
        assertTrue(newStore.containsKey("save_key1"));
        assertEquals("hello_save", newStore.get("save_key1").getValue());

        // Clean up test file
        file.delete();
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        java.io.File file = new java.io.File("dump.rdb");
        if (file.exists()) {
            file.delete();
        }
    }

    private long getIntegerVal(RespValue resp) {
        assertEquals(RespValueType.INTEGER, resp.getType());
        return resp.getIntegerValue();
    }

    private boolean isError(RespValue resp) {
        return resp.getStringValue() != null && resp.getStringValue().startsWith("ERR");
    }
}
