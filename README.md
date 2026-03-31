<div align="center">

# 🏦 Imali Banking API

**A production-style retail banking REST API — concurrency-safe, audit-compliant, fraud-aware.**

*Imali (isiZulu) — money.*

[![Java](https://img.shields.io/badge/Java_17-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://www.java.com)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot_3.2-6DB33F?style=for-the-badge&logo=spring-boot&logoColor=white)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-316192?style=for-the-badge&logo=postgresql&logoColor=white)](https://www.postgresql.org)
[![Docker](https://img.shields.io/badge/Docker-2496ED?style=for-the-badge&logo=docker&logoColor=white)](https://www.docker.com)
[![CI](https://github.com/KaraboVilakazi/imali-banking-api/actions/workflows/ci.yml/badge.svg)](https://github.com/KaraboVilakazi/imali-banking-api/actions/workflows/ci.yml)

*Full retail banking lifecycle — auth, accounts, deposits, withdrawals, transfers — with the real-world constraints a production system actually needs.*

</div>

---

## ⚡ Engineering Highlights

### Deadlock-Safe Concurrent Transfers
Two users transferring money to each other simultaneously is the classic deadlock scenario. Imali prevents it by always acquiring pessimistic locks on both accounts in consistent UUID order before touching any balance. The order is deterministic regardless of which direction the transfer flows — so threads never wait on each other in a cycle.

### Audit Log That Survives Failures
`AuditLogService` runs under `Propagation.REQUIRES_NEW` — its transaction commits independently of the parent. If a transfer fails and rolls back, the audit record of that attempt still exists. This mirrors real compliance requirements: the trail must be complete even when the operation wasn't.

### Balance Snapshots for Point-in-Time Reconstruction
Every `Transaction` record stores `balanceAfter` at the moment it completes. Account state at any point in history can be read directly from a single row — no need to replay the full transaction history.

### Non-Blocking Fraud Detection
Flagged transactions are not rejected. They proceed and are marked `flagged: true` with a `fraudReason`. This mirrors how real banking fraud systems work — quarantine for review, don't block the customer. A dedicated `FRAUD_FLAGGED` audit entry is written separately so the event is queryable independently of the transaction log.

---

## 🏗️ Architecture

```
Client
  │
  ▼
[JwtAuthenticationFilter]   ← validates Bearer token on every protected request
  │
  ▼
[Controllers]               ← AuthController / AccountController / TransactionController
  │
  ▼
[Services]
  ├── AccountService         ← Pessimistic locking, deadlock prevention
  ├── TransactionService     ← Fraud detection, balance snapshots
  └── AuditLogService        ← REQUIRES_NEW propagation — survives parent rollback
  │
  ▼
[PostgreSQL]                 ← users / accounts / transactions / audit_logs
```

```
src/java/com/imali/banking/
├── config/          # Security and app bean config
├── controller/      # REST controllers
├── domain/
│   ├── entity/      # User, Account, Transaction, AuditLog
│   └── enums/       # AccountType, AccountStatus, TransactionType, AuditAction
├── dto/             # Request and response payloads
├── exception/       # Custom exceptions + GlobalExceptionHandler
├── repository/      # Spring Data JPA repositories
├── security/        # JWT provider, filter, UserDetailsService
└── service/         # Business logic
```

---

## 🛠️ Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.2 |
| Security | Spring Security + JWT (JJWT) |
| Database | PostgreSQL |
| Containers | Docker & Docker Compose |
| Docs | Swagger UI (springdoc-openapi) |
| CI | GitHub Actions |
| Test DB | H2 (in-memory, test scope) |
| Build | Maven |

---

## 🧪 Test Suite

19 integration tests across three classes — all running against an H2 in-memory database, no external services required.

| Class | Coverage |
|---|---|
| `AuthIntegrationTest` | Register, login, duplicate email, validation errors, wrong password |
| `AccountIntegrationTest` | Create account, list accounts, get by ID, ownership enforcement |
| `TransactionIntegrationTest` | Deposit, withdraw, transfer, fraud flagging, insufficient funds, transaction history |

```bash
mvn test
```

---

## 🚀 Running Locally

**Docker only — no local Java or database setup needed.**

```bash
git clone https://github.com/KaraboVilakazi/imali-banking-api.git
cd imali-banking-api
docker compose up --build
```

API available at `http://localhost:8080`.
Swagger UI at `http://localhost:8080/swagger-ui/index.html`.

To stop:

```bash
docker compose down
```

To also remove the database volume:

```bash
docker compose down -v
```

---

## ☁️ Deploying to Railway

The fastest way to get a live URL.

1. Push the repo to GitHub (already done)
2. Go to [railway.app](https://railway.app) → **New Project** → **Deploy from GitHub repo**
3. Select `imali-banking-api`
4. Add a **PostgreSQL** plugin from the Railway dashboard
5. Set the following environment variables in Railway:

| Variable | Value |
|---|---|
| `SPRING_DATASOURCE_URL` | Railway provides this as `${{Postgres.DATABASE_URL}}` |
| `SPRING_DATASOURCE_USERNAME` | `${{Postgres.PGUSER}}` |
| `SPRING_DATASOURCE_PASSWORD` | `${{Postgres.PGPASSWORD}}` |
| `JWT_SECRET` | Any 64-character hex string |

6. Railway detects the `Dockerfile` automatically and builds on push.

---

## 📡 API Reference

All endpoints except `/api/v1/auth/**` require `Authorization: Bearer <token>`.

### Authentication

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/v1/auth/register` | Register a new user |
| `POST` | `/api/v1/auth/login` | Login and receive JWT |

### Accounts

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/v1/accounts` | Create a new account |
| `GET` | `/api/v1/accounts` | List my accounts |
| `GET` | `/api/v1/accounts/{id}` | Get account by ID |

`accountType` values: `CHEQUE`, `SAVINGS`, `FIXED_DEPOSIT`

### Transactions

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/v1/transactions/deposit` | Deposit funds |
| `POST` | `/api/v1/transactions/withdraw` | Withdraw funds |
| `POST` | `/api/v1/transactions/transfer` | Transfer between accounts |
| `GET` | `/api/v1/transactions/account/{accountId}` | Transaction history (paginated) |

**Transaction response:**
```json
{
  "id": "uuid",
  "type": "TRANSFER_DEBIT",
  "amount": 15000.00,
  "balanceAfter": 5000.00,
  "description": "Rent",
  "flagged": true,
  "fraudReason": "Large transaction: amount R15000.00 exceeds R10,000 threshold",
  "createdAt": "2026-03-31T10:30:00"
}
```

#### Fraud Detection Rules

| Rule | Threshold | Behaviour |
|---|---|---|
| Large transaction | Amount > R10,000 | Proceeds, `flagged: true` |
| Rapid transfers | > 3 transfers in 5 minutes from same account | Proceeds, `flagged: true` |

#### Audit Events

| Event | Trigger |
|---|---|
| `REGISTER` | New user registered |
| `LOGIN` | Successful login |
| `DEPOSIT` | Every deposit |
| `WITHDRAWAL` | Every withdrawal |
| `TRANSFER` | Every transfer |
| `FRAUD_FLAGGED` | Any fraud rule triggered |

---

## 📄 License

MIT
