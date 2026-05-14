package com.christoskarakoutis.paymentsystem.controller;

import com.christoskarakoutis.paymentsystem.dto.WalletCreateRequest;
import com.christoskarakoutis.paymentsystem.dto.WalletResponse;
import com.christoskarakoutis.paymentsystem.entity.Wallet;
import com.christoskarakoutis.paymentsystem.entity.WalletType;
import com.christoskarakoutis.paymentsystem.exception.GlobalExceptionHandler;
import com.christoskarakoutis.paymentsystem.exception.ResourceNotFoundException;
import com.christoskarakoutis.paymentsystem.repository.WalletRepository;
import com.christoskarakoutis.paymentsystem.service.WalletService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class WalletControllerTest {

    private MockMvc mockMvc;
    private WalletService walletService;
    private WalletRepository walletRepository;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        walletService = mock(WalletService.class);
        walletRepository = mock(WalletRepository.class);
        objectMapper = new ObjectMapper();

        SecurityContext securityContext = mock(SecurityContext.class);
        Authentication authentication = mock(Authentication.class);
        when(authentication.getDetails()).thenReturn("test-user-id");
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        WalletController controller = new WalletController(walletService, walletRepository);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("POST /api/wallets with no type returns 201 with PEER wallet")
    void createWallet_returns201WithPeerDefault() throws Exception {
        WalletResponse response = new WalletResponse(
                "wallet-1", "test-user-id", BigDecimal.ZERO, "EUR", WalletType.PEER, null, null
        );
        when(walletService.createWallet(eq("test-user-id"), eq(WalletType.PEER))).thenReturn(response);

        mockMvc.perform(post("/api/wallets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("wallet-1"))
                .andExpect(jsonPath("$.walletType").value("PEER"));
    }

    @Test
    @DisplayName("POST /api/wallets with MERCHANT type returns 201")
    void createWallet_returns201WithMerchantTypeViaBody() throws Exception {
        WalletResponse response = new WalletResponse(
                "wallet-2", "test-user-id", BigDecimal.ZERO, "EUR", WalletType.MERCHANT, null, null
        );
        when(walletService.createWallet(eq("test-user-id"), eq(WalletType.MERCHANT))).thenReturn(response);

        String body = objectMapper.writeValueAsString(new WalletCreateRequest(WalletType.MERCHANT));

        mockMvc.perform(post("/api/wallets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("wallet-2"))
                .andExpect(jsonPath("$.walletType").value("MERCHANT"));
    }

    @Test
    @DisplayName("GET /api/wallets/me returns 200 with wallet")
    void getMyWallet_returns200() throws Exception {
        WalletResponse response = new WalletResponse(
                "wallet-1", "test-user-id", new BigDecimal("50.00"), "EUR", WalletType.PEER, null, null
        );
        when(walletService.getWalletByUserId("test-user-id")).thenReturn(response);

        mockMvc.perform(get("/api/wallets/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(50.00));
    }

    @Test
    @DisplayName("DELETE /api/wallets/{id} returns 204 when owner")
    void deleteWallet_returns204WhenOwner() throws Exception {
        Wallet wallet = new Wallet(
                "wallet-1", null, "test-user-id", WalletType.PEER, "EUR", null, null
        );
        when(walletRepository.findById("wallet-1")).thenReturn(Optional.of(wallet));

        mockMvc.perform(delete("/api/wallets/wallet-1"))
                .andExpect(status().isNoContent());

        verify(walletService).deleteWallet("wallet-1");
    }

    @Test
    @DisplayName("DELETE /api/wallets/{id} returns 403 when not owner")
    void deleteWallet_returns403WhenNotOwner() throws Exception {
        Wallet wallet = new Wallet(
                "wallet-1", null, "other-user", WalletType.PEER, "EUR", null, null
        );
        when(walletRepository.findById("wallet-1")).thenReturn(Optional.of(wallet));

        mockMvc.perform(delete("/api/wallets/wallet-1"))
                .andExpect(status().isForbidden());

        verify(walletService, never()).deleteWallet(any());
    }

    @Test
    @DisplayName("DELETE /api/wallets/{id} returns 404 when not found")
    void deleteWallet_returns404WhenNotFound() throws Exception {
        when(walletRepository.findById("missing")).thenReturn(Optional.empty());

        mockMvc.perform(delete("/api/wallets/missing"))
                .andExpect(status().isNotFound());
    }
}
