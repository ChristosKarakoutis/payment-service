package com.christoskarakoutis.paymentsystem.exception;

import jakarta.persistence.PessimisticLockException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    @DisplayName("DataIntegrityViolationException returns 409 CONFLICT")
    void handleDataIntegrityViolation_returnsConflict() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException("duplicate key value violates unique constraint");
        ResponseEntity<Map<String, String>> response = handler.handleDataIntegrityViolation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(Objects.requireNonNull(response.getBody()).get("error")).contains("duplicate key value");
    }

    @Test
    @DisplayName("PessimisticLockException returns 409 CONFLICT with retry message")
    void handlePessimisticLock_returnsConflict() {
        PessimisticLockException ex = new PessimisticLockException("could not acquire lock");
        ResponseEntity<Map<String, String>> response = handler.handlePessimisticLock(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(Objects.requireNonNull(response.getBody()).get("error")).isEqualTo("Could not acquire lock, please retry");
    }
}
