package com.christoskarakoutis.paymentsystem.service;

import com.christoskarakoutis.paymentsystem.dto.WalletResponse;
import com.christoskarakoutis.paymentsystem.entity.LedgerEntry;
import com.christoskarakoutis.paymentsystem.entity.Wallet;
import com.christoskarakoutis.paymentsystem.entity.WalletType;
import com.christoskarakoutis.paymentsystem.exception.ResourceNotFoundException;
import com.christoskarakoutis.paymentsystem.repository.LedgerEntryRepository;
import com.christoskarakoutis.paymentsystem.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock
    private WalletRepository walletRepository;
    @Mock
    private LedgerEntryRepository ledgerEntryRepository;

    private WalletService walletService;

    @BeforeEach
    void setUp() {
        walletService = new WalletService(walletRepository, ledgerEntryRepository);
    }

    @Test
    @DisplayName("createWallet creates PEER by default")
    void createWallet_createsPeerByDefault() {
        Wallet savedWallet = new Wallet(
                "wallet-1", null, "user-1", WalletType.PEER, "EUR", null, null
        );
        when(walletRepository.save(any(Wallet.class))).thenReturn(savedWallet);

        WalletResponse response = walletService.createWallet("user-1", null);

        assertThat(response.id()).isEqualTo("wallet-1");
        assertThat(response.walletType()).isEqualTo(WalletType.PEER);

        ArgumentCaptor<Wallet> captor = ArgumentCaptor.forClass(Wallet.class);
        verify(walletRepository).save(captor.capture());
        assertThat(captor.getValue().getWalletType()).isEqualTo(WalletType.PEER);
    }

    @Test
    @DisplayName("createWallet creates MERCHANT when specified")
    void createWallet_createsMerchantWhenSpecified() {
        Wallet savedWallet = new Wallet(
                "wallet-2", null, "user-2", WalletType.MERCHANT, "EUR", null, null
        );
        when(walletRepository.save(any(Wallet.class))).thenReturn(savedWallet);

        WalletResponse response = walletService.createWallet("user-2", WalletType.MERCHANT);

        assertThat(response.id()).isEqualTo("wallet-2");
        assertThat(response.walletType()).isEqualTo(WalletType.MERCHANT);

        ArgumentCaptor<Wallet> captor = ArgumentCaptor.forClass(Wallet.class);
        verify(walletRepository).save(captor.capture());
        assertThat(captor.getValue().getWalletType()).isEqualTo(WalletType.MERCHANT);
    }

    @Test
    @DisplayName("getWalletByUserId returns wallet when found")
    void getWalletByUserId_returnsWallet() {
        Wallet wallet = new Wallet(
                "wallet-1", null, "user-1", WalletType.PEER, "EUR", null, null
        );
        LedgerEntry lastEntry = LedgerEntry.builder()
                .accountId("wallet-1").runningBalance(new BigDecimal("100.00")).build();

        when(walletRepository.findByUserId("user-1")).thenReturn(Optional.of(wallet));
        when(ledgerEntryRepository.findTopByAccountIdOrderByCreatedAtDesc("wallet-1"))
                .thenReturn(Optional.of(lastEntry));

        WalletResponse response = walletService.getWalletByUserId("user-1");

        assertThat(response.id()).isEqualTo("wallet-1");
        assertThat(response.balance()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(response.walletType()).isEqualTo(WalletType.PEER);
    }

    @Test
    @DisplayName("getWalletByUserId throws when not found")
    void getWalletByUserId_throwsWhenNotFound() {
        when(walletRepository.findByUserId("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> walletService.getWalletByUserId("unknown"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("not found");
    }

    @Test
    @DisplayName("deleteWallet deletes wallet when found and balance is zero")
    void deleteWallet_deletesWhenFound() {
        Wallet wallet = new Wallet(
                "wallet-1", null, "user-1", WalletType.PEER, "EUR", null, null
        );
        when(walletRepository.findByIdWithLock("wallet-1")).thenReturn(Optional.of(wallet));
        when(ledgerEntryRepository.findTopByAccountIdOrderByCreatedAtDesc("wallet-1"))
                .thenReturn(Optional.empty());

        walletService.deleteWallet("wallet-1");

        verify(ledgerEntryRepository).deleteByAccountId("wallet-1");
        verify(walletRepository).delete(wallet);
    }

    @Test
    @DisplayName("deleteWallet throws when not found")
    void deleteWallet_throwsWhenNotFound() {
        when(walletRepository.findByIdWithLock("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> walletService.deleteWallet("missing"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("not found");
    }

    @Test
    @DisplayName("deleteWallet throws when balance is non-zero")
    void deleteWallet_throwsWhenNonZeroBalance() {
        Wallet wallet = new Wallet(
                "wallet-1", null, "user-1", WalletType.PEER, "EUR", null, null
        );
        LedgerEntry lastEntry = LedgerEntry.builder()
                .accountId("wallet-1").runningBalance(new BigDecimal("50.00")).build();

        when(walletRepository.findByIdWithLock("wallet-1")).thenReturn(Optional.of(wallet));
        when(ledgerEntryRepository.findTopByAccountIdOrderByCreatedAtDesc("wallet-1"))
                .thenReturn(Optional.of(lastEntry));

        assertThatThrownBy(() -> walletService.deleteWallet("wallet-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("non-zero balance");

        verify(walletRepository, never()).delete(any());
    }
}
