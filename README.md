# Spring Boot WebFlux Authentication & Authorization Service

A reactive, non-blocking Spring Boot 3 application built with **Spring WebFlux**, **Spring Data R2DBC**, **Spring Security**, and **PostgreSQL**. Featuring a dual authentication system (**Basic Auth** and **JWT Access/Refresh Tokens**), role-based access control via custom AOP annotations (`@Authenticated`), Flyway database migrations, and MailDev integration for email/OTP verification.

---

## 🚀 Features

- **Reactive Core**: Pure non-blocking reactive stack using Spring WebFlux & Spring Data R2DBC.
- **Dual Authentication**: Supports both **HTTP Basic Authentication** and **JWT (JSON Web Tokens)**.
- **Token Management**: Issues short-lived Access Tokens and long-lived Refresh Tokens with a dedicated `/auths/refresh` endpoint.
- **Role-Based Authorization**: Fine-grained access control using a custom `@Authenticated(roles = {"ADMIN", "USER"})` aspect.
- **Email & OTP Verification**: Account email verification and password resets via 6-digit OTP codes.
- **Database Migrations**: Automatic schema and seed data migrations powered by **Flyway**.
- **Containerized Environment**: One-command setup for PostgreSQL 17 and MailDev via Docker Compose.
- **API Documentation**: Interactive Swagger UI (`/swagger-ui.html`).
- **Postman Ready**: Pre-configured Postman collection & environment files provided in the `postman/` directory.

---

## 🛠️ Tech Stack & Prerequisites

### Technologies
- **Java**: 21
- **Framework**: Spring Boot 3.x (WebFlux, Security, AOP, Validation)
- **Database**: PostgreSQL 17 + Spring Data R2DBC
- **Migrations**: Flyway
- **Email**: MailDev (SMTP testing server)
- **Build Tool**: Maven (`./mvnw`)

### Prerequisites
Ensure the following tools are installed on your machine:
- **Java 21 JDK**
- **Git**
- **Docker Desktop** (for running PostgreSQL and MailDev)
- **Postman** (optional, for API testing)

---

## ⚡ Quick Start

### 1. Clone the Repository
```bash
git clone https://github.com/zihaogit/Springboot-BasicAuth-Webflux.git
cd Springboot-BasicAuth-Webflux
```

### 2. Configure Environment & Start Docker Containers
Ensure your `.env` file exists at the root directory (or configure `docker-compose.yml`). Then start PostgreSQL and MailDev:

```bash
docker compose up -d
```

This will launch:
- **PostgreSQL Database**: `localhost:5432`
- **MailDev Web Interface**: [http://localhost:1080](http://localhost:1080)
- **MailDev SMTP Server**: `localhost:1025`

### 3. Run the Spring Boot Application
Navigate to the application folder and run using the Maven wrapper:

```bash
cd springbootbasiclogin
./mvnw spring-boot:run
```

The application will start on `http://localhost:8080`.

### 🔑 Default Credentials (Seeded)
| Username | Password | Role | Description |
| :--- | :--- | :--- | :--- |
| `admin` | `admin12345` | `ADMIN`, `USER` | Pre-seeded administrator account |
| `JohnDoe` | `kX9#mQ2$vL7p` | `USER` | Pre-seeded user account (John Doe) |

---

## 🧪 Running Tests

To run the complete unit and integration test suite:

```bash
cd springbootbasiclogin
./mvnw test
```

---

## 📖 API Documentation & Endpoints

### Swagger UI
Access the interactive OpenAPI / Swagger UI at:
👉 **[http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)**

### MailDev (Email / OTP Inbox)
Check sent verification emails and OTP codes at:
👉 **[http://localhost:1080](http://localhost:1080)**

---

### Key API Endpoints Summary

#### 🔑 Authentication (`/auths`)
| Method | Endpoint | Description | Auth Required |
| :--- | :--- | :--- | :--- |
| `POST` | `/auths/register` | Register a new user | None |
| `GET` | `/auths/verify-email` | Verify account email via OTP (`?verifyOTP=123456`) | None |
| `POST` | `/auths/login` | Login via JSON body or Basic Auth header -> returns JWT tokens | None / Basic Auth |
| `POST` | `/auths/refresh` | Obtain new access token using refresh token | None |
| `POST` | `/auths/fp` | Request password reset email (`?email=...`) | None |
| `POST` | `/auths/reset-password` | Reset password using `verificationToken` UUID (from forget-password email) | None |
| `GET` | `/auths/logout` | Invalidate session / Logout | `@Authenticated` |

#### 👤 User Management (`/users`)
| Method | Endpoint | Description | Role Required |
| :--- | :--- | :--- | :--- |
| `GET` | `/users/all` | Get all registered users | `ADMIN` |
| `GET` | `/users` | Get user details (`?userId=1`) | `USER`, `ADMIN` |
| `PUT` | `/users/update` | Update user details (`?userId=1`) | `USER`, `ADMIN` |
| `DELETE` | `/users` | Delete user (`?userId=1`) | `ADMIN` |

---

## 📬 Postman Collection

A pre-configured Postman Collection is included in the repository and also synced to Postman Cloud (**Webflux Assignment** collection):

- `postman/Springboot-BasicAuth-Webflux.postman_collection.json`

Import this file into Postman to test all endpoints locally.

### Collection Variables

| Variable | Default | Description |
| :--- | :--- | :--- |
| `baseUrl` | `http://localhost:8080` | Application base URL |
| `basicAuthUser` | `admin` | Login username (seeded admin) |
| `basicAuthPass` | `admin12345` | Login password (seeded admin) |
| `username` | `alice` | New user registration username |
| `password` | `secret123` | New user registration password |
| `email` | `alice@example.com` | New user registration email |
| `verifyOTP` | _(from logs)_ | 6-digit OTP from app console after Register |
| `verificationToken` | _(from logs)_ | UUID from app console after Forget Password |
| `accessToken` | _(from login)_ | JWT access token returned from Login |
| `refreshToken` | _(from login)_ | JWT refresh token returned from Login |
| `userId` | `16` | User ID for user-management endpoints (16 = John Doe) |
| `johnAuthUser` | `JohnDoe` | Seeded USER role account username |
| `johnAuthPass` | `kX9#mQ2$vL7p` | Seeded USER role account password |

### Step-by-Step Testing Workflow

1. **Start Docker containers** and the Spring Boot app (`./mvnw spring-boot:run`).
2. **Register** — send `POST /auths/register` with `username`, `password`, `role`, and `email`.
3. **Get OTP** — copy the 6-digit OTP printed in the app console (search for `verifyOTP=`).
4. **Update `verifyOTP`** collection variable with the OTP from step 3.
5. **Verify Email** — send `GET /auths/verify-email?verifyOTP={{verifyOTP}}`.
6. **Login** — send `POST /auths/login` (Basic Auth header). Copy `accessToken` and `refreshToken` into collection variables.
7. **Call user endpoints** using Basic Auth or `Authorization: Bearer {{accessToken}}`.
8. **Forget Password** — send `POST /auths/fp?email={{email}}`. Copy the UUID token from the app console.
9. **Update `verificationToken`** collection variable with the UUID.
10. **Reset Password** — send `POST /auths/reset-password` with `{ "verificationToken": "...", "password": "..." }`.
