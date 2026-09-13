package org.redis_server.models;

import org.redis_server.enums.RedisValueType;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class CommandHandler {
    private final ConcurrentHashMap<String, RedisValue> redisStore;

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

            case "EXISTS":
                return handleExists(elements);

            case "DEL":
                return handleDel(elements);

            case "INCR":
                return handleIncr(elements, true);

            case "DECR":
                return handleIncr(elements, false);

            case "LPUSH":
                return handlePush(elements, true);

            case "RPUSH":
                return handlePush(elements, false);

            case "SAVE":
                return handleSave();

            default:
                return RespValue.createError("ERR unknown command '" + command + "'");
        }
    }

    private RespValue handleSave() {
        try {
            saveDatabaseToFile("dump.rdb");
            return RespValue.createSimpleString("OK");
        } catch (Exception e) {
            return RespValue.createError("ERR Failed to save database: " + e.getMessage());
        }
    }

    public void saveDatabaseToFile(String filename) throws IOException {
        // Clean up expired keys before saving
        redisStore.entrySet().removeIf(entry -> entry.getValue().isExpired());

        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(filename))) {
            oos.writeObject(new ConcurrentHashMap<>(redisStore));
        }
    }

    private RespValue handlePush(List<RespValue> elements, boolean isLeft) {
        if (elements.size() < 3) {
            return RespValue.createError("ERR wrong number of arguments for '" + (isLeft ? "lpush" : "rpush") + "' command");
        }

        String key = elements.get(1).getStringValue();

        List<String> valuesToPush = new java.util.ArrayList<>();
        for (int i = 2; i < elements.size(); i++) {
            valuesToPush.add(elements.get(i).getStringValue());
        }

        final int[] newSize = new int[1];

        try {
            redisStore.compute(key, (k, v) -> {
                java.util.LinkedList<String> currentList;
                Long expiry = null;

                if (v != null && !v.isExpired()) {
                    expiry = v.getExpiryTimeMs();
                    if (v.getType() == RedisValueType.LIST) {
                        currentList = (java.util.LinkedList<String>) v.getListValue();
                    } else {
                        throw new IllegalArgumentException("ERR Operation against a key holding the wrong kind of value");
                    }
                } else {
                    currentList = new java.util.LinkedList<>();
                }

                for (String val : valuesToPush) {
                    if (isLeft) {
                        currentList.addFirst(val);
                    } else {
                        currentList.addLast(val);
                    }
                }

                newSize[0] = currentList.size();
                return new RedisValue(currentList, expiry);
            });

            return RespValue.createInteger(newSize[0]);
        } catch (IllegalArgumentException e) {
            return RespValue.createError(e.getMessage());
        }
    }

    private RespValue handleIncr(List<RespValue> elements, boolean isIncrement) {
        if (elements.size() < 2) {
            return RespValue.createError("ERR wrong number of arguments for '" + (isIncrement ? "incr" : "decr") + "' command");
        }

        String key = elements.get(1).getStringValue();

        try {
            final int[] finalVal = new int[1];

            redisStore.compute(key, (k, v) -> {
                int currentVal = 0;
                Long expiry = null;

                if (v != null) {
                    if (!v.isExpired()) {
                        expiry = v.getExpiryTimeMs();
                        try {
                            currentVal = Integer.parseInt(v.getValue());
                        } catch (NumberFormatException e) {
                            throw new IllegalArgumentException("ERR value is not an integer or out of range");
                        }
                    }
                }

                finalVal[0] = isIncrement ? currentVal + 1 : currentVal - 1;
                return new RedisValue(String.valueOf(finalVal[0]), expiry);
            });

            return RespValue.createInteger(finalVal[0]);
        } catch (IllegalArgumentException e) {
            return RespValue.createError(e.getMessage());
        }
    }

    private RespValue handleDel(List<RespValue> elements) {
        if (elements.size() < 2) {
            return RespValue.createError("ERR wrong number of arguments for 'del' command");
        }

        int count = 0;
        for (int i = 1; i < elements.size(); i++) {
            String key = elements.get(i).getStringValue();

            RedisValue redisValue = redisStore.get(key);
            if (redisValue != null && redisValue.isExpired()) {
                redisStore.remove(key);
                continue;
            }

            if (redisStore.remove(key) != null) {
                count++;
            }
        }
        return RespValue.createInteger(count);
    }

    private RespValue handleExists(List<RespValue> elements) {
        if (elements.size() < 2) {
            return RespValue.createError("ERR wrong number of arguments for 'exists' command");
        }

        int count = 0;
        for (int i = 1; i < elements.size(); i++) {
            String key = elements.get(i).getStringValue();
            RedisValue redisValue = redisStore.get(key);

            if (redisValue != null) {
                if (redisValue.isExpired()) {
                    redisStore.remove(key);
                } else {
                    count++;
                }
            }
        }
        return RespValue.createInteger(count);
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
