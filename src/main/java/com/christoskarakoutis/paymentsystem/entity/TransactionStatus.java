package com.christoskarakoutis.paymentsystem.entity;

public enum TransactionStatus {
    INITIALIZED,
    PENDING,
    COMPLETED,
    FAILED;


    public boolean canTransitionTo(TransactionStatus nextState) {
        return switch (this) {
            case INITIALIZED -> nextState == PENDING;
            case PENDING -> nextState == COMPLETED || nextState == FAILED;
            case FAILED, COMPLETED -> false; // Terminal state
        };
    }
}
