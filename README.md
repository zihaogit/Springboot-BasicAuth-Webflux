# Spring Boot WebFlux Auth & IAM Service

A reactive authentication and authorization service built with **Spring WebFlux**, **PostgreSQL (R2DBC)**, **JWT**, and **FusionAuth IAM** (supporting Google & Facebook OAuth2 social login).

---

## 🚀 Quick Start

### 1. Start Infrastructure (PostgreSQL, MailDev, FusionAuth)
```bash
docker compose up -d
```
- **PostgreSQL**: `localhost:5432`
- **MailDev (Email/OTP Inbox)**: [http://localhost:1080](http://localhost:1080)
- **FusionAuth Admin**: [http://localhost:9011](http://localhost:9011) (`fusionadmin@localhost.com` / `FusionAuth@2026!`)

### 2. Start Application
```bash
cd springbootbasiclogin
./mvnw spring-boot:run
```
- **API Base URL**: `http://localhost:8080`
- **Swagger UI**: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)

---

## 🔑 Default Seeded Credentials

| Username | Password | Role |
| :--- | :--- | :--- |
| `admin` | `admin12345` | `ADMIN`, `USER` |
| `JohnDoe` | `kX9#mQ2$vL7p` | `USER` |

---

## 📡 Key Endpoints

### 🔐 Authentication (`/auths`)
| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `POST` | `/auths/register` | Register new user |
| `GET` | `/auths/verify-email?verifyOTP={otp}` | Verify email with 6-digit OTP |
| `POST` | `/auths/login` | Login (Basic Auth header or JSON body) -> returns JWTs |
| `POST` | `/auths/refresh` | Exchange refresh token for new access token |
| `POST` | `/auths/fp?email={email}` | Request password reset token |
| `POST` | `/auths/reset-password` | Reset password using token |
| `GET` | `/auths/logout` | Invalidate token / Logout (`@Authenticated`) |

### 🌐 Social Login (Google & Facebook)
| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `GET` | `/auths/social/login?provider=google\|facebook` | Browser direct redirect to social login |
| `GET` | `/auths/social/url?provider=google\|facebook` | Returns authorization URL for frontend/apps |
| `GET` / `POST` | `/auths/social/callback` | OAuth2 callback, provisions user, returns JWTs |

### 👤 User Management (`/users`)
| Method | Endpoint | Description | Required Role |
| :--- | :--- | :--- | :--- |
| `GET` | `/users/all` | List all users | `ADMIN` |
| `GET` | `/users?userId={id}` | Get user by ID | `USER` or `ADMIN` |
| `PUT` | `/users/update?userId={id}` | Update user details | `USER` or `ADMIN` |
| `DELETE` | `/users?userId={id}` | Delete user | `ADMIN` |
