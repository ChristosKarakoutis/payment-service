package com.christoskarakoutis.paymentsystem.config;

import com.christoskarakoutis.paymentsystem.entity.Wallet;
import com.christoskarakoutis.paymentsystem.repository.WalletRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ServiceFeeWalletInitializerTest {

    @Mock
    private WalletRepository walletRepository;

    @InjectMocks
    private ServiceFeeWalletInitializer initializer;

    @Captor
    private ArgumentCaptor<Wallet> walletCaptor;

    @Test
    @DisplayName("init creates fee wallet when none exists")
    void init_createsWalletWhenNotFound() {
        when(walletRepository.findByUserId("SERVICE-FEE")).thenReturn(Optional.empty());

        initializer.init();

        verify(walletRepository).save(walletCaptor.capture());
        Wallet saved = walletCaptor.getValue();
        assertThat(saved.getUserId()).isEqualTo("SERVICE-FEE");
        assertThat(saved.getWalletType()).isEqualTo(com.christoskarakoutis.paymentsystem.entity.WalletType.SERVICE_FEE);
        assertThat(saved.getCurrency()).isEqualTo("EUR");
    }

    @Test
    @DisplayName("init does nothing when fee wallet already exists")
    void init_skipsWhenWalletExists() {
        Wallet existing = Wallet.builder()
                .userId("SERVICE-FEE")
                .walletType(com.christoskarakoutis.paymentsystem.entity.WalletType.SERVICE_FEE)
                .currency("EUR")
                .build();
        when(walletRepository.findByUserId("SERVICE-FEE")).thenReturn(Optional.of(existing));

        initializer.init();

        verify(walletRepository, never()).save(any());
    }
}
