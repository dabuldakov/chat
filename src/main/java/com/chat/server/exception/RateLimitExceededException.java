package com.chat.server.exception;

/** Превышен лимит попыток (логин/регистрация) — HTTP 429. */
public class RateLimitExceededException extends RuntimeException {
    public RateLimitExceededException(String message) {
        super(message);
    }
}
