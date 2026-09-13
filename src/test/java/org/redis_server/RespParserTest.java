package org.redis_server;

import org.junit.jupiter.api.Test;
import org.redis_server.enums.RespValueType;
import org.redis_server.models.RespParser;
import org.redis_server.models.RespValue;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class RespParserTest {
    @Test
    void testParseSimpleString() throws IOException {
        RespParser parser = new RespParser("+OK\r\n");
        RespValue result = parser.parse();

        assertEquals(RespValueType.SIMPLE_STRING, result.getType());
        assertEquals("OK", result.getStringValue());
        assertEquals("+OK\r\n", result.serialize());
    }

    @Test
    void testParseError() throws IOException {
        RespParser parser = new RespParser("-Error message\r\n");
        RespValue result = parser.parse();

        assertEquals(RespValueType.ERROR, result.getType());
        assertEquals("Error message", result.getStringValue());
        assertEquals("-Error message\r\n", result.serialize());
    }

    @Test
    void testParseNullBulkString() throws IOException {
        RespParser parser = new RespParser("$-1\r\n");
        RespValue result = parser.parse();

        assertEquals(RespValueType.NULL, result.getType());
        assertEquals("$-1\r\n", result.serialize());
    }

    @Test
    void testParseEmptyBulkString() throws IOException {
        RespParser parser = new RespParser("$0\r\n\r\n");
        RespValue result = parser.parse();

        assertEquals(RespValueType.BULK_STRING, result.getType());
        assertEquals("", result.getStringValue());
        assertEquals("$0\r\n\r\n", result.serialize());
    }

    @Test
    void testParseArrayPing() throws IOException {
        RespParser parser = new RespParser("*1\r\n$4\r\nping\r\n");
        RespValue result = parser.parse();

        assertEquals(RespValueType.ARRAY, result.getType());
        assertEquals(1, result.getArrayValue().size());
        assertEquals("ping", result.getArrayValue().getFirst().getStringValue());
        assertEquals("*1\r\n$4\r\nping\r\n", result.serialize());
    }

    @Test
    void testParseArrayEcho() throws IOException {
        RespParser parser = new RespParser("*2\r\n$4\r\necho\r\n$11\r\nhello world\r\n");
        RespValue result = parser.parse();

        assertEquals(RespValueType.ARRAY, result.getType());
        assertEquals(2, result.getArrayValue().size());
        assertEquals("echo", result.getArrayValue().get(0).getStringValue());
        assertEquals("hello world", result.getArrayValue().get(1).getStringValue());
        assertEquals("*2\r\n$4\r\necho\r\n$11\r\nhello world\r\n", result.serialize());
    }

    @Test
    void testParseArrayGet() throws IOException {
        RespParser parser = new RespParser("*2\r\n$3\r\nget\r\n$3\r\nkey\r\n");
        RespValue result = parser.parse();

        assertEquals(RespValueType.ARRAY, result.getType());
        assertEquals(2, result.getArrayValue().size());
        assertEquals("get", result.getArrayValue().get(0).getStringValue());
        assertEquals("key", result.getArrayValue().get(1).getStringValue());
        assertEquals("*2\r\n$3\r\nget\r\n$3\r\nkey\r\n", result.serialize());
    }

    @Test
    void testInvalidMarkerThrowsException() {
        RespParser parser = new RespParser("X123\r\n");
        assertThrows(IllegalArgumentException.class, parser::parse);
    }
}
