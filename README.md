# Payment System

A stateless payment microservice for atomic fund transfers between wallets. Integrates with the JWT Authentication System for cookie-based authentication and uses RS256 public key verification to validate requests without database calls on the hot path.

## Architecture

```
                    HTTPS (TLS)           HTTP                HTTP
+-----------+     /api/auth/*    +-----------+     +------------------+       +------------+
|  Browser  | -----------------> |   Nginx   | --> |  auth-service    | -->   | PostgreSQL |
|           |                    | (gateway) |     +------------------+       | (auth)     |
|           |                    |           |     +------------------+       +------------+
|           |     /api/*         |           |     |  payment-service |       +------------+
|           | -----------------> |           | --> |                  | -->   | PostgreSQL |
+-----------+                    +-----------+     +------------------+       | (payment)  |
     https://localhost               port 443              port 8080              5432/5433
```

Nginx terminates TLS and routes by path prefix. Auth-service and payment-service each have their own PostgreSQL database. The payment service never communicates with the auth service directly. It validates JWTs cryptographically using the shared RSA public key.

The entire stack runs inside a single Docker Compose file within this repository. The authentication JAR is pre-built and tracked in git, served via a minimal Dockerfile. No external repositories are required at runtime.

### Wallet types

Three wallet types are supported:

| Type | Behaviour |
|------|-----------|
| `PEER` | Standard user wallet. Can send and receive payments. Default when no type is specified |
| `MERCHANT` | Business wallet. **Receive-only** — cannot initiate transfers. Created explicitly with `{"type": "MERCHANT"}` |
| `SERVICE_FEE` | Internal platform wallet. Collects a configurable percentage fee on P2M transfers. **Cannot** be created via the API |

### P2M transfers and service fee

When a `PEER` wallet sends to a `MERCHANT` wallet, a service fee (default 2.5%) is deducted from the amount the merchant receives and credited to the `SERVICE_FEE` wallet:

```
Peer sends 100 → Merchant receives 97.50, Service fee wallet receives 2.50
```

The sender's balance is only checked against the transfer amount (not amount + fee) — the merchant bears the cost. Refunds of P2M transfers return the full amount to the peer; the fee is **not** reversed. This matches real-world payment processing where merchant service fees are non-refundable.

### Idempotency keys

Every transfer request includes an `idempotencyKey` (client-generated UUID). If a request fails due to a network error and the client retries with the same key, the service returns the original result instead of executing a duplicate transfer. This makes safe retries possible without double-spending.

### Pessimistic locking

Wallet balances are read and written under `SELECT ... FOR UPDATE` locks. The lock acquisition order is deterministic (lower UUID first) to prevent deadlocks. Within a single `@Transactional` method, the state machine transitions atomically: `INITIALIZED -> PENDING -> COMPLETED` or `INITIALIZED -> PENDING -> FAILED`. For P2M transfers the `SERVICE_FEE` wallet is locked as a third participant after source and target.

### Refunds create new transactions

Refunds are not status changes on the original transaction. A new transaction is created with `referenceTransactionId` pointing to the original, and the source/target wallets are swapped. This preserves an immutable audit trail: every debit has a corresponding credit entry.

Only P2M (`PEER→MERCHANT`) transactions can be refunded — the merchant bears the full amount including the non-refunded fee. P2P refunds are rejected since there is no merchant relationship to hold responsible.

### Architecture decisions

| Decision | Rationale |
|----------|-----------|
| **Idempotency-key dedup** | Clients can safely retry without duplicate transfers. The key is stored with a unique constraint |
| **Pessimistic write locks** | Prevents race conditions on balance updates. Locks acquired in ascending ID order to avoid deadlocks |
| **State machine enum** | `INITIALIZED -> PENDING -> COMPLETED/FAILED` with `canTransitionTo()` guards. Invalid transitions are rejected at compile-visible level |
| **Immutable refund trail** | Refunds create a new transaction instead of flipping a status. Both entries are preserved |
| **P2M fee model** | Merchant pays the processing fee (deducted from received amount). Fee is non-refundable on P2M refunds |
| **Service fee wallet** | Internal `SERVICE_FEE` wallet collects fees. Auto-created on startup. API blocks manual creation |
| **RS256 JWT verification** | Tokens are validated using only the public key. No database lookup, no network call to auth-service |
| **Cookie-based auth** | `jwt_access_token` cookie is set by auth-service. This service only verifies; it never issues tokens |
| **Self-contained Dockerfile** | Maven build in stage 1, JRE runtime in stage 2. No external build tools needed at deploy time |
| **Shared RSA key mount** | Docker Compose generates fresh keypair each deploy via a `keygen` service. Both auth and payment mount the same `./keys` volume |

### Stateless vs stateful endpoints

| Endpoint | DB access | Why |
|----------|-----------|-----|
| `POST /api/wallets` | Yes | Must persist new wallet |
| `GET /api/wallets/me` | Yes | Must query wallet by user ID |
| `DELETE /api/wallets/{id}` | Yes | Must lock and delete wallet |
| `POST /api/transactions` | Yes | Must lock wallets, update balances, persist transaction |
| `POST /api/transactions/{id}/refund` | Yes | Must look up original transaction and create refund |
| `GET /api/transactions/{id}` | Yes | Must read transaction from DB |
| `GET /api/transactions?walletId=` | Yes | Must query transactions by wallet |
| All JWT validation | **No** | Token is verified cryptographically with the RSA public key |

## Tech Stack

- **Java 17**
- **Spring Boot 4.x**
- **Spring Security**
- **Spring Data JPA**
- **PostgreSQL**
- **Nginx** (API gateway, TLS termination)
- **Docker & Docker Compose**
- **Lombok**
- **JJWT** (Java JWT)
- **Springdoc OpenAPI** (Swagger UI)

## Getting Started

### Prerequisites

- Docker & Docker Compose
- mkcert (for local HTTPS certificates)

### Quick start

```bash
docker compose up --build
```

The API will be available at `https://localhost`.

If TLS certificates are missing, generate them with:

```bash
mkcert -install
mkdir -p certs
mkcert -key-file certs/localhost-key.pem -cert-file certs/localhost.pem localhost 127.0.0.1 ::1
```

### Directory layout

```
payment-system/
├── auth-service/
│   ├── Dockerfile            # Runs the pre-built auth JAR
│   └── app.jar               # Auth JAR (pre-built, tracked in git)
├── certs/                    # TLS certificates (gitignored)
├── keys/                     # Shared RSA keypair (generated, gitignored)
├── docker-compose.yml        # Full-stack orchestrator
├── nginx.conf                # Path-based routing config
├── Dockerfile                # Payment service Dockerfile
├── README.md
└── ...
```

### API Documentation

Once the application is running, access the Swagger UI:

| URL | Service |
|-----|---------|
| `https://localhost/swagger-ui/index.html` | Payment system API |
| `https://localhost/api/auth/swagger-ui/index.html` | Auth system API |

## API Endpoints

| Method | Endpoint | Description | Auth Required |
|--------|----------|-------------|---------------|
| POST | `/api/wallets` | Create a wallet (zero balance, EUR default). Accepts optional `{"type": "PEER"}` or `{"type": "MERCHANT"}` body; defaults to `PEER` | Yes |
| GET | `/api/wallets/me` | Get the authenticated user's wallet | Yes |
| DELETE | `/api/wallets/{walletId}` | Delete a wallet (must be owner, balance must be zero) | Yes |
| POST | `/api/transactions` | Execute an atomic fund transfer (idempotent). Returns 400 if merchant initiates a send | Yes |
| POST | `/api/transactions/{id}/refund` | Refund a completed P2M transaction. Returns 400 if P2P or already refunded | Yes |
| GET | `/api/transactions/{id}` | Get a single transaction | Yes |
| GET | `/api/transactions?walletId=` | List transactions for a wallet | Yes |

## Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `JWT_PUBLIC_KEY` | Path to RSA public key (X.509 PEM) | `classpath:keys/public.pem` |
| `SPRING_DATASOURCE_URL` | PostgreSQL JDBC URL | `jdbc:postgresql://payment-db:5432/payment_system` |
| `SPRING_DATASOURCE_USERNAME` | Database username | `admin` |
| `SPRING_DATASOURCE_PASSWORD` | Database password | `password` |
| `SPRING_JPA_HIBERNATE_DDL_AUTO` | Schema generation strategy | `update` |
| `PAYMENT_P2M_FEE_PERCENTAGE` | Percentage deducted from P2M transfers as service fee | `2.5` |

## Security Note

This service only holds the RSA **public** key. It can verify tokens but cannot sign them. The private key resides exclusively in the auth-service. For production, generate a dedicated 4096-bit keypair and distribute the public key via a secrets manager or mounted volume:

```bash
openssl genpkey -algorithm RSA -out keys/private.pem -pkeyopt rsa_keygen_bits:4096
openssl pkey -in keys/private.pem -pubout -out keys/public.pem
```

Never commit private keys to version control. The public key in `src/main/resources/keys/` is a dev-only default and must be overridden via `JWT_PUBLIC_KEY` in any non-local deployment.
