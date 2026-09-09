package org.redis_server.models;

import org.redis_server.enums.Type;

import java.util.List;
import java.util.Objects;

public class RespValue {
    private final Type type;
    private final String stringValue;
    private final Integer integerValue;
    private final List<RespValue> arrayValue;

    private RespValue(Type type, String stringValue, Integer integerValue, List<RespValue> arrayValue) {
        this.type = type;
        this.stringValue = stringValue;
        this.integerValue = integerValue;
        this.arrayValue = arrayValue;
    }

    public Type getType() {
        return type;
    }

    public String getStringValue() {
        return stringValue;
    }

    public List<RespValue> getArrayValue() {
        return arrayValue;
    }

    public static RespValue createSimpleString(String val) {
        return new RespValue(Type.SIMPLE_STRING, val, null, null);
    }

    public static RespValue createError(String val) {
        return new RespValue(Type.ERROR, val, null, null);
    }

    public static RespValue createInteger(int val) {
        return new RespValue(Type.INTEGER, null, val, null);
    }

    public static RespValue createBulkString(String val) {
        return new RespValue(Type.BULK_STRING, val, null, null);
    }

    public static RespValue createNullBulkString() {
        return new RespValue(Type.NULL, null, null, null);
    }

    public static RespValue createArray(List<RespValue> val) {
        return new RespValue(Type.ARRAY, null, null, val);
    }

    // Serialization logic to convert Java objects back to RESP wire format
    public String serialize() {
        switch (type) {
            case SIMPLE_STRING:
                return "+" + stringValue + "\r\n";
            case ERROR:
                return "-" + stringValue + "\r\n";
            case INTEGER:
                return ":" + integerValue + "\r\n";
            case BULK_STRING:
                if (stringValue == null) return "$-1\r\n";
                return "$" + stringValue.getBytes().length + "\r\n" + stringValue + "\r\n";
            case NULL:
                return "$-1\r\n";
            case ARRAY:
                if (arrayValue == null) return "*-1\r\n";
                StringBuilder sb = new StringBuilder();
                sb.append("*").append(arrayValue.size()).append("\r\n");
                for (RespValue item : arrayValue) {
                    sb.append(item.serialize());
                }
                return sb.toString();
            default:
                throw new IllegalStateException("Unknown RESP type");
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RespValue respValue = (RespValue) o;
        return type == respValue.type &&
                Objects.equals(stringValue, respValue.stringValue) &&
                Objects.equals(integerValue, respValue.integerValue) &&
                Objects.equals(arrayValue, respValue.arrayValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, stringValue, integerValue, arrayValue);
    }
}
