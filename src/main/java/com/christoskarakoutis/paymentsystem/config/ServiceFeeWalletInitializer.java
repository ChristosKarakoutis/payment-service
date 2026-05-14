package com.christoskarakoutis.paymentsystem.config;

import com.christoskarakoutis.paymentsystem.entity.Wallet;
import com.christoskarakoutis.paymentsystem.entity.WalletType;
import com.christoskarakoutis.paymentsystem.repository.WalletRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
@Slf4j
public class ServiceFeeWalletInitializer {

    private static final String SERVICE_FEE_USER_ID = "SERVICE-FEE";

    private final WalletRepository walletRepository;

    @PostConstruct
    void init() {
        if (walletRepository.findByUserId(SERVICE_FEE_USER_ID).isEmpty()) {
            Wallet feeWallet = Wallet.builder()
                    .userId(SERVICE_FEE_USER_ID)
                    .walletType(WalletType.SERVICE_FEE)
                    .balance(BigDecimal.ZERO)
                    .currency("EUR")
                    .build();
            walletRepository.save(feeWallet);
            log.info("Created service fee wallet");
        }
    }
}
