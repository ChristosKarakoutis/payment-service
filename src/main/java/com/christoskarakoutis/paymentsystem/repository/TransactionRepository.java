package com.christoskarakoutis.paymentsystem.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.christoskarakoutis.paymentsystem.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, String>{

    boolean existsByIdempotencyKey(String idempotencyKey);
    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);
    boolean existsByReferenceTransactionId(String referenceTransactionId);
    Page<Transaction> findAllBySourceWalletIdOrTargetWalletId(
            String sourceId, String targetId, Pageable pageable
    );

}
