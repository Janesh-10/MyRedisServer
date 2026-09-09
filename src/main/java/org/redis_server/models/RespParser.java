package org.redis_server.models;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

public class RespParser {
    private final BufferedReader reader;

    public RespParser(String rawInput) {
        this.reader = new BufferedReader(new StringReader(rawInput));
    }

    public RespParser(BufferedReader reader) {
        this.reader = reader;
    }

    private String readLine() throws IOException {
        StringBuilder sb = new StringBuilder();
        int prev = -1;
        int curr;

        while ((curr = reader.read()) != -1) {
            if (prev == '\r' && curr == '\n') {
                // Remove trailing \r from sb
                sb.deleteCharAt(sb.length() - 1);
                return sb.toString();
            }
            sb.append((char) curr);
            prev = curr;
        }
        throw new IOException("Malformed line: missing \\r\\n");
    }

    public RespValue parse() throws IOException {
        int firstByte = reader.read();
        if (firstByte == -1) {
            throw new IOException("End of stream reached unexpectedly");
        }

        char marker = (char) firstByte;
        switch (marker) {
            case '+':
                return RespValue.createSimpleString(readLine());
            case '-':
                return RespValue.createError(readLine());
            case ':':
                return RespValue.createInteger(Integer.parseInt(readLine()));
            case '$':
                int length = Integer.parseInt(readLine());
                if (length == -1) {
                    return RespValue.createNullBulkString();
                }
                char[] buf = new char[length];
                int read = reader.read(buf, 0, length);
                if (read != length) {
                    throw new IOException("Premature end of stream while reading bulk string");
                }
                // Consume trailing \r\n
                reader.read(); // \r
                reader.read(); // \n
                return RespValue.createBulkString(new String(buf));
            case '*':
                int count = Integer.parseInt(readLine());
                if (count == -1) {
                    // Null array representation if needed
                    return RespValue.createArray(null);
                }
                List<RespValue> elements = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                    elements.add(parse());
                }
                return RespValue.createArray(elements);
            default:
                throw new IllegalArgumentException("Unknown RESP data type marker: " + marker);
        }
    }
}
