package com.christoskarakoutis.paymentsystem.service;

import com.christoskarakoutis.paymentsystem.entity.Wallet;
import com.christoskarakoutis.paymentsystem.entity.WalletType;
import com.christoskarakoutis.paymentsystem.exception.ResourceNotFoundException;
import com.christoskarakoutis.paymentsystem.repository.WalletRepository;
import com.christoskarakoutis.paymentsystem.dto.WalletResponse;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class WalletService {
        private final WalletRepository walletRepository;

        public WalletResponse createWallet(String userId, WalletType walletType) {
            if (walletType == null) {
                walletType = WalletType.PEER;
            }
            Wallet wallet = new Wallet(
                    null,
                    userId,
                    walletType,
                    BigDecimal.ZERO,
                    "EUR",
                    null,
                    null
            );

            wallet = walletRepository.save(wallet);

            return new WalletResponse(
                    wallet.getId(),
                    wallet.getUserId(),
                    wallet.getBalance(),
                    wallet.getCurrency(),
                    wallet.getWalletType(),
                    wallet.getCreatedAt(),
                    wallet.getUpdatedAt()
            );

        }

    public WalletResponse getWalletByUserId(String userId) {
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found for user: " + userId));
        return new WalletResponse(
                wallet.getId(),
                wallet.getUserId(),
                wallet.getBalance(),
                wallet.getCurrency(),
                wallet.getWalletType(),
                wallet.getCreatedAt(),
                wallet.getUpdatedAt()
        );
    }

    public void deleteWallet(String walletId) {
        Wallet wallet = walletRepository.findByIdWithLock(walletId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found: " + walletId));

        walletRepository.delete(wallet);
    }
}
