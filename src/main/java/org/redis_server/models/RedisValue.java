package org.redis_server.models;

public class RedisValue {
    private final String value;
    private final Long expiryTimeMs;

    public RedisValue(String value, Long expiryTimeMs) {
        this.value = value;
        this.expiryTimeMs = expiryTimeMs;
    }

    public String getValue() {
        return value;
    }

    public boolean isExpired() {
        if (expiryTimeMs == null) {
            return false;
        }
        return System.currentTimeMillis() > expiryTimeMs;
    }
}
