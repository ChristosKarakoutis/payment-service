package com.christoskarakoutis.paymentsystem.exception;

/**
 * Thrown when an idempotency key is reused for a request that differs from the original one.
 */
public class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException(String message) {
        super(message);
    }
}
