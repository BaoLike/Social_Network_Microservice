package com.call.call_service.exception;

import lombok.Getter;

@Getter
public enum ErrorCode {
    UNAUTHORIZED(401, "Unauthorized"),
    FORBIDDEN(403, "Forbidden"),
    INVALID_REQUEST(400, "Invalid request"),
    CALL_NOT_FOUND(404, "Call not found"),
    BUSY(409, "User busy");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
