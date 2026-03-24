package com.imali.banking.service;

import com.imali.banking.domain.entity.Account;
import com.imali.banking.domain.entity.User;
import com.imali.banking.domain.enums.AccountStatus;
import com.imali.banking.dto.request.CreateAccountRequest;
import com.imali.banking.dto.response.AccountResponse;
import com.imali.banking.exception.AccountNotFoundException;
import com.imali.banking.exception.UnauthorizedAccountAccessException;
import com.imali.banking.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final UserService userService;

    private static final SecureRandom RANDOM = new SecureRandom();

    @Transactional
    public AccountResponse createAccount(CreateAccountRequest request, Authentication authentication) {
        User user = userService.getAuthenticatedUser(authentication);

        Account account = Account.builder()
                .accountNumber(generateUniqueAccountNumber())
                .accountType(request.getAccountType())
                .status(AccountStatus.ACTIVE)
                .balance(BigDecimal.ZERO)
                .currency("ZAR")
                .user(user)
                .build();

        Account saved = accountRepository.save(account);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> getMyAccounts(Authentication authentication) {
        User user = userService.getAuthenticatedUser(authentication);
        return accountRepository.findAllByUserId(user.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccountById(UUID accountId, Authentication authentication) {
        User user = userService.getAuthenticatedUser(authentication);
        Account account = findAccountOrThrow(accountId);
        assertOwnership(account, user.getId());
        return toResponse(account);
    }

    // Package-private — used by TransactionService within the same transaction
    Account findAccountOrThrow(UUID accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));
    }

    Account findAccountWithLockOrThrow(UUID accountId) {
        return accountRepository.findByIdWithLock(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));
    }

    void assertOwnership(Account account, UUID userId) {
        if (!account.getUser().getId().equals(userId)) {
            throw new UnauthorizedAccountAccessException("You do not have access to account: " + account.getId());
        }
    }

    private String generateUniqueAccountNumber() {
        String accountNumber;
        do {
            // Format: 62 + 8 random digits (mimics South African bank account format)
            long suffix = (long) (RANDOM.nextDouble() * 1_000_000_00L);
            accountNumber = String.format("62%08d", suffix);
        } while (accountRepository.existsByAccountNumber(accountNumber));
        return accountNumber;
    }

    AccountResponse toResponse(Account account) {
        return AccountResponse.builder()
                .id(account.getId())
                .accountNumber(account.getAccountNumber())
                .accountType(account.getAccountType())
                .status(account.getStatus())
                .balance(account.getBalance())
                .currency(account.getCurrency())
                .createdAt(account.getCreatedAt())
                .build();
    }
}
