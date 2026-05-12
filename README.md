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

### Idempotency keys

Every transfer request includes an `idempotencyKey` (client-generated UUID). If a request fails due to a network error and the client retries with the same key, the service returns the original result instead of executing a duplicate transfer. This makes safe retries possible without double-spending.

### Pessimistic locking

Wallet balances are read and written under `SELECT ... FOR UPDATE` locks. The lock acquisition order is deterministic (lower UUID first) to prevent deadlocks. Within a single `@Transactional` method, the state machine transitions atomically: `INITIALIZED -> PENDING -> COMPLETED` or `INITIALIZED -> PENDING -> FAILED`.

### Refunds create new transactions

Refunds are not status changes on the original transaction. A new transaction is created with `referenceTransactionId` pointing to the original, and the source/target wallets are swapped. This preserves an immutable audit trail: every debit has a corresponding credit entry.

### Architecture decisions

| Decision | Rationale |
|----------|-----------|
| **Idempotency-key dedup** | Clients can safely retry without duplicate transfers. The key is stored with a unique constraint |
| **Pessimistic write locks** | Prevents race conditions on balance updates. Locks acquired in ascending ID order to avoid deadlocks |
| **State machine enum** | `INITIALIZED -> PENDING -> COMPLETED/FAILED` with `canTransitionTo()` guards. Invalid transitions are rejected at compile-visible level |
| **Immutable refund trail** | Refunds create a new transaction instead of flipping a status. Both entries are preserved |
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
| POST | `/api/wallets` | Create a wallet (zero balance, EUR default) | Yes |
| GET | `/api/wallets/me` | Get the authenticated user's wallet | Yes |
| DELETE | `/api/wallets/{walletId}` | Delete a wallet (must be owner, balance must be zero) | Yes |
| POST | `/api/transactions` | Execute an atomic fund transfer (idempotent) | Yes |
| POST | `/api/transactions/{id}/refund` | Refund a completed transaction | Yes |
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

## Security Note

This service only holds the RSA **public** key. It can verify tokens but cannot sign them. The private key resides exclusively in the auth-service. For production, generate a dedicated 4096-bit keypair and distribute the public key via a secrets manager or mounted volume:

```bash
openssl genpkey -algorithm RSA -out keys/private.pem -pkeyopt rsa_keygen_bits:4096
openssl pkey -in keys/private.pem -pubout -out keys/public.pem
```

Never commit private keys to version control. The public key in `src/main/resources/keys/` is a dev-only default and must be overridden via `JWT_PUBLIC_KEY` in any non-local deployment.
