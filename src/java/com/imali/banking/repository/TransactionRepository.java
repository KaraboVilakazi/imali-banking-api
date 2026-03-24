package com.imali.banking.repository;

import com.imali.banking.domain.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    // Returns all transactions where the account is either source or destination
    // Returns all transactions where the account is either source or destination
    @Query("""
            SELECT t FROM Transaction t
            WHERE t.sourceAccount.id = :accountId
               OR t.destinationAccount.id = :accountId
            ORDER BY t.createdAt DESC
            """)
    Page<Transaction> findAllByAccountId(@Param("accountId") UUID accountId, Pageable pageable);

    // Count outgoing transfers from an account within a time window — used for rapid-transfer fraud detection
    @Query("""
            SELECT COUNT(t) FROM Transaction t
            WHERE t.sourceAccount.id = :accountId
              AND t.type IN (com.imali.banking.domain.enums.TransactionType.TRANSFER_DEBIT)
              AND t.createdAt >= :since
            """)
    long countRecentTransfers(@Param("accountId") UUID accountId, @Param("since") LocalDateTime since);
}
