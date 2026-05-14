package com.christoskarakoutis.paymentsystem.dto;

import com.christoskarakoutis.paymentsystem.entity.WalletType;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Request to create a new wallet")
public record WalletCreateRequest(
        @Schema(description = "Wallet type", example = "PEER", defaultValue = "PEER")
        WalletType type
) {
}
