package com.imali.banking.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class DepositRequest {

    @NotNull(message = "Account ID is required")
    private UUID accountId;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Deposit amount must be greater than zero")
    private BigDecimal amount;

    private String description;
}
