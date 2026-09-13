package org.redis_server.models;

import org.redis_server.enums.RedisValueType;

import java.io.Serial;
import java.util.List;

public class RedisValue implements java.io.Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private final RedisValueType type;
    private final String value;
    private final List<String> listValue;
    private final Long expiryTimeMs;

    public RedisValue(String value, Long expiryTimeMs) {
        this.value = value;
        this.expiryTimeMs = expiryTimeMs;
        this.type = RedisValueType.STRING;
        this.listValue = null;
    }

    public RedisValue(List<String> listValue, Long expiryTimeMs) {
        this.listValue = listValue;
        this.expiryTimeMs = expiryTimeMs;
        this.type = RedisValueType.LIST;
        this.value = null;
    }

    public String getValue() {
        return value;
    }

    public RedisValueType getType() {
        return type;
    }

    public List<String> getListValue() {
        return listValue;
    }

    public Long getExpiryTimeMs() {
        return expiryTimeMs;
    }

    public boolean isExpired() {
        if (expiryTimeMs == null) {
            return false;
        }
        return System.currentTimeMillis() > expiryTimeMs;
    }
}
