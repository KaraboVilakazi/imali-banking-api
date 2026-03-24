package com.imali.banking.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.imali.banking.dto.request.CreateAccountRequest;
import com.imali.banking.dto.request.RegisterRequest;
import com.imali.banking.domain.enums.AccountType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    // Each test class uses a unique email namespace to avoid collisions with other test classes
    private static final String EMAIL_SUFFIX = "@account-test.com";
    private String primaryToken;
    private String secondaryToken;

    @BeforeEach
    void setUp() throws Exception {
        primaryToken   = registerAndGetToken("primary"  + UUID.randomUUID());
        secondaryToken = registerAndGetToken("secondary" + UUID.randomUUID());
    }

    @Test
    void createAccount_authenticated_returns201() throws Exception {
        CreateAccountRequest request = new CreateAccountRequest();
        request.setAccountType(AccountType.CHEQUE);

        mockMvc.perform(post("/api/v1/accounts")
                .header("Authorization", "Bearer " + primaryToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.accountNumber").isNotEmpty())
                .andExpect(jsonPath("$.accountType").value("CHEQUE"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.balance").value(0))
                .andExpect(jsonPath("$.currency").value("ZAR"));
    }

    @Test
    void createAccount_noAuth_returns401() throws Exception {
        CreateAccountRequest request = new CreateAccountRequest();
        request.setAccountType(AccountType.SAVINGS);

        mockMvc.perform(post("/api/v1/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createAccount_missingAccountType_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/accounts")
                .header("Authorization", "Bearer " + primaryToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.accountType").exists());
    }

    @Test
    void getMyAccounts_returnsOwnedAccounts() throws Exception {
        // Create two accounts for primary user
        createAccount(primaryToken, AccountType.CHEQUE);
        createAccount(primaryToken, AccountType.SAVINGS);

        mockMvc.perform(get("/api/v1/accounts")
                .header("Authorization", "Bearer " + primaryToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(2))));
    }

    @Test
    void getMyAccounts_noAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/accounts"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getAccountById_ownAccount_returns200() throws Exception {
        UUID accountId = createAccount(primaryToken, AccountType.FIXED_DEPOSIT);

        mockMvc.perform(get("/api/v1/accounts/" + accountId)
                .header("Authorization", "Bearer " + primaryToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(accountId.toString()))
                .andExpect(jsonPath("$.accountType").value("FIXED_DEPOSIT"));
    }

    @Test
    void getAccountById_otherUsersAccount_returns403() throws Exception {
        // Primary user creates an account
        UUID accountId = createAccount(primaryToken, AccountType.CHEQUE);

        // Secondary user tries to access it
        mockMvc.perform(get("/api/v1/accounts/" + accountId)
                .header("Authorization", "Bearer " + secondaryToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void getAccountById_nonExistent_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/" + UUID.randomUUID())
                .header("Authorization", "Bearer " + primaryToken))
                .andExpect(status().isNotFound());
    }

    // --- Helpers ---

    private String registerAndGetToken(String usernamePrefix) throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setFirstName("Test");
        request.setLastName("User");
        request.setEmail(usernamePrefix + EMAIL_SUFFIX);
        request.setPassword("securepass123");

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("accessToken").asText();
    }

    UUID createAccount(String token, AccountType type) throws Exception {
        CreateAccountRequest request = new CreateAccountRequest();
        request.setAccountType(type);

        MvcResult result = mockMvc.perform(post("/api/v1/accounts")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return UUID.fromString(body.get("id").asText());
    }
}
