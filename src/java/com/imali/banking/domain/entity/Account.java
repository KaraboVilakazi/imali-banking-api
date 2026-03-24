package com.imali.banking.domain.entity;

import com.imali.banking.domain.enums.AccountStatus;
import com.imali.banking.domain.enums.AccountType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "accounts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = false, length = 12)
    private String accountNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountType accountType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountStatus status;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal balance;

    // ISO 4217 currency code — ZAR for South African Rand
    @Column(nullable = false, length = 3)
    private String currency;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Builder.Default
    @OneToMany(mappedBy = "sourceAccount", fetch = FetchType.LAZY)
    private List<Transaction> outgoingTransactions = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "destinationAccount", fetch = FetchType.LAZY)
    private List<Transaction> incomingTransactions = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
