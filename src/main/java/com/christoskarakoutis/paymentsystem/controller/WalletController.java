package com.christoskarakoutis.paymentsystem.controller;

import com.christoskarakoutis.paymentsystem.dto.WalletResponse;
import com.christoskarakoutis.paymentsystem.entity.Wallet;
import com.christoskarakoutis.paymentsystem.exception.ResourceNotFoundException;
import com.christoskarakoutis.paymentsystem.repository.WalletRepository;
import com.christoskarakoutis.paymentsystem.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/wallets")
@RequiredArgsConstructor
@Tag(name = "Wallets", description = "Wallet management for the authenticated user")
public class WalletController {

    private final WalletService walletService;
    private final WalletRepository walletRepository;

    @PostMapping
    @Operation(summary = "Create a wallet", description = "Creates a new wallet with zero balance for the authenticated user")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Wallet created"),
            @ApiResponse(responseCode = "401", description = "Not authenticated — missing or invalid JWT cookie")
    })
    public ResponseEntity<WalletResponse> createWallet() {
        String userId = (String) SecurityContextHolder.getContext().getAuthentication().getDetails();
        WalletResponse response = walletService.createWallet(userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/me")
    @Operation(summary = "Get own wallet", description = "Returns the wallet belonging to the authenticated user")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Wallet found"),
            @ApiResponse(responseCode = "404", description = "No wallet exists for this user")
    })
    public ResponseEntity<WalletResponse> getMyWallet() {
        String userId = (String) SecurityContextHolder.getContext().getAuthentication().getDetails();
        WalletResponse response = walletService.getWalletByUserId(userId);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{walletId}")
    @Operation(summary = "Delete a wallet", description = "Deletes a wallet if it belongs to the authenticated user and balance is zero")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Wallet deleted"),
            @ApiResponse(responseCode = "403", description = "Wallet belongs to another user"),
            @ApiResponse(responseCode = "404", description = "Wallet not found")
    })
    public ResponseEntity<Void> deleteWallet(@PathVariable String walletId) {
        String userId = (String) SecurityContextHolder.getContext().getAuthentication().getDetails();
        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found: " + walletId));
        if (!wallet.getUserId().equals(userId)) {
            throw new AccessDeniedException("Wallet does not belong to the authenticated user");
        }
        walletService.deleteWallet(walletId);
        return ResponseEntity.noContent().build();
    }
}
