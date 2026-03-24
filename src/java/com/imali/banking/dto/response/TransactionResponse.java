package com.imali.banking.dto.response;

import com.imali.banking.domain.enums.TransactionType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class TransactionResponse {

    private UUID id;
    private TransactionType type;
    private BigDecimal amount;
    private BigDecimal balanceAfter;
    private String description;
    private String sourceAccountNumber;
    private String destinationAccountNumber;
    private boolean flagged;
    private String fraudReason;
    private LocalDateTime createdAt;
}
