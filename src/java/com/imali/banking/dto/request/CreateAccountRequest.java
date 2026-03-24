package com.imali.banking.dto.request;

import com.imali.banking.domain.enums.AccountType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateAccountRequest {

    @NotNull(message = "Account type is required")
    private AccountType accountType;
}
