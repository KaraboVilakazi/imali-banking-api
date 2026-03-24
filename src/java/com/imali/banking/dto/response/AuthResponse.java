package com.imali.banking.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuthResponse {

    private String accessToken;

    @Builder.Default
    private String tokenType = "Bearer";
}
