package com.christoskarakoutis.paymentsystem.service;

import com.christoskarakoutis.paymentsystem.entity.LedgerEntry;
import com.christoskarakoutis.paymentsystem.entity.LedgerEntryType;
import com.christoskarakoutis.paymentsystem.entity.TransactionStatus;
import com.christoskarakoutis.paymentsystem.entity.Wallet;
import com.christoskarakoutis.paymentsystem.entity.WalletType;
import com.christoskarakoutis.paymentsystem.exception.ResourceNotFoundException;
import com.christoskarakoutis.paymentsystem.repository.LedgerEntryRepository;
import com.christoskarakoutis.paymentsystem.repository.TransactionRepository;
import com.christoskarakoutis.paymentsystem.repository.WalletRepository;
import com.christoskarakoutis.paymentsystem.entity.Transaction;
import com.christoskarakoutis.paymentsystem.dto.TransactionResponse;
import com.christoskarakoutis.paymentsystem.dto.TransactionRequest;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import org.springframework.data.domain.Pageable;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final WalletRepository walletRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    @Value("${payment.p2m.fee-percentage:2.5}")
    private double feePercentage;

    private static final String SERVICE_FEE_USER_ID = "SERVICE-FEE";

    @Transactional
    public TransactionResponse executeAtomicTransfer(TransactionRequest request) {
        Optional<Transaction> existing = transactionRepository.findByIdempotencyKey(request.idempotencyKey());
        if (existing.isPresent()) {
            return toResponse(existing.get());
        }

        String sourceId = request.sourceWalletId();
        String targetId = request.targetWalletId();

        if (sourceId.equals(targetId)) {
            throw new IllegalArgumentException("Source and target wallets must differ");
        }

        Transaction tx = Transaction.builder()
                .idempotencyKey(request.idempotencyKey())
                .amount(request.amount())
                .description(request.description())
                .referenceTransactionId(request.referenceTransactionId())
                .status(TransactionStatus.INITIALIZED)
                .build();
        transactionRepository.save(tx);

        Wallet source;
        Wallet target;
        boolean isP2M;
        BigDecimal fee = BigDecimal.ZERO;
        Wallet feeWallet = null;
        String feeWalletId = null;

        Wallet sourceMeta = walletRepository.findById(sourceId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found: " + sourceId));
        Wallet targetMeta = walletRepository.findById(targetId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found: " + targetId));

        if (sourceMeta.getWalletType() == WalletType.MERCHANT && request.referenceTransactionId() == null) {
            throw new IllegalArgumentException("Merchant wallets cannot initiate transfers.");
        }

        isP2M = sourceMeta.getWalletType() == WalletType.PEER && targetMeta.getWalletType() == WalletType.MERCHANT;

        List<String> lockOrder = new ArrayList<>(List.of(sourceId, targetId));
        if (isP2M) {
            Wallet feeMeta = walletRepository.findByUserId(SERVICE_FEE_USER_ID)
                    .orElseThrow(() -> new ResourceNotFoundException("Service fee wallet not found"));
            feeWalletId = feeMeta.getId();
            lockOrder.add(feeWalletId);
            fee = request.amount().multiply(BigDecimal.valueOf(feePercentage))
                    .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        }
        lockOrder.sort(Comparator.naturalOrder());

        Map<String, Wallet> lockedWallets = new HashMap<>();
        for (String id : lockOrder) {
            Wallet w = walletRepository.findByIdWithLock(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Wallet not found: " + id));
            lockedWallets.put(id, w);
        }

        source = lockedWallets.get(sourceId);
        target = lockedWallets.get(targetId);
        if (isP2M) {
            feeWallet = lockedWallets.get(feeWalletId);
        }

        tx.setSourceWallet(source);
        tx.setTargetWallet(target);

        if (!tx.getStatus().canTransitionTo(TransactionStatus.PENDING)) {
            throw new IllegalStateException("Cannot transition to PENDING from " + tx.getStatus());
        }
        tx.setStatus(TransactionStatus.PENDING);
        transactionRepository.save(tx);

        BigDecimal sourceBalance = getCurrentBalance(sourceId);

        if (sourceBalance.compareTo(request.amount()) < 0) {
            if (!tx.getStatus().canTransitionTo(TransactionStatus.FAILED)) {
                throw new IllegalStateException("Cannot transition to FAILED from " + tx.getStatus());
            }
            tx.setStatus(TransactionStatus.FAILED);
            transactionRepository.save(tx);
            return toResponse(tx);
        }

        BigDecimal sourceNewBalance = sourceBalance.subtract(request.amount());
        BigDecimal targetBalance = getCurrentBalance(targetId);
        BigDecimal targetNewBalance = targetBalance.add(request.amount());

        saveLedgerEntry(sourceId, tx.getId(), LedgerEntryType.DEBIT, request.amount(), sourceNewBalance);
        saveLedgerEntry(targetId, tx.getId(), LedgerEntryType.CREDIT, request.amount(), targetNewBalance);

        if (isP2M) {
            BigDecimal merchantAfterFee = targetNewBalance.subtract(fee);
            BigDecimal feeBalance = getCurrentBalance(feeWallet.getId());
            saveLedgerEntry(targetId, tx.getId(), LedgerEntryType.DEBIT, fee, merchantAfterFee);
            saveLedgerEntry(feeWallet.getId(), tx.getId(), LedgerEntryType.CREDIT, fee, feeBalance.add(fee));
        }

        if (!tx.getStatus().canTransitionTo(TransactionStatus.COMPLETED)) {
            throw new IllegalStateException("Cannot transition to COMPLETED from " + tx.getStatus());
        }
        tx.setStatus(TransactionStatus.COMPLETED);
        transactionRepository.save(tx);

        return toResponse(tx);
    }

    private BigDecimal getCurrentBalance(String accountId) {
        return ledgerEntryRepository.findTopByAccountIdOrderByCreatedAtDesc(accountId)
                .map(LedgerEntry::getRunningBalance)
                .orElse(BigDecimal.ZERO);
    }

    private void saveLedgerEntry(String accountId, String transactionId, LedgerEntryType type, BigDecimal amount, BigDecimal runningBalance) {
        LedgerEntry entry = LedgerEntry.builder()
                .accountId(accountId)
                .transactionId(transactionId)
                .type(type)
                .amount(amount)
                .runningBalance(runningBalance)
                .build();
        ledgerEntryRepository.save(entry);
    }

    @Transactional
    public TransactionResponse refundTransaction(String originalTransactionId) {
        Transaction original = transactionRepository.findById(originalTransactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Original transaction not found: " + originalTransactionId));

        if (original.getStatus() != TransactionStatus.COMPLETED) {
            throw new IllegalStateException("Can only refund COMPLETED transactions");
        }

        if (transactionRepository.existsByReferenceTransactionId(originalTransactionId)) {
            throw new IllegalStateException("Transaction already refunded: " + originalTransactionId);
        }

        Wallet src = original.getSourceWallet();
        Wallet tgt = original.getTargetWallet();
        if (src.getWalletType() == WalletType.PEER && tgt.getWalletType() == WalletType.PEER) {
            throw new IllegalArgumentException("P2P transactions cannot be refunded");
        }

        TransactionRequest refundRequest = new TransactionRequest(
                "REFUND-" + original.getIdempotencyKey(),
                original.getTargetWallet().getId(),
                original.getSourceWallet().getId(),
                original.getAmount(),
                "Refund of transaction " + originalTransactionId,
                originalTransactionId
        );

        return executeAtomicTransfer(refundRequest);
    }

    public TransactionResponse getTransaction(String transactionId) {
        Transaction transaction =  transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + transactionId));

        return toResponse(transaction);
    }

    public List<TransactionResponse> getTransactionsByWalletId(String walletId) {
        return transactionRepository.findAllBySourceWalletIdOrTargetWalletId(
                walletId, walletId, Pageable.unpaged()
        ).stream()
                .map(this::toResponse)
                .toList();
    }



    private TransactionResponse toResponse(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getIdempotencyKey(),
                transaction.getSourceWallet().getId(),
                transaction.getTargetWallet().getId(),
                transaction.getAmount(),
                transaction.getDescription(),
                transaction.getStatus().name(),
                transaction.getReferenceTransactionId(),
                transaction.getCreatedAt()
        );
    }
}
