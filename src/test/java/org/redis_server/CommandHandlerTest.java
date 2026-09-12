package org.redis_server;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redis_server.enums.Type;
import org.redis_server.models.CommandHandler;
import org.redis_server.models.RedisValue;
import org.redis_server.models.RespParser;
import org.redis_server.models.RespValue;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CommandHandlerTest {
    private ConcurrentHashMap<String, RedisValue> store;
    private CommandHandler handler;

    @BeforeEach
    void setUp() {
        store = new ConcurrentHashMap<>();
        handler = new CommandHandler(store);
    }

    private RespValue parseAndHandle(String respInput) throws IOException {
        RespParser parser = new RespParser(respInput);
        RespValue request = parser.parse();
        return handler.handle(request.getArrayValue());
    }

    @Test
    void testSetAndGetWithoutExpiry() throws IOException {
        RespValue setRes = parseAndHandle("*3\r\n$3\r\nSET\r\n$3\r\nfoo\r\n$3\r\nbar\r\n");
        assertEquals(Type.SIMPLE_STRING, setRes.getType());
        assertEquals("OK", setRes.getStringValue());

        RespValue getRes = parseAndHandle("*2\r\n$3\r\nGET\r\n$3\r\nfoo\r\n");
        assertEquals(Type.BULK_STRING, getRes.getType());
        assertEquals("bar", getRes.getStringValue());
    }

    @Test
    void testSetWithPxExpiry() throws IOException, InterruptedException {
        // SET foo bar PX 100 (expires in 100ms)
        String setCmd = "*5\r\n$3\r\nSET\r\n$3\r\nfoo\r\n$3\r\nbar\r\n$2\r\nPX\r\n$3\r\n100\r\n";
        RespValue setRes = parseAndHandle(setCmd);
        assertEquals("OK", setRes.getStringValue());

        // Get immediately (should exist)
        RespValue getRes1 = parseAndHandle("*2\r\n$3\r\nGET\r\n$3\r\nfoo\r\n");
        assertEquals("bar", getRes1.getStringValue());

        // Wait for expiration
        Thread.sleep(150);

        // Get after expiration (should be null)
        RespValue getRes2 = parseAndHandle("*2\r\n$3\r\nGET\r\n$3\r\nfoo\r\n");
        assertEquals(Type.NULL, getRes2.getType());
    }

    @Test
    void testSetWithPastExatExpiry() throws IOException {
        // SET foo bar EXAT 100 (timestamp in 1970, already expired)
        String setCmd = "*5\r\n$3\r\nSET\r\n$3\r\nfoo\r\n$3\r\nbar\r\n$4\r\nEXAT\r\n$3\r\n100\r\n";
        parseAndHandle(setCmd);

        // Get immediately should return null because it's already expired
        RespValue getRes = parseAndHandle("*2\r\n$3\r\nGET\r\n$3\r\nfoo\r\n");
        assertEquals(Type.NULL, getRes.getType());
    }

    @Test
    void testSetInvalidSyntaxError() throws IOException {
        // SET foo bar EX invalid_number
        String setCmd = "*5\r\n$3\r\nSET\r\n$3\r\nfoo\r\n$3\r\nbar\r\n$2\r\nEX\r\n$7\r\ninvalid\r\n";
        RespValue res = parseAndHandle(setCmd);
        assertEquals(Type.ERROR, res.getType());
        assertTrue(res.getStringValue().contains("ERR"));
    }
}
