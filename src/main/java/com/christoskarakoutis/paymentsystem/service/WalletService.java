package com.christoskarakoutis.paymentsystem.service;

import com.christoskarakoutis.paymentsystem.entity.LedgerEntry;
import com.christoskarakoutis.paymentsystem.entity.Wallet;
import com.christoskarakoutis.paymentsystem.entity.WalletType;
import com.christoskarakoutis.paymentsystem.exception.ResourceNotFoundException;
import com.christoskarakoutis.paymentsystem.repository.LedgerEntryRepository;
import com.christoskarakoutis.paymentsystem.repository.WalletRepository;
import com.christoskarakoutis.paymentsystem.dto.WalletResponse;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class WalletService {
    private final WalletRepository walletRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    public WalletResponse createWallet(String userId, WalletType walletType) {
        if (walletType == null) {
            walletType = WalletType.PEER;
        }
        Wallet wallet = Wallet.builder()
                .userId(userId)
                .walletType(walletType)
                .currency("EUR")
                .build();

        wallet = walletRepository.save(wallet);

        return new WalletResponse(
                wallet.getId(),
                wallet.getUserId(),
                BigDecimal.ZERO,
                wallet.getCurrency(),
                wallet.getWalletType(),
                wallet.getCreatedAt(),
                wallet.getUpdatedAt()
        );
    }

    public WalletResponse getWalletByUserId(String userId) {
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found for user: " + userId));
        BigDecimal balance = getCurrentBalance(wallet.getId());
        return new WalletResponse(
                wallet.getId(),
                wallet.getUserId(),
                balance,
                wallet.getCurrency(),
                wallet.getWalletType(),
                wallet.getCreatedAt(),
                wallet.getUpdatedAt()
        );
    }

    public void deleteWallet(String walletId) {
        Wallet wallet = walletRepository.findByIdWithLock(walletId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found: " + walletId));

        BigDecimal balance = getCurrentBalance(walletId);
        if (balance.compareTo(BigDecimal.ZERO) != 0) {
            throw new IllegalStateException("Cannot delete wallet with non-zero balance: " + balance);
        }

        walletRepository.delete(wallet);
    }

    private BigDecimal getCurrentBalance(String accountId) {
        return ledgerEntryRepository.findTopByAccountIdOrderByCreatedAtDesc(accountId)
                .map(LedgerEntry::getRunningBalance)
                .orElse(BigDecimal.ZERO);
    }
}
