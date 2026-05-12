package com.christoskarakoutis.paymentsystem.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "Transaction details")
public record TransactionResponse(

        @Schema(description = "Unique transaction ID")
        String id,

        @Schema(description = "Idempotency key used for this transaction")
        String idempotencyKey,

        @Schema(description = "Source wallet ID")
        String sourceWalletId,

        @Schema(description = "Target wallet ID")
        String targetWalletId,

        @Schema(description = "Transferred amount")
        BigDecimal amount,

        @Schema(description = "Optional description")
        String description,

        @Schema(description = "Transaction status (INITIALIZED, PENDING, COMPLETED, FAILED)")
        String status,

        @Schema(description = "Reference to original transaction (set for refunds)")
        String referenceTransactionId,

        @Schema(description = "Creation timestamp")
        Instant createdAt
) {
}
