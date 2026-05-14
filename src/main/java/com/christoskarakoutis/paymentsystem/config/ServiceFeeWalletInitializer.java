package com.christoskarakoutis.paymentsystem.config;

import com.christoskarakoutis.paymentsystem.entity.Wallet;
import com.christoskarakoutis.paymentsystem.entity.WalletType;
import com.christoskarakoutis.paymentsystem.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ServiceFeeWalletInitializer {

    private static final String SERVICE_FEE_USER_ID = "SERVICE-FEE";

    private final WalletRepository walletRepository;

    @EventListener(ApplicationReadyEvent.class)
    void init() {
        if (walletRepository.findByUserId(SERVICE_FEE_USER_ID).isEmpty()) {
            Wallet feeWallet = Wallet.builder()
                    .userId(SERVICE_FEE_USER_ID)
                    .walletType(WalletType.SERVICE_FEE)
                    .currency("EUR")
                    .build();
            walletRepository.save(feeWallet);
            log.info("Created service fee wallet");
        }
    }
}
