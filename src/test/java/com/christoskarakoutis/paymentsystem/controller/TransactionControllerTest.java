package com.christoskarakoutis.paymentsystem.controller;

import com.christoskarakoutis.paymentsystem.dto.TransactionRequest;
import com.christoskarakoutis.paymentsystem.dto.TransactionResponse;
import com.christoskarakoutis.paymentsystem.entity.Wallet;
import com.christoskarakoutis.paymentsystem.exception.GlobalExceptionHandler;
import com.christoskarakoutis.paymentsystem.exception.ResourceNotFoundException;
import com.christoskarakoutis.paymentsystem.repository.WalletRepository;
import com.christoskarakoutis.paymentsystem.service.TransactionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TransactionControllerTest {

    private MockMvc mockMvc;
    private TransactionService transactionService;
    private WalletRepository walletRepository;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        transactionService = mock(TransactionService.class);
        walletRepository = mock(WalletRepository.class);
        objectMapper = new ObjectMapper();

        SecurityContext securityContext = mock(SecurityContext.class);
        Authentication authentication = mock(Authentication.class);
        when(authentication.getDetails()).thenReturn("test-user-id");
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        TransactionController controller = new TransactionController(transactionService, walletRepository);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private Wallet userWallet() {
        return new Wallet("wallet-1", "test-user-id", new BigDecimal("100.00"), "EUR", null, null);
    }

    private TransactionRequest validRequest() {
        return new TransactionRequest(
                "idem-1", "wallet-1", "wallet-2",
                new BigDecimal("50.00"), "test payment", null
        );
    }

    private TransactionResponse completedResponse() {
        return new TransactionResponse(
                "tx-1", "idem-1", "wallet-1", "wallet-2",
                new BigDecimal("50.00"), "test payment",
                "COMPLETED", null, Instant.now()
        );
    }

    // ── POST /api/transactions ─────────────────────────────────────────

    @Test
    @DisplayName("POST /api/transactions returns 201")
    void executeTransfer_returns201() throws Exception {
        when(walletRepository.findById("wallet-1")).thenReturn(Optional.of(userWallet()));
        when(transactionService.executeAtomicTransfer(any(TransactionRequest.class)))
                .thenReturn(completedResponse());

        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("tx-1"))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    @DisplayName("POST /api/transactions returns 403 when source wallet not owned")
    void executeTransfer_returns403WhenNotOwner() throws Exception {
        Wallet otherWallet = new Wallet("wallet-1", "other-user", BigDecimal.ZERO, "EUR", null, null);
        when(walletRepository.findById("wallet-1")).thenReturn(Optional.of(otherWallet));

        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/transactions returns 404 when source wallet not found")
    void executeTransfer_returns404WhenSourceNotFound() throws Exception {
        when(walletRepository.findById("wallet-1")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /api/transactions returns 400 on validation error")
    void executeTransfer_returns400OnValidationError() throws Exception {
        var invalidRequest = new TransactionRequest(
                "", "", "",
                null, null, null
        );

        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    // ── POST /api/transactions/{id}/refund ─────────────────────────────

    @Test
    @DisplayName("POST /api/transactions/{id}/refund returns 201")
    void refundTransaction_returns201() throws Exception {
        when(transactionService.refundTransaction("tx-1")).thenReturn(completedResponse());

        mockMvc.perform(post("/api/transactions/tx-1/refund"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("tx-1"));
    }

    // ── GET /api/transactions/{id} ─────────────────────────────────────

    @Test
    @DisplayName("GET /api/transactions/{id} returns 200")
    void getTransaction_returns200() throws Exception {
        when(transactionService.getTransaction("tx-1")).thenReturn(completedResponse());

        mockMvc.perform(get("/api/transactions/tx-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("tx-1"));
    }

    // ── GET /api/transactions?walletId= ────────────────────────────────

    @Test
    @DisplayName("GET /api/transactions returns 200 with list")
    void getTransactions_returns200() throws Exception {
        when(walletRepository.findById("wallet-1")).thenReturn(Optional.of(userWallet()));
        when(transactionService.getTransactionsByWalletId("wallet-1"))
                .thenReturn(List.of(completedResponse()));

        mockMvc.perform(get("/api/transactions").param("walletId", "wallet-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("tx-1"));
    }

    @Test
    @DisplayName("GET /api/transactions returns 403 when wallet not owned")
    void getTransactions_returns403WhenNotOwner() throws Exception {
        Wallet otherWallet = new Wallet("wallet-1", "other-user", BigDecimal.ZERO, "EUR", null, null);
        when(walletRepository.findById("wallet-1")).thenReturn(Optional.of(otherWallet));

        mockMvc.perform(get("/api/transactions").param("walletId", "wallet-1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/transactions returns 404 when wallet not found")
    void getTransactions_returns404WhenWalletNotFound() throws Exception {
        when(walletRepository.findById("missing")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/transactions").param("walletId", "missing"))
                .andExpect(status().isNotFound());
    }
}
