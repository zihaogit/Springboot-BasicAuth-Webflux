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

---

## 🧪 Running Unit Tests

To run the complete unit test suite:

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
| `POST` | `/auths/reset-password` | Reset password using OTP code | None |
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

A pre-configured Postman Collection and Environment are included in the repository:
- `postman/Springboot-BasicAuth-Webflux.postman_collection.json`
- `postman/Local.postman_environment.json`

Import both files into Postman to test all endpoints locally.
