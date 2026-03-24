package com.imali.banking.service;

import com.imali.banking.domain.entity.Account;
import com.imali.banking.domain.entity.Transaction;
import com.imali.banking.domain.entity.User;
import com.imali.banking.domain.enums.AccountStatus;
import com.imali.banking.domain.enums.AuditAction;
import com.imali.banking.domain.enums.TransactionType;
import com.imali.banking.dto.request.DepositRequest;
import com.imali.banking.dto.request.TransferRequest;
import com.imali.banking.dto.request.WithdrawRequest;
import com.imali.banking.dto.response.TransactionResponse;
import com.imali.banking.exception.AccountNotActiveException;
import com.imali.banking.exception.InsufficientFundsException;
import com.imali.banking.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private static final BigDecimal LARGE_TRANSACTION_THRESHOLD = new BigDecimal("10000");
    private static final int RAPID_TRANSFER_LIMIT = 3;
    private static final int RAPID_TRANSFER_WINDOW_MINUTES = 5;

    private final TransactionRepository transactionRepository;
    private final AccountService accountService;
    private final UserService userService;
    private final AuditLogService auditLogService;

    @Transactional
    public TransactionResponse deposit(DepositRequest request, Authentication authentication) {
        User user = userService.getAuthenticatedUser(authentication);
        Account account = accountService.findAccountWithLockOrThrow(request.getAccountId());

        accountService.assertOwnership(account, user.getId());
        assertAccountActive(account);

        account.setBalance(account.getBalance().add(request.getAmount()));

        FraudCheck fraud = checkFraud(account.getId(), request.getAmount(), TransactionType.DEPOSIT);

        Transaction transaction = Transaction.builder()
                .type(TransactionType.DEPOSIT)
                .amount(request.getAmount())
                .balanceAfter(account.getBalance())
                .description(request.getDescription())
                .destinationAccount(account)
                .flagged(fraud.flagged())
                .fraudReason(fraud.reason())
                .build();

        Transaction saved = transactionRepository.save(transaction);

        auditLogService.log(AuditAction.DEPOSIT, user.getId(), account.getId(), request.getAmount(),
                fraud.flagged() ? "FRAUD: " + fraud.reason() : null);

        if (fraud.flagged()) {
            auditLogService.log(AuditAction.FRAUD_FLAGGED, user.getId(), account.getId(),
                    request.getAmount(), fraud.reason());
        }

        return toResponse(saved);
    }

    @Transactional
    public TransactionResponse withdraw(WithdrawRequest request, Authentication authentication) {
        User user = userService.getAuthenticatedUser(authentication);
        Account account = accountService.findAccountWithLockOrThrow(request.getAccountId());

        accountService.assertOwnership(account, user.getId());
        assertAccountActive(account);
        assertSufficientFunds(account, request.getAmount());

        account.setBalance(account.getBalance().subtract(request.getAmount()));

        FraudCheck fraud = checkFraud(account.getId(), request.getAmount(), TransactionType.WITHDRAWAL);

        Transaction transaction = Transaction.builder()
                .type(TransactionType.WITHDRAWAL)
                .amount(request.getAmount())
                .balanceAfter(account.getBalance())
                .description(request.getDescription())
                .sourceAccount(account)
                .flagged(fraud.flagged())
                .fraudReason(fraud.reason())
                .build();

        Transaction saved = transactionRepository.save(transaction);

        auditLogService.log(AuditAction.WITHDRAWAL, user.getId(), account.getId(), request.getAmount(),
                fraud.flagged() ? "FRAUD: " + fraud.reason() : null);

        if (fraud.flagged()) {
            auditLogService.log(AuditAction.FRAUD_FLAGGED, user.getId(), account.getId(),
                    request.getAmount(), fraud.reason());
        }

        return toResponse(saved);
    }

    @Transactional
    public TransactionResponse transfer(TransferRequest request, Authentication authentication) {
        if (request.getSourceAccountId().equals(request.getDestinationAccountId())) {
            throw new IllegalArgumentException("Source and destination accounts cannot be the same");
        }

        User user = userService.getAuthenticatedUser(authentication);

        // Lock both accounts in consistent order to prevent deadlocks
        UUID firstId  = min(request.getSourceAccountId(), request.getDestinationAccountId());
        UUID secondId = max(request.getSourceAccountId(), request.getDestinationAccountId());

        Account first  = accountService.findAccountWithLockOrThrow(firstId);
        Account second = accountService.findAccountWithLockOrThrow(secondId);

        Account source      = first.getId().equals(request.getSourceAccountId()) ? first : second;
        Account destination = first.getId().equals(request.getDestinationAccountId()) ? first : second;

        accountService.assertOwnership(source, user.getId());
        assertAccountActive(source);
        assertAccountActive(destination);
        assertSufficientFunds(source, request.getAmount());

        source.setBalance(source.getBalance().subtract(request.getAmount()));
        destination.setBalance(destination.getBalance().add(request.getAmount()));

        FraudCheck fraud = checkFraud(source.getId(), request.getAmount(), TransactionType.TRANSFER_DEBIT);

        Transaction debit = Transaction.builder()
                .type(TransactionType.TRANSFER_DEBIT)
                .amount(request.getAmount())
                .balanceAfter(source.getBalance())
                .description(request.getDescription())
                .sourceAccount(source)
                .destinationAccount(destination)
                .flagged(fraud.flagged())
                .fraudReason(fraud.reason())
                .build();

        Transaction credit = Transaction.builder()
                .type(TransactionType.TRANSFER_CREDIT)
                .amount(request.getAmount())
                .balanceAfter(destination.getBalance())
                .description(request.getDescription())
                .sourceAccount(source)
                .destinationAccount(destination)
                .build();

        transactionRepository.save(debit);
        transactionRepository.save(credit);

        auditLogService.log(AuditAction.TRANSFER, user.getId(), source.getId(), request.getAmount(),
                fraud.flagged() ? "FRAUD: " + fraud.reason() : null);

        if (fraud.flagged()) {
            auditLogService.log(AuditAction.FRAUD_FLAGGED, user.getId(), source.getId(),
                    request.getAmount(), fraud.reason());
        }

        // Return the debit record from the sender's perspective
        return toResponse(debit);
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponse> getTransactionHistory(
            UUID accountId, Authentication authentication, Pageable pageable) {

        User user = userService.getAuthenticatedUser(authentication);
        Account account = accountService.findAccountOrThrow(accountId);
        accountService.assertOwnership(account, user.getId());

        return transactionRepository.findAllByAccountId(accountId, pageable)
                .map(this::toResponse);
    }

    // --- Fraud detection ---

    private FraudCheck checkFraud(UUID accountId, BigDecimal amount, TransactionType type) {
        if (amount.compareTo(LARGE_TRANSACTION_THRESHOLD) > 0) {
            return new FraudCheck(true, "Large transaction: amount R" + amount + " exceeds R10,000 threshold");
        }

        if (type == TransactionType.TRANSFER_DEBIT) {
            LocalDateTime since = LocalDateTime.now().minusMinutes(RAPID_TRANSFER_WINDOW_MINUTES);
            long recentCount = transactionRepository.countRecentTransfers(accountId, since);
            if (recentCount >= RAPID_TRANSFER_LIMIT) {
                return new FraudCheck(true,
                        "Rapid transfer activity: " + recentCount + " transfers in the last " +
                        RAPID_TRANSFER_WINDOW_MINUTES + " minutes");
            }
        }

        return new FraudCheck(false, null);
    }

    private record FraudCheck(boolean flagged, String reason) {}

    // --- Helpers ---

    private void assertAccountActive(Account account) {
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountNotActiveException(
                    "Account " + account.getAccountNumber() + " is not active (status: " + account.getStatus() + ")"
            );
        }
    }

    private void assertSufficientFunds(Account account, BigDecimal amount) {
        if (account.getBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException(
                    "Insufficient funds. Available balance: R" + account.getBalance()
            );
        }
    }

    private UUID min(UUID a, UUID b) {
        return a.compareTo(b) <= 0 ? a : b;
    }

    private UUID max(UUID a, UUID b) {
        return a.compareTo(b) >= 0 ? a : b;
    }

    private TransactionResponse toResponse(Transaction transaction) {
        return TransactionResponse.builder()
                .id(transaction.getId())
                .type(transaction.getType())
                .amount(transaction.getAmount())
                .balanceAfter(transaction.getBalanceAfter())
                .description(transaction.getDescription())
                .sourceAccountNumber(
                        transaction.getSourceAccount() != null
                                ? transaction.getSourceAccount().getAccountNumber()
                                : null
                )
                .destinationAccountNumber(
                        transaction.getDestinationAccount() != null
                                ? transaction.getDestinationAccount().getAccountNumber()
                                : null
                )
                .flagged(transaction.isFlagged())
                .fraudReason(transaction.getFraudReason())
                .createdAt(transaction.getCreatedAt())
                .build();
    }
}
