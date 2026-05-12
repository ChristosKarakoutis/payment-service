package com.christoskarakoutis.paymentsystem.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "Wallet details")
public record WalletResponse(
        @Schema(description = "Unique wallet ID", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
        String id,

        @Schema(description = "User ID who owns this wallet", example = "user-abc-123")
        String userId,

        @Schema(description = "Current balance (precision 19, scale 4)", example = "150.0000")
        BigDecimal balance,

        @Schema(description = "ISO-4217 currency code", example = "EUR")
        String currency,

        @Schema(description = "Creation timestamp")
        Instant createdAt,

        @Schema(description = "Last update timestamp")
        Instant updatedAt
) {
}
