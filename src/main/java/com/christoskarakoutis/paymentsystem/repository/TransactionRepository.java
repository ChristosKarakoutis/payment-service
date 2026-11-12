package com.christoskarakoutis.paymentsystem.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.christoskarakoutis.paymentsystem.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, String>{

    Optional<Transaction> findBySourceWalletIdAndIdempotencyKey(String sourceWalletId, String idempotencyKey);
    boolean existsByReferenceTransactionId(String referenceTransactionId);
    boolean existsBySourceWalletIdOrTargetWalletId(String sourceWalletId, String targetWalletId);
    Page<Transaction> findAllBySourceWalletIdOrTargetWalletId(
            String sourceId, String targetId, Pageable pageable
    );

}
