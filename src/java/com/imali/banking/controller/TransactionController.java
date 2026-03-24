package com.imali.banking.controller;

import com.imali.banking.dto.request.DepositRequest;
import com.imali.banking.dto.request.TransferRequest;
import com.imali.banking.dto.request.WithdrawRequest;
import com.imali.banking.dto.response.TransactionResponse;
import com.imali.banking.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping("/deposit")
    public ResponseEntity<TransactionResponse> deposit(
            @Valid @RequestBody DepositRequest request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(transactionService.deposit(request, authentication));
    }

    @PostMapping("/withdraw")
    public ResponseEntity<TransactionResponse> withdraw(
            @Valid @RequestBody WithdrawRequest request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(transactionService.withdraw(request, authentication));
    }

    @PostMapping("/transfer")
    public ResponseEntity<TransactionResponse> transfer(
            @Valid @RequestBody TransferRequest request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(transactionService.transfer(request, authentication));
    }

    @GetMapping("/account/{accountId}")
    public ResponseEntity<Page<TransactionResponse>> getTransactionHistory(
            @PathVariable UUID accountId,
            Authentication authentication,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(transactionService.getTransactionHistory(accountId, authentication, pageable));
    }
}
