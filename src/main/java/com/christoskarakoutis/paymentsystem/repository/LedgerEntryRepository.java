package com.christoskarakoutis.paymentsystem.repository;

import com.christoskarakoutis.paymentsystem.entity.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {
    Optional<LedgerEntry> findTopByAccountIdOrderByIdDesc(String accountId);
}
