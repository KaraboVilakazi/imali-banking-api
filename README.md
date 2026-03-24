# Imali Banking API

![CI](https://github.com/KaraboVilakazi/imali-banking-api/actions/workflows/ci.yml/badge.svg)

A production-style RESTful banking API built with Spring Boot 3 and Java 17. Imali (meaning *money* in Zulu) provides core retail banking operations including user authentication, account management, and financial transactions, secured with JWT, backed by PostgreSQL, and equipped with audit logging and fraud detection.

> Designed to simulate real-world banking system constraints including concurrency control, audit compliance, and fraud detection.

---

## Table of Contents

- [Features](#features)
- [Tech Stack](#tech-stack)
- [Architecture](#architecture)
- [Prerequisites](#prerequisites)
- [Getting Started](#getting-started)
- [Running with Docker](#running-with-docker)
- [Running the Tests](#running-the-tests)
- [CI Pipeline](#ci-pipeline)
- [API Reference](#api-reference)
- [Testing with curl](#testing-with-curl)
- [Project Structure](#project-structure)

---

## Features

- User registration and login with JWT authentication (24-hour token expiry)
- Create and manage bank accounts (Cheque, Savings, Fixed Deposit)
- Deposit, withdraw, and transfer funds between accounts
- **Concurrency-safe transfers** using pessimistic locking and deadlock prevention
- **Fraud detection** — flags large transactions (>R10,000) and rapid repeated transfers
- **Full audit logging** — every login, transaction, and fraud event is recorded
- Paginated transaction history with balance snapshots on every record
- Comprehensive error handling with descriptive HTTP responses
- Role-based access control (`CUSTOMER` / `ADMIN`)
- **Swagger UI** — interactive API documentation at `/swagger-ui/index.html`

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.2.3 |
| Security | Spring Security + JWT (JJWT) |
| Persistence | Spring Data JPA + Hibernate |
| Database | PostgreSQL |
| Containerisation | Docker + Docker Compose |
| CI | GitHub Actions |
| Docs | Swagger UI (springdoc-openapi) |
| Build | Maven |
| Test DB | H2 (in-memory, test scope only) |

---

## Architecture

```
Client
  │
  ▼
[JwtAuthenticationFilter]  ← validates Bearer token on every request
  │
  ▼
[Controllers]              ← AuthController / AccountController / TransactionController
  │
  ▼
[Services]                 ← Business logic, fraud detection, audit logging
  │
  ├──► [AccountService]    ← Pessimistic locking for concurrent transfers
  ├──► [TransactionService]← Fraud detection rules + balance snapshots
  └──► [AuditLogService]   ← Writes to audit_logs (REQUIRES_NEW propagation)
  │
  ▼
[Repositories]             ← Spring Data JPA
  │
  ▼
[PostgreSQL]               ← accounts / transactions / audit_logs
```

---

## Key Design Decisions

- **Pessimistic locking** — both accounts in a transfer are locked in consistent UUID order before any balance update, guaranteeing correctness under concurrent load and preventing deadlocks
- **Audit log isolation** — `AuditLogService` runs in `REQUIRES_NEW` propagation, so audit records are written and committed independently; a failed transaction does not erase its audit trail
- **Non-blocking fraud detection** — flagged transactions are processed normally and marked with `flagged: true`, mirroring how real banking systems quarantine suspicious activity for review rather than rejecting it outright
- **Balance snapshots** — every `Transaction` record stores `balanceAfter` at the time of the operation, enabling point-in-time account reconstruction without replaying the full transaction history

---

## Prerequisites

### Running locally (without Docker)

- **Java 17+** — [Download JDK](https://adoptium.net/)
- **Maven 3.8+** — [Download Maven](https://maven.apache.org/download.cgi)
- **PostgreSQL 13+** — [Download PostgreSQL](https://www.postgresql.org/download/)

```bash
java -version
mvn -version
psql --version
```

### Running with Docker

- **Docker Desktop** — [Download Docker](https://www.docker.com/products/docker-desktop/)

---

## Getting Started

### 1. Clone the repository

```bash
git clone https://github.com/KaraboVilakazi/imali-banking-api.git
cd imali-banking-api
```

### 2. Set up PostgreSQL

```bash
psql -U postgres
```

```sql
CREATE DATABASE imali_db;
\q
```

Hibernate will automatically create all tables on first startup (`ddl-auto: update`).

### 3. Configure environment variables

```bash
export DB_USERNAME=postgres
export DB_PASSWORD=your_postgres_password
export JWT_SECRET=your_64_char_hex_secret
```

> If not set, the app falls back to the defaults in `application.yml`. Always override these in non-local environments.

### 4. Build and run

```bash
mvn clean install -DskipTests
mvn spring-boot:run
```

The API starts at **http://localhost:8080**.

### 5. Swagger UI

```
http://localhost:8080/swagger-ui/index.html
```

---

## Running with Docker

The easiest way to run the full stack. Docker Compose starts PostgreSQL and the application together — no local Java or database setup needed.

```bash
docker compose up --build
```

The API starts at **http://localhost:8080**. PostgreSQL is exposed on port `5432`.

To stop and remove containers:

```bash
docker compose down
```

To also remove the persisted database volume:

```bash
docker compose down -v
```

Environment variables can be overridden at runtime:

```bash
DB_USERNAME=postgres DB_PASSWORD=secret JWT_SECRET=your_hex_secret docker compose up --build
```

---

## Running the Tests

```bash
mvn test
```

Uses an H2 in-memory database — no PostgreSQL setup required. The `test` Spring profile is activated automatically via `@ActiveProfiles("test")` on each test class, which loads `application-test.yml`.

The test suite covers 19 integration tests across three classes:

| Class | Coverage |
|---|---|
| `AuthIntegrationTest` | Register, login, duplicate email, validation errors, wrong password |
| `AccountIntegrationTest` | Create account, list accounts, get by ID, ownership enforcement |
| `TransactionIntegrationTest` | Deposit, withdraw, transfer, fraud flagging, insufficient funds, transaction history |

---

## CI Pipeline

A GitHub Actions workflow runs on every push and pull request:

```
.github/workflows/ci.yml
```

- Checks out the code
- Sets up Java 17 (Temurin) with Maven dependency caching
- Runs `mvn test -B` against H2 — no external services required

Status badge can be added to this README once the repo is connected to Actions.

---

## API Reference

All endpoints except `/api/v1/auth/**` require `Authorization: Bearer <token>`.

### Authentication

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/v1/auth/register` | Register a new user |
| `POST` | `/api/v1/auth/login` | Login and receive JWT |

**Register — Request body:**
```json
{
  "firstName": "Karabo",
  "lastName": "Vilakazi",
  "email": "karabo@example.com",
  "password": "securepassword123"
}
```

**Login / Register — Response `200/201`:**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9..."
}
```

---

### Accounts

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/v1/accounts` | Create a new account |
| `GET` | `/api/v1/accounts` | List my accounts |
| `GET` | `/api/v1/accounts/{id}` | Get account by ID |

`accountType` values: `CHEQUE`, `SAVINGS`, `FIXED_DEPOSIT`

---

### Transactions

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/v1/transactions/deposit` | Deposit funds |
| `POST` | `/api/v1/transactions/withdraw` | Withdraw funds |
| `POST` | `/api/v1/transactions/transfer` | Transfer between accounts |
| `GET` | `/api/v1/transactions/account/{accountId}` | Transaction history (paginated) |

**Transaction response (includes fraud fields):**
```json
{
  "id": "uuid",
  "type": "TRANSFER_DEBIT",
  "amount": 15000.00,
  "balanceAfter": 5000.00,
  "description": "Rent",
  "sourceAccountNumber": "621234567890",
  "destinationAccountNumber": "629876543210",
  "flagged": true,
  "fraudReason": "Large transaction: amount R15000.00 exceeds R10,000 threshold",
  "createdAt": "2026-03-24T10:30:00"
}
```

#### Fraud Detection Rules

| Rule | Threshold | Behaviour |
|---|---|---|
| Large transaction | Amount > R10,000 | Transaction proceeds, `flagged: true` returned |
| Rapid transfers | > 3 transfers in 5 minutes from same account | Transaction proceeds, `flagged: true` returned |

Flagged transactions also write a dedicated `FRAUD_FLAGGED` entry to the audit log.

#### Audit Log Events

| Event | When |
|---|---|
| `REGISTER` | New user registered |
| `LOGIN` | Successful login |
| `DEPOSIT` | Every deposit |
| `WITHDRAWAL` | Every withdrawal |
| `TRANSFER` | Every transfer (source account) |
| `FRAUD_FLAGGED` | Dedicated entry when any fraud rule triggers |

Audit logs use `Propagation.REQUIRES_NEW` — they persist even if the parent transaction rolls back.

---

## Testing with curl

### Step 1 — Register

```bash
curl -s -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "firstName": "Karabo",
    "lastName": "Vilakazi",
    "email": "karabo@example.com",
    "password": "securepassword123"
  }' | jq .
```

### Step 2 — Set your token

```bash
TOKEN="<paste-token-here>"
```

### Step 3 — Create a Cheque account

```bash
curl -s -X POST http://localhost:8080/api/v1/accounts \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"accountType": "CHEQUE"}' | jq .
```

### Step 4 — Deposit funds

```bash
ACCOUNT_ID="<paste-account-id-here>"

curl -s -X POST http://localhost:8080/api/v1/transactions/deposit \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"accountId\": \"$ACCOUNT_ID\", \"amount\": 1000.00}" | jq .
```

### Step 5 — Check balance

```bash
curl -s http://localhost:8080/api/v1/accounts/$ACCOUNT_ID \
  -H "Authorization: Bearer $TOKEN" | jq .
```

### Step 6 — Transfer funds (triggers fraud detection if > R10,000)

```bash
ACCOUNT_ID_2="<second-account-id>"

curl -s -X POST http://localhost:8080/api/v1/transactions/transfer \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"sourceAccountId\": \"$ACCOUNT_ID\",
    \"destinationAccountId\": \"$ACCOUNT_ID_2\",
    \"amount\": 15000.00,
    \"description\": \"Rent\"
  }" | jq .
```

### Step 7 — View transaction history

```bash
curl -s "http://localhost:8080/api/v1/transactions/account/$ACCOUNT_ID?page=0&size=10" \
  -H "Authorization: Bearer $TOKEN" | jq .
```

> **Tip:** Install `jq` with `brew install jq` (macOS) or `sudo apt install jq` (Ubuntu). Optional — the API works fine without it.

---

## Project Structure

```
imali-banking-api/
├── Dockerfile                          # Multi-stage build (Maven → JRE alpine)
├── docker-compose.yml                  # App + PostgreSQL stack
├── pom.xml
├── .github/
│   └── workflows/
│       └── ci.yml                      # GitHub Actions CI (mvn test on push/PR)
└── src/
    ├── java/com/imali/banking/
    │   ├── ImaliBankingApplication.java
    │   ├── controller/        # REST controllers (Auth, Account, Transaction)
    │   ├── service/           # Business logic, fraud detection, audit logging
    │   ├── repository/        # Spring Data JPA repositories
    │   ├── domain/
    │   │   ├── entity/        # JPA entities (User, Account, Transaction, AuditLog)
    │   │   └── enums/         # AccountType, AccountStatus, TransactionType, UserRole, AuditAction
    │   ├── dto/
    │   │   ├── request/       # Inbound request payloads
    │   │   └── response/      # Outbound response payloads
    │   ├── security/          # JWT provider, filter, user details service
    │   ├── config/            # Spring Security and app bean config
    │   └── exception/         # Custom exceptions and global exception handler
    ├── resources/
    │   └── application.yml             # App config (datasource, JWT, logging)
    └── test/
        ├── java/com/imali/banking/
        │   └── integration/            # @SpringBootTest integration tests
        │       ├── AuthIntegrationTest.java
        │       ├── AccountIntegrationTest.java
        │       └── TransactionIntegrationTest.java
        └── resources/
            └── application-test.yml    # H2 datasource config for test profile
```

---

## Engineering Highlights

- **Deadlock prevention** — transfers lock accounts in consistent UUID order so concurrent transfers never deadlock
- **Audit trail** — every transaction records the balance snapshot after it completes, enabling point-in-time reconstruction
- **Fraud detection** — rule-based engine flags suspicious activity without blocking the transaction
- **Log isolation** — audit logs use `REQUIRES_NEW` propagation, surviving even a failed parent transaction
