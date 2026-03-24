package com.imali.banking.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.imali.banking.domain.enums.AccountType;
import com.imali.banking.dto.request.CreateAccountRequest;
import com.imali.banking.dto.request.DepositRequest;
import com.imali.banking.dto.request.RegisterRequest;
import com.imali.banking.dto.request.TransferRequest;
import com.imali.banking.dto.request.WithdrawRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TransactionIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    private static final String EMAIL_SUFFIX = "@tx-test.com";

    private String senderToken;
    private UUID senderAccountId;
    private String recipientToken;
    private UUID recipientAccountId;

    @BeforeEach
    void setUp() throws Exception {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        senderToken       = registerAndGetToken("sender-"    + unique);
        recipientToken    = registerAndGetToken("recipient-" + unique);
        senderAccountId   = createAccount(senderToken,    AccountType.CHEQUE);
        recipientAccountId = createAccount(recipientToken, AccountType.CHEQUE);
    }

    // --- Deposit ---

    @Test
    void deposit_validRequest_returns201() throws Exception {
        DepositRequest request = new DepositRequest();
        request.setAccountId(senderAccountId);
        request.setAmount(new BigDecimal("500.00"));
        request.setDescription("Initial deposit");

        mockMvc.perform(post("/api/v1/transactions/deposit")
                .header("Authorization", "Bearer " + senderToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.type").value("DEPOSIT"))
                .andExpect(jsonPath("$.amount").value(500.00))
                .andExpect(jsonPath("$.balanceAfter").value(500.00));
    }

    @Test
    void deposit_noAuth_returns401() throws Exception {
        DepositRequest request = new DepositRequest();
        request.setAccountId(senderAccountId);
        request.setAmount(new BigDecimal("100.00"));

        mockMvc.perform(post("/api/v1/transactions/deposit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deposit_toOtherUsersAccount_returns403() throws Exception {
        DepositRequest request = new DepositRequest();
        request.setAccountId(recipientAccountId); // recipient's account
        request.setAmount(new BigDecimal("100.00"));

        // But authenticated as sender
        mockMvc.perform(post("/api/v1/transactions/deposit")
                .header("Authorization", "Bearer " + senderToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void deposit_largeAmount_flaggedForFraud() throws Exception {
        DepositRequest request = new DepositRequest();
        request.setAccountId(senderAccountId);
        request.setAmount(new BigDecimal("15000.00")); // exceeds R10,000 threshold

        mockMvc.perform(post("/api/v1/transactions/deposit")
                .header("Authorization", "Bearer " + senderToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.flagged").value(true))
                .andExpect(jsonPath("$.fraudReason").isNotEmpty());
    }

    // --- Withdraw ---

    @Test
    void withdraw_sufficientFunds_returns201() throws Exception {
        // Deposit first
        deposit(senderToken, senderAccountId, new BigDecimal("1000.00"));

        WithdrawRequest request = new WithdrawRequest();
        request.setAccountId(senderAccountId);
        request.setAmount(new BigDecimal("300.00"));
        request.setDescription("ATM withdrawal");

        mockMvc.perform(post("/api/v1/transactions/withdraw")
                .header("Authorization", "Bearer " + senderToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("WITHDRAWAL"))
                .andExpect(jsonPath("$.amount").value(300.00))
                .andExpect(jsonPath("$.balanceAfter").value(700.00));
    }

    @Test
    void withdraw_insufficientFunds_returns422() throws Exception {
        // Account has zero balance
        WithdrawRequest request = new WithdrawRequest();
        request.setAccountId(senderAccountId);
        request.setAmount(new BigDecimal("500.00"));

        mockMvc.perform(post("/api/v1/transactions/withdraw")
                .header("Authorization", "Bearer " + senderToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422));
    }

    @Test
    void withdraw_zeroAmount_returns400() throws Exception {
        WithdrawRequest request = new WithdrawRequest();
        request.setAccountId(senderAccountId);
        request.setAmount(new BigDecimal("0.00"));

        mockMvc.perform(post("/api/v1/transactions/withdraw")
                .header("Authorization", "Bearer " + senderToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // --- Transfer ---

    @Test
    void transfer_validRequest_returns201AndUpdatesBalances() throws Exception {
        deposit(senderToken, senderAccountId, new BigDecimal("1000.00"));

        TransferRequest request = new TransferRequest();
        request.setSourceAccountId(senderAccountId);
        request.setDestinationAccountId(recipientAccountId);
        request.setAmount(new BigDecimal("250.00"));
        request.setDescription("Rent payment");

        mockMvc.perform(post("/api/v1/transactions/transfer")
                .header("Authorization", "Bearer " + senderToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("TRANSFER_DEBIT"))
                .andExpect(jsonPath("$.amount").value(250.00))
                .andExpect(jsonPath("$.balanceAfter").value(750.00));
    }

    @Test
    void transfer_sameSourceAndDestination_returns400() throws Exception {
        deposit(senderToken, senderAccountId, new BigDecimal("500.00"));

        TransferRequest request = new TransferRequest();
        request.setSourceAccountId(senderAccountId);
        request.setDestinationAccountId(senderAccountId); // same account
        request.setAmount(new BigDecimal("100.00"));

        mockMvc.perform(post("/api/v1/transactions/transfer")
                .header("Authorization", "Bearer " + senderToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void transfer_insufficientFunds_returns422() throws Exception {
        // senderAccountId has zero balance
        TransferRequest request = new TransferRequest();
        request.setSourceAccountId(senderAccountId);
        request.setDestinationAccountId(recipientAccountId);
        request.setAmount(new BigDecimal("100.00"));

        mockMvc.perform(post("/api/v1/transactions/transfer")
                .header("Authorization", "Bearer " + senderToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void transfer_fromOtherUsersAccount_returns403() throws Exception {
        deposit(recipientToken, recipientAccountId, new BigDecimal("500.00"));

        TransferRequest request = new TransferRequest();
        request.setSourceAccountId(recipientAccountId); // not owned by sender
        request.setDestinationAccountId(senderAccountId);
        request.setAmount(new BigDecimal("100.00"));

        mockMvc.perform(post("/api/v1/transactions/transfer")
                .header("Authorization", "Bearer " + senderToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    // --- Transaction history ---

    @Test
    void getTransactionHistory_returnsPaginatedResults() throws Exception {
        deposit(senderToken, senderAccountId, new BigDecimal("100.00"));
        deposit(senderToken, senderAccountId, new BigDecimal("200.00"));

        mockMvc.perform(get("/api/v1/transactions/account/" + senderAccountId)
                .header("Authorization", "Bearer " + senderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(2))))
                .andExpect(jsonPath("$.totalElements").value(greaterThanOrEqualTo(2)));
    }

    @Test
    void getTransactionHistory_otherUsersAccount_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/transactions/account/" + recipientAccountId)
                .header("Authorization", "Bearer " + senderToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void getTransactionHistory_noAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/transactions/account/" + senderAccountId))
                .andExpect(status().isUnauthorized());
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

    private UUID createAccount(String token, AccountType type) throws Exception {
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

    private void deposit(String token, UUID accountId, BigDecimal amount) throws Exception {
        DepositRequest request = new DepositRequest();
        request.setAccountId(accountId);
        request.setAmount(amount);

        mockMvc.perform(post("/api/v1/transactions/deposit")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }
}
