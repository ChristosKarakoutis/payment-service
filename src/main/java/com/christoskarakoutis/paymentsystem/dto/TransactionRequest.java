package com.christoskarakoutis.paymentsystem.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

@Schema(description = "Request to execute an atomic fund transfer")
public record TransactionRequest(

        @Schema(description = "Unique idempotency key for safe retries", example = "tx-001")
        @NotBlank(message = "Idempotency key is required.")
        String idempotencyKey,

        @Schema(description = "Source wallet ID", example = "wallet-src-uuid")
        @NotBlank(message = "Source wallet ID is required.")
        String sourceWalletId,

        @Schema(description = "Target wallet ID", example = "wallet-tgt-uuid")
        @NotBlank(message = "Target wallet ID is required.")
        String targetWalletId,

        @Schema(description = "Amount to transfer (must be positive)", example = "99.99")
        @NotNull(message = "Amount cannot be null.")
        @Positive(message = "Amount must be greater than zero.")
        BigDecimal amount,

        @Schema(description = "Optional description / memo", example = "Invoice payment #1234")
        String description,

        @Schema(description = "Reference to an original transaction (used for refunds)", example = "orig-tx-uuid")
        String referenceTransactionId
) {
}
