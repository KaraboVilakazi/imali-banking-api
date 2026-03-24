package com.imali.banking.domain.entity;

import com.imali.banking.domain.enums.AuditAction;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuditAction action;

    // The user who performed the action
    @Column
    private UUID userId;

    // The account involved (nullable — e.g. login has no account)
    @Column
    private UUID accountId;

    // The amount involved (nullable — e.g. login has no amount)
    @Column(precision = 19, scale = 4)
    private BigDecimal amount;

    // Free-form context: fraud reason, error message, etc.
    @Column(length = 500)
    private String metadata;

    @Column(nullable = false, updatable = false)
    private LocalDateTime timestamp;

    @PrePersist
    protected void onCreate() {
        timestamp = LocalDateTime.now();
    }
}
