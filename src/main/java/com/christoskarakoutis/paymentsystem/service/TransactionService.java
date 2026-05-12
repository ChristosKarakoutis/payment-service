package com.christoskarakoutis.paymentsystem.service;

import com.christoskarakoutis.paymentsystem.entity.TransactionStatus;
import com.christoskarakoutis.paymentsystem.entity.Wallet;
import com.christoskarakoutis.paymentsystem.exception.ResourceNotFoundException;
import com.christoskarakoutis.paymentsystem.repository.TransactionRepository;
import com.christoskarakoutis.paymentsystem.repository.WalletRepository;
import com.christoskarakoutis.paymentsystem.entity.Transaction;
import com.christoskarakoutis.paymentsystem.dto.TransactionResponse;
import com.christoskarakoutis.paymentsystem.dto.TransactionRequest;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.List;
import org.springframework.data.domain.Pageable;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final WalletRepository walletRepository;

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

        if (sourceId.compareTo(targetId) < 0) {
            source = walletRepository.findByIdWithLock(sourceId)
                    .orElseThrow(() -> new ResourceNotFoundException("Wallet not found: " + sourceId));
            target = walletRepository.findByIdWithLock(targetId)
                    .orElseThrow(() -> new ResourceNotFoundException("Wallet not found: " + targetId));
        } else {
            target = walletRepository.findByIdWithLock(targetId)
                    .orElseThrow(() -> new ResourceNotFoundException("Wallet not found: " + targetId));
            source = walletRepository.findByIdWithLock(sourceId)
                    .orElseThrow(() -> new ResourceNotFoundException("Wallet not found: " + sourceId));
        }

        tx.setSourceWallet(source);
        tx.setTargetWallet(target);

        if (!tx.getStatus().canTransitionTo(TransactionStatus.PENDING)) {
            throw new IllegalStateException("Cannot transition to PENDING from " + tx.getStatus());
        }
        tx.setStatus(TransactionStatus.PENDING);
        transactionRepository.save(tx);

        if (source.getBalance().compareTo(request.amount()) < 0) {
            if (!tx.getStatus().canTransitionTo(TransactionStatus.FAILED)) {
                throw new IllegalStateException("Cannot transition to FAILED from " + tx.getStatus());
            }
            tx.setStatus(TransactionStatus.FAILED);
            transactionRepository.save(tx);
            return toResponse(tx);
        }

        source.setBalance(source.getBalance().subtract(request.amount()));
        target.setBalance(target.getBalance().add(request.amount()));

        walletRepository.save(source);
        walletRepository.save(target);

        if (!tx.getStatus().canTransitionTo(TransactionStatus.COMPLETED)) {
            throw new IllegalStateException("Cannot transition to COMPLETED from " + tx.getStatus());
        }
        tx.setStatus(TransactionStatus.COMPLETED);
        transactionRepository.save(tx);

        return toResponse(tx);
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
