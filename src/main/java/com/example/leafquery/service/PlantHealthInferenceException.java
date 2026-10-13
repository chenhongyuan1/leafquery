package com.example.leafquery.service;

import org.springframework.http.HttpStatus;

/** Stable inference failure information, independent of upstream response bodies and URLs. */
public class PlantHealthInferenceException extends RuntimeException {
    public enum Reason {
        TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "INFERENCE_TIMEOUT", "识别服务响应超时，请稍后重试"),
        UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "INFERENCE_UNAVAILABLE", "识别服务暂时无法连接，请稍后重试"),
        UPSTREAM_ERROR(HttpStatus.BAD_GATEWAY, "INFERENCE_UPSTREAM_ERROR", "识别服务处理失败，请稍后重试"),
        INVALID_RESPONSE(HttpStatus.BAD_GATEWAY, "INFERENCE_INVALID_RESPONSE", "识别服务返回了无效结果，请稍后重试");

        private final HttpStatus httpStatus;
        private final String errorCode;
        private final String message;

        Reason(HttpStatus httpStatus, String errorCode, String message) {
            this.httpStatus = httpStatus;
            this.errorCode = errorCode;
            this.message = message;
        }
    }

    private final Reason reason;

    public PlantHealthInferenceException(Reason reason, Throwable cause) {
        super(reason.message, cause);
        this.reason = reason;
    }

    public Reason getReason() { return reason; }
    public HttpStatus getHttpStatus() { return reason.httpStatus; }
    public String getErrorCode() { return reason.errorCode; }
}
