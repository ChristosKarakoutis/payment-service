package com.christoskarakoutis.paymentsystem.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
// Idempotency keys are scoped to the source wallet, so two users can never collide
// on (or read) each other's transactions through the same key.
@Table(name = "transactions", uniqueConstraints = {
        @UniqueConstraint(name = "uk_tx_source_idempotency", columnNames = {"source_wallet_id", "idempotency_key"})
})
@Getter
@Setter
@ToString(exclude = {"sourceWallet", "targetWallet"})
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Version
    private Long version;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(name = "reference_tx_id")
    private String referenceTransactionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_wallet_id")
    private Wallet sourceWallet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_wallet_id")
    private Wallet targetWallet;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionStatus status;

    @CreationTimestamp
    @Column(updatable = false, nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
      this.createdAt = Instant.now();
    }
}
