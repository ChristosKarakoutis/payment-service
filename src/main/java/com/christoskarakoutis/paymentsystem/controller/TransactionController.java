package com.christoskarakoutis.paymentsystem.controller;

import com.christoskarakoutis.paymentsystem.dto.TransactionRequest;
import com.christoskarakoutis.paymentsystem.dto.TransactionResponse;
import com.christoskarakoutis.paymentsystem.entity.Wallet;
import com.christoskarakoutis.paymentsystem.exception.ResourceNotFoundException;
import com.christoskarakoutis.paymentsystem.repository.WalletRepository;
import com.christoskarakoutis.paymentsystem.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
@Tag(name = "Transactions", description = "Atomic fund transfers between wallets with idempotency and refund support")
public class TransactionController {

    private final TransactionService transactionService;
    private final WalletRepository walletRepository;

    @PostMapping
    @Operation(summary = "Execute a transfer", description = """
            Atomically transfers funds from source to target wallet.
            Idempotent — safe to retry with the same idempotency key.
            Source wallet must belong to the authenticated user.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Transfer completed or idempotent response returned"),
            @ApiResponse(responseCode = "400", description = "Validation error or same-wallet transfer"),
            @ApiResponse(responseCode = "403", description = "Source wallet belongs to another user"),
            @ApiResponse(responseCode = "404", description = "Source or target wallet not found")
    })
    public ResponseEntity<TransactionResponse> executeTransfer(@Valid @RequestBody TransactionRequest request) {
        String userId = (String) SecurityContextHolder.getContext().getAuthentication().getDetails();
        Wallet sourceWallet = walletRepository.findById(request.sourceWalletId())
                .orElseThrow(() -> new ResourceNotFoundException("Source wallet not found: " + request.sourceWalletId()));
        if (!sourceWallet.getUserId().equals(userId)) {
            throw new AccessDeniedException("Source wallet does not belong to the authenticated user");
        }
        TransactionRequest sanitized = new TransactionRequest(
                request.idempotencyKey(),
                request.sourceWalletId(),
                request.targetWalletId(),
                request.amount(),
                request.description(),
                null
        );
        TransactionResponse response = transactionService.executeAtomicTransfer(sanitized);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{id}/refund")
    @Operation(summary = "Refund a completed transaction", description = "Creates a reverse transfer (source and target swapped) for a completed transaction. You must own one of the wallets involved.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Refund completed"),
            @ApiResponse(responseCode = "400", description = "Transaction not COMPLETED or already refunded"),
            @ApiResponse(responseCode = "403", description = "You are not involved in this transaction"),
            @ApiResponse(responseCode = "404", description = "Original transaction not found")
    })
    public ResponseEntity<TransactionResponse> refundTransaction(@PathVariable String id) {
        String userId = (String) SecurityContextHolder.getContext().getAuthentication().getDetails();
        TransactionResponse original = transactionService.getTransaction(id);
        Wallet srcWallet = walletRepository.findById(original.sourceWalletId())
                .orElseThrow(() -> new ResourceNotFoundException("Source wallet not found: " + original.sourceWalletId()));
        Wallet tgtWallet = walletRepository.findById(original.targetWalletId())
                .orElseThrow(() -> new ResourceNotFoundException("Target wallet not found: " + original.targetWalletId()));
        if (!srcWallet.getUserId().equals(userId) && !tgtWallet.getUserId().equals(userId)) {
            throw new AccessDeniedException("You are not involved in this transaction");
        }
        TransactionResponse response = transactionService.refundTransaction(id);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a transaction by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Transaction found"),
            @ApiResponse(responseCode = "404", description = "Transaction not found")
    })
    public ResponseEntity<TransactionResponse> getTransaction(@PathVariable String id) {
        TransactionResponse response = transactionService.getTransaction(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @Operation(summary = "List wallet transactions", description = "Returns all transactions (as source or target) for a given wallet. Wallet must belong to the authenticated user.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of transactions"),
            @ApiResponse(responseCode = "403", description = "Wallet belongs to another user"),
            @ApiResponse(responseCode = "404", description = "Wallet not found")
    })
    public ResponseEntity<List<TransactionResponse>> getTransactions(@RequestParam String walletId) {
        String userId = (String) SecurityContextHolder.getContext().getAuthentication().getDetails();
        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found: " + walletId));
        if (!wallet.getUserId().equals(userId)) {
            throw new AccessDeniedException("Wallet does not belong to the authenticated user");
        }
        List<TransactionResponse> responses = transactionService.getTransactionsByWalletId(walletId);
        return ResponseEntity.ok(responses);
    }
}
