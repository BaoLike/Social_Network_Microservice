package com.ig.PostService.exception;

public class PostViolationException extends RuntimeException {
    public PostViolationException(String reason) {
        super(reason);
    }
}
