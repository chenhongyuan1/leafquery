package com.example.leafquery.dto;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import java.util.LinkedHashMap;
import java.util.Map;

/** Stable business envelope; legacy top-level extensions are preserved during migration. */
public class ApiResponse<T> {
    private int code = 200;
    private String message = "success";
    private T data;
    private final Map<String, Object> legacyFields = new LinkedHashMap<>();

    public int getCode() { return code; }
    public void setCode(int code) { this.code = code; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public T getData() { return data; }
    public void setData(T data) { this.data = data; }

    public void setLegacyField(String name, Object value) {
        if (name.equals("code") || name.equals("message") || name.equals("data")) {
            throw new IllegalArgumentException("Reserved response field");
        }
        legacyFields.put(name, value);
    }

    @JsonAnyGetter
    public Map<String, Object> legacyFields() { return legacyFields; }

    public static <T> ApiResponse<T> error(int code, String message) {
        ApiResponse<T> result = new ApiResponse<>();
        result.setCode(code);
        result.setMessage(message);
        return result;
    }

    public static ApiResponse<Object> fromLegacy(Map<?, ?> body) {
        ApiResponse<Object> result = new ApiResponse<>();
        result.setCode(((Number) body.get("code")).intValue());
        Object message = body.get("message");
        result.setMessage(result.getCode() >= 500 ? "服务暂时不可用，请稍后重试。"
                : message instanceof String text ? text : "success");
        result.setData(body.get("data"));
        body.forEach((name, value) -> {
            if (name instanceof String key && !key.equals("code") && !key.equals("message") && !key.equals("data")) {
                result.setLegacyField(key, value);
            }
        });
        return result;
    }
}
