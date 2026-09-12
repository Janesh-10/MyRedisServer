package org.redis_server.models;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class CommandHandler {
    private ConcurrentHashMap<String, RedisValue> redisStore = new ConcurrentHashMap<>();

    public CommandHandler(ConcurrentHashMap<String, RedisValue> redisStore) {
        this.redisStore = redisStore;
    }

    public RespValue handle(List<RespValue> elements) {
        if (elements == null || elements.isEmpty()) {
            return RespValue.createError("ERR empty command");
        }

        String command = elements.get(0).getStringValue().toUpperCase();

        switch (command) {
            case "PING":
                return RespValue.createSimpleString("PONG");

            case "ECHO":
                if (elements.size() > 1) {
                    String message = elements.get(1).getStringValue();
                    return RespValue.createBulkString(message);
                } else {
                    return RespValue.createError("ERR wrong number of arguments for 'echo' command");
                }

            case "SET":
                return handleSet(elements);

            case "GET":
                return handleGet(elements);

            default:
                return RespValue.createError("ERR unknown command '" + command + "'");
        }
    }

    private RespValue handleSet(List<RespValue> elements) {
        if (elements.size() < 3) {
            return RespValue.createError("ERR wrong number of arguments for 'set' command");
        }

        String key = elements.get(1).getStringValue();
        String value = elements.get(2).getStringValue();
        Long expiryTimeMs = null;

        int i = 3;
        while (i < elements.size()) {
            if (i + 1 >= elements.size()) return RespValue.createError("ERR syntax error");

            String option = elements.get(i).getStringValue().toUpperCase();
            long numericVal;
            try {
                numericVal = Long.parseLong(elements.get(i + 1).getStringValue());
            } catch (NumberFormatException e) {
                return RespValue.createError("ERR value is not an integer or out of range");
            }

            switch (option) {
                case "EX" -> expiryTimeMs = System.currentTimeMillis() + (numericVal * 1000);
                case "PX" -> expiryTimeMs = System.currentTimeMillis() + numericVal;
                case "EXAT" -> expiryTimeMs = numericVal * 1000;
                case "PXAT" -> expiryTimeMs = numericVal;
                default -> { return RespValue.createError("ERR syntax error"); }
            }
            i += 2;
        }

        redisStore.put(key, new RedisValue(value, expiryTimeMs));
        return RespValue.createSimpleString("OK");
    }

    private RespValue handleGet(List<RespValue> elements) {
        if (elements.size() < 2) {
            return RespValue.createError("ERR wrong number of arguments for 'get' command");
        }

        String key = elements.get(1).getStringValue();
        RedisValue redisValue = redisStore.get(key);

        if (redisValue == null) {
            return RespValue.createNullBulkString();
        }
        if (redisValue.isExpired()) {
            redisStore.remove(key);
            return RespValue.createNullBulkString();
        }

        return RespValue.createBulkString(redisValue.getValue());
    }
}
