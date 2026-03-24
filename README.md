# Imali Banking API

A production-grade RESTful banking API built with Spring Boot 3 and Java 17. Imali (meaning "money" in Zulu) provides core retail banking operations including user authentication, account management, and financial transactions — secured with JWT and backed by PostgreSQL.

---

## Table of Contents

- [Features](#features)
- [Tech Stack](#tech-stack)
- [Prerequisites](#prerequisites)
- [Getting Started](#getting-started)
  - [1. Clone the Repository](#1-clone-the-repository)
  - [2. Set Up PostgreSQL](#2-set-up-postgresql)
  - [3. Configure Environment Variables](#3-configure-environment-variables)
  - [4. Build and Run](#4-build-and-run)
- [Running the Tests](#running-the-tests)
- [API Reference](#api-reference)
  - [Authentication](#authentication)
  - [Accounts](#accounts)
  - [Transactions](#transactions)
- [Testing the Application with curl](#testing-the-application-with-curl)
- [Project Structure](#project-structure)

---

## Features

- User registration and login with JWT authentication (24-hour token expiry)
- Create and manage bank accounts (Cheque, Savings, Fixed Deposit)
- Deposit, withdraw, and transfer funds between accounts
- Paginated transaction history with full audit trail
- Concurrency-safe transfers using pessimistic locking and deadlock prevention
- Comprehensive error handling with descriptive HTTP responses
- Role-based access control (CUSTOMER / ADMIN)

---

## Tech Stack

| Layer        | Technology                        |
|--------------|-----------------------------------|
| Language     | Java 17                           |
| Framework    | Spring Boot 3.2.3                 |
| Security     | Spring Security + JJWT            |
| Persistence  | Spring Data JPA + Hibernate       |
| Database     | PostgreSQL                        |
| Build Tool   | Maven                             |
| Test DB      | H2 (in-memory, test scope only)   |

---

## Prerequisites

Make sure you have the following installed before running the application:

- **Java 17+** — [Download JDK](https://adoptium.net/)
- **Maven 3.8+** — [Download Maven](https://maven.apache.org/download.cgi)
- **PostgreSQL 13+** — [Download PostgreSQL](https://www.postgresql.org/download/)

Verify your installations:

```bash
java -version
mvn -version
psql --version
```

---

## Getting Started

### 1. Clone the Repository

```bash
git clone https://github.com/karabovilakazi/imali-banking-api.git
cd imali-banking-api
```

### 2. Set Up PostgreSQL

Start PostgreSQL and create the database:

```bash
psql -U postgres
```

```sql
CREATE DATABASE imali_db;
\q
```

Hibernate will automatically create the required tables on first startup (`ddl-auto: update`).

### 3. Configure Environment Variables

The application reads its database credentials and JWT secret from environment variables. Set the following before running:

```bash
export DB_USERNAME=postgres
export DB_PASSWORD=your_postgres_password
export JWT_SECRET=your_very_long_secret_key_at_least_32_characters
```

> **Note:** If you don't set these, the application falls back to the defaults defined in `application.yml` (`postgres` / `password`). For any non-local environment, always override these with strong values.

### 4. Build and Run

```bash
# Build the project (skips tests for a quick start)
mvn clean install -DskipTests

# Start the application
mvn spring-boot:run
```

The API will be available at: **http://localhost:8080**

You should see output like:
```
Started ImaliBankingApplication in X.XXX seconds
```

---

## Running the Tests

The test suite uses an H2 in-memory database, so no additional setup is required.

```bash
mvn test
```

---

## API Reference

All endpoints (except auth) require a `Bearer` JWT token in the `Authorization` header.

### Authentication

#### Register a new user

```
POST /api/v1/auth/register
```

**Request body:**
```json
{
  "firstName": "Karabo",
  "lastName": "Vilakazi",
  "email": "karabo@example.com",
  "password": "securepassword123"
}
```

**Response `201 Created`:**
```json
{
  "token": "<jwt-token>"
}
```

---

#### Login

```
POST /api/v1/auth/login
```

**Request body:**
```json
{
  "email": "karabo@example.com",
  "password": "securepassword123"
}
```

**Response `200 OK`:**
```json
{
  "token": "<jwt-token>"
}
```

---

### Accounts

All account endpoints require `Authorization: Bearer <token>`.

#### Create an account

```
POST /api/v1/accounts
```

**Request body:**
```json
{
  "accountType": "CHEQUE"
}
```

`accountType` must be one of: `CHEQUE`, `SAVINGS`, `FIXED_DEPOSIT`

---

#### List all accounts

```
GET /api/v1/accounts
```

---

#### Get account by ID

```
GET /api/v1/accounts/{accountId}
```

---

### Transactions

All transaction endpoints require `Authorization: Bearer <token>`.

#### Deposit

```
POST /api/v1/transactions/deposit
```

```json
{
  "accountId": "<account-uuid>",
  "amount": 1000.00
}
```

---

#### Withdraw

```
POST /api/v1/transactions/withdraw
```

```json
{
  "accountId": "<account-uuid>",
  "amount": 250.00
}
```

---

#### Transfer between accounts

```
POST /api/v1/transactions/transfer
```

```json
{
  "sourceAccountId": "<account-uuid>",
  "destinationAccountId": "<account-uuid>",
  "amount": 500.00
}
```

---

#### Get transaction history

```
GET /api/v1/transactions/account/{accountId}?page=0&size=20
```

Returns paginated transaction history. Default page size is 20.

---

## Testing the Application with curl

Here is a step-by-step walkthrough to verify everything is working from scratch.

### Step 1 — Register a user

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

Copy the `token` value from the response.

### Step 2 — Set your token as a variable

```bash
TOKEN="<paste-your-token-here>"
```

### Step 3 — Create a Cheque account

```bash
curl -s -X POST http://localhost:8080/api/v1/accounts \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"accountType": "CHEQUE"}' | jq .
```

Copy the `id` (account UUID) from the response.

### Step 4 — Deposit funds

```bash
ACCOUNT_ID="<paste-account-id-here>"

curl -s -X POST http://localhost:8080/api/v1/transactions/deposit \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"accountId\": \"$ACCOUNT_ID\", \"amount\": 1000.00}" | jq .
```

### Step 5 — Check your balance

```bash
curl -s http://localhost:8080/api/v1/accounts/$ACCOUNT_ID \
  -H "Authorization: Bearer $TOKEN" | jq .
```

You should see `"balance": 1000.00`.

### Step 6 — View transaction history

```bash
curl -s "http://localhost:8080/api/v1/transactions/account/$ACCOUNT_ID?page=0&size=10" \
  -H "Authorization: Bearer $TOKEN" | jq .
```

> **Tip:** `jq` is a lightweight JSON formatter. Install it with `sudo apt install jq` (Ubuntu/Debian) or `brew install jq` (macOS). It's optional — the API works fine without it.

---

## Project Structure

```
imali-banking-api/
├── pom.xml
└── src/
    └── main/
        ├── java/com/imali/banking/
        │   ├── ImaliBankingApplication.java
        │   ├── controller/        # REST controllers (Auth, Account, Transaction)
        │   ├── service/           # Business logic
        │   ├── repository/        # Spring Data JPA repositories
        │   ├── domain/
        │   │   ├── entity/        # JPA entities (User, Account, Transaction)
        │   │   └── enums/         # AccountType, AccountStatus, TransactionType, UserRole
        │   ├── dto/
        │   │   ├── request/       # Inbound request payloads
        │   │   └── response/      # Outbound response payloads
        │   ├── security/          # JWT provider, filter, user details service
        │   ├── config/            # Spring Security and app bean config
        │   └── exception/         # Custom exceptions and global exception handler
        └── resources/
            └── application.yml    # Application configuration
```

---

## License

This project is open source. See the repository for details.
