# Spring Boot WebFlux IAM & Authentication Microservice

A production-ready reactive authentication and IAM microservice built with Spring Boot WebFlux, R2DBC (PostgreSQL), Redis, and FusionAuth integration.

## 🚀 Key Features

- **Reactive WebFlux Stack**: Non-blocking I/O throughout the request lifecycle.
- **Dual Persistence**:
  - **PostgreSQL + R2DBC**: Reactive persistence for user identities, roles, and webhook audit events.
  - **Redis 7 (Reactive)**: High-speed in-memory store for OTPs, verification codes, and password reset tokens with automatic TTL expiration.
- **FusionAuth Integration & Webhooks**: Supports external identity providers and transactional webhook consumption with idempotency guarantees.
- **Containerized & Kubernetes-Ready**:
  - Multi-stage `Dockerfile` with distroless/alpine runtime.
  - Kubernetes `Deployment` & `Service` for high availability.
  - Kubernetes `CronJob` specifications for automated database pruning and maintenance.

---

## 📋 API Endpoints Overview

| Method | Endpoint | Description | Access |
| :--- | :--- | :--- | :--- |
| `POST` | `/auths/register` | Register new user account | Public |
| `POST` | `/auths/verify-email` | Verify registration OTP code | Public |
| `POST` | `/auths/login` | Authenticate with credentials | Public |
| `POST` | `/auths/refresh` | Refresh expired JWT access token | Public |
| `POST` | `/auths/fp` | Request forgot password OTP | Public |
| `POST` | `/auths/reset-password` | Reset password using verified OTP | Public |
| `POST` | `/auths/social/login` | Initiate social login provider redirect | Public |
| `POST` | `/auths/social/callback` | Handle OAuth2/OIDC social callback | Public |
| `POST` | `/webhooks/fusionauth` | Ingest FusionAuth event webhooks | Signature Verified |
| `GET` | `/users?userId={id}` | Get user by ID | `USER` or `ADMIN` |
| `PUT` | `/users/update?userId={id}` | Update user details | `USER` or `ADMIN` |
| `DELETE` | `/users?userId={id}` | Delete user | `ADMIN` |

---

## ⏱️ Scheduled CronJobs & K9s Management

Automated database maintenance and cleanup tasks are managed via **Kubernetes CronJobs** and monitored through **K9s**:

| CronJob Name | Schedule | Target | Description |
| :--- | :--- | :--- | :--- |
| `cleanup-expired-otps` | Daily at 02:00 AM | `verification_otp` | Deletes expired verification OTPs & tokens older than 24h |
| `purge-webhook-events` | Weekly (Sun 03:00 AM) | `processed_webhook_events` | Purges idempotency webhook records older than 30 days |
| `cleanup-unverified-users` | Weekly (Sun 04:00 AM) | `users` | Prunes abandoned unverified accounts without active OTPs |

See [K8S_CRONJOB_GUIDE.md](file:///c:/Users/Laggerbomb/OneDrive/Documents/Default%20Project/Springboot-BasicAuth-Webflux/K8S_CRONJOB_GUIDE.md) for full deployment instructions, testing steps, and K9s operations.
