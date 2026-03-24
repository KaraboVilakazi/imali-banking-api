package com.imali.banking.domain.entity;

import com.imali.banking.domain.enums.TransactionType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    // Snapshot of the account balance after this transaction — enables audit trail
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal balanceAfter;

    @Column(length = 255)
    private String description;

    // Null for deposits (no source account)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_account_id")
    private Account sourceAccount;

    // Null for withdrawals (no destination account)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_account_id")
    private Account destinationAccount;

    // Fraud detection — flagged by rule engine during transaction processing
    @Column(nullable = false)
    @Builder.Default
    private boolean flagged = false;

    @Column(length = 255)
    private String fraudReason;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
