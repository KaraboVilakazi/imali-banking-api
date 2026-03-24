package com.imali.banking.controller;

import com.imali.banking.dto.request.CreateAccountRequest;
import com.imali.banking.dto.response.AccountResponse;
import com.imali.banking.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @PostMapping
    public ResponseEntity<AccountResponse> createAccount(
            @Valid @RequestBody CreateAccountRequest request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(accountService.createAccount(request, authentication));
    }

    @GetMapping
    public ResponseEntity<List<AccountResponse>> getMyAccounts(Authentication authentication) {
        return ResponseEntity.ok(accountService.getMyAccounts(authentication));
    }

    @GetMapping("/{accountId}")
    public ResponseEntity<AccountResponse> getAccountById(
            @PathVariable UUID accountId,
            Authentication authentication) {
        return ResponseEntity.ok(accountService.getAccountById(accountId, authentication));
    }
}
