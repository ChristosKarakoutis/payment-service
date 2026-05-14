package com.christoskarakoutis.paymentsystem.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "transactions", indexes = {
        @Index(name = "idx_tx_idempotency", columnList = "idempotencyKey", unique = true)
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

    @Column(nullable = false, unique = true)
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
