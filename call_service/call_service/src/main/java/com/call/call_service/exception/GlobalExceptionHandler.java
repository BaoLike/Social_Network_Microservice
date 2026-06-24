package com.call.call_service.exception;

import com.call.call_service.dto.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiResponse<Void>> handleAppException(AppException ex) {
        return ResponseEntity.status(ex.getErrorCode().getCode())
                .body(ApiResponse.<Void>builder()
                        .code(ex.getErrorCode().getCode())
                        .message(ex.getMessage())
                        .build());
    }
}
