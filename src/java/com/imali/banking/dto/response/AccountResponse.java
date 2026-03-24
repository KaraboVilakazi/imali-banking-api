package com.imali.banking.dto.response;

import com.imali.banking.domain.enums.AccountStatus;
import com.imali.banking.domain.enums.AccountType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class AccountResponse {

    private UUID id;
    private String accountNumber;
    private AccountType accountType;
    private AccountStatus status;
    private BigDecimal balance;
    private String currency;
    private LocalDateTime createdAt;
}
