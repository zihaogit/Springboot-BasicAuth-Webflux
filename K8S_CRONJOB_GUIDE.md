# 🚀 Kubernetes Deployment & CronJob Operations Guide

This guide details how the microservice is deployed in Kubernetes (K8s) as a continuously running Pod (`deployment.yaml`) and how scheduled maintenance tasks are triggered via lightweight `curl` CronJobs rather than heavy separate Java containers.

---

## 🏗️ Architecture Overview

```
                      ┌────────────────────────────────────────┐
                      │            Kubernetes Pod              │
                      │     (springboot-iam-app:latest)        │
                      │                                        │
                      │   Spring Boot WebFlux Microservice    │
                      │   Port: 8080 (ClusterIP Service)       │
                      │                                        │
                      │   Internal Job Endpoints:              │
                      │   • /internal/jobs/cleanup-otps        │
                      │   • /internal/jobs/purge-webhook-events│
                      │   • /internal/jobs/cleanup-unverified  │
                      └──────────────────▲─────────────────────┘
                                         │
                 ┌───────────────────────┼───────────────────────┐
                 │ HTTP POST             │ HTTP POST             │ HTTP POST
                 │ (curl)                │ (curl)                │ (curl)
        ┌────────┴────────┐     ┌────────┴────────┐     ┌────────┴────────┐
        │     CronJob     │     │     CronJob     │     │     CronJob     │
        │  cleanup-otps   │     │  purge-webhooks │     │  cleanup-users  │
        │ (Daily 2:00 AM) │     │ (Sun 3:00 AM)   │     │ (Sun 4:00 AM)   │
        └─────────────────┘     └─────────────────┘     └─────────────────┘
```

### Why This Design?
1. **Zero Cold-Start Lag**: Spring Boot does not need to boot up from scratch for each scheduled run.
2. **Minimal Resource Overhead**: Each CronJob uses a minimal alpine/curl container (~5MB RAM, runs in < 2 seconds).
3. **Database Connection Pool Reuse**: Database operations use the existing reactive R2DBC pool within the main running application.
4. **Centralized Logging & Observability**: Scheduled job logs appear in both K8s Job execution logs and the main application container logs.

---

## 📁 Kubernetes Manifests Summary

All Kubernetes resource definitions are located in the [`k8s/`](file:///c:/Users/Laggerbomb/OneDrive/Documents/Default%20Project/Springboot-BasicAuth-Webflux/k8s) folder:

| File | Resource Kind | Description |
| :--- | :--- | :--- |
| [`deployment.yaml`](file:///c:/Users/Laggerbomb/OneDrive/Documents/Default%20Project/Springboot-BasicAuth-Webflux/k8s/deployment.yaml) | Deployment & Service | Runs the core WebFlux microservice and exposes internal ClusterIP port 8080. Includes liveness/readiness probes. |
| [`secret.yaml`](file:///c:/Users/Laggerbomb/OneDrive/Documents/Default%20Project/Springboot-BasicAuth-Webflux/k8s/secret.yaml) | Secret Guide | Instructions for creating `springboot-iam-secrets` from local `.env` securely. |
| [`cronjob-cleanup-otps.yaml`](file:///c:/Users/Laggerbomb/OneDrive/Documents/Default%20Project/Springboot-BasicAuth-Webflux/k8s/cronjob-cleanup-otps.yaml) | CronJob | Triggers daily at 02:00 AM. Deletes OTP tokens older than 24 hours. |
| [`cronjob-purge-webhook-events.yaml`](file:///c:/Users/Laggerbomb/OneDrive/Documents/Default%20Project/Springboot-BasicAuth-Webflux/k8s/cronjob-purge-webhook-events.yaml) | CronJob | Triggers weekly on Sunday at 03:00 AM. Purges idempotency records older than 30 days. |
| [`cronjob-cleanup-unverified-users.yaml`](file:///c:/Users/Laggerbomb/OneDrive/Documents/Default%20Project/Springboot-BasicAuth-Webflux/k8s/cronjob-cleanup-unverified-users.yaml) | CronJob | Triggers weekly on Sunday at 04:00 AM. Prunes abandoned unverified user accounts. |

---

## 🛠️ Step-by-Step Deployment Instructions

### 1. Build and Tag the Docker Image

Run this from the project root directory:
```bash
docker build -t springboot-iam-app:latest ./springbootbasiclogin
```

> **Note**: For local clusters (like Minikube or Docker Desktop K8s), the image is available locally immediately (`imagePullPolicy: IfNotPresent`).

### 2. Create the Kubernetes Secret

Create the secret from your existing `.env` file:
```bash
kubectl create secret generic springboot-iam-secrets --from-env-file=.env
```

To verify secret creation:
```bash
kubectl get secret springboot-iam-secrets
```

### 3. Deploy the Microservice Pods & Service

Apply the deployment and ClusterIP service:
```bash
kubectl apply -f k8s/deployment.yaml
```

Check the status:
```bash
kubectl get deployments
kubectl get pods -l app=springboot-iam-app
kubectl get svc springboot-iam-service
```

### 4. Deploy the Maintenance CronJobs

Apply all three CronJob manifests:
```bash
kubectl apply -f k8s/cronjob-cleanup-otps.yaml
kubectl apply -f k8s/cronjob-purge-webhook-events.yaml
kubectl apply -f k8s/cronjob-cleanup-unverified-users.yaml
```

Verify active CronJobs:
```bash
kubectl get cronjobs
```

---

## 🧪 Testing CronJobs Manually

You do not need to wait for the scheduled time to verify that a CronJob works. You can trigger a manual Job execution from any CronJob specification at any time:

### Test OTP Cleanup Job
```bash
kubectl create job --from=cronjob/cleanup-expired-otps test-otp-cleanup-01
kubectl wait --for=condition=complete job/test-otp-cleanup-01 --timeout=60s
kubectl logs job/test-otp-cleanup-01
```

### Test Webhook Purge Job
```bash
kubectl create job --from=cronjob/purge-webhook-events test-webhook-purge-01
kubectl wait --for=condition=complete job/test-webhook-purge-01 --timeout=60s
kubectl logs job/test-webhook-purge-01
```

### Test Unverified Users Cleanup Job
```bash
kubectl create job --from=cronjob/cleanup-unverified-users test-user-cleanup-01
kubectl wait --for=condition=complete job/test-user-cleanup-01 --timeout=60s
kubectl logs job/test-user-cleanup-01
```

### Expected Output
The job logs will display the JSON response from the internal job endpoint, for example:
```json
{"status":"SUCCESS","job":"cleanup-otps","deletedRecords":3,"cutoffTime":"2026-10-04T02:00:00Z"}
```

---

## 📊 K9s Navigation & Management

When monitoring using **K9s**:

1. **Launch K9s**:
   ```bash
   k9s
   ```
2. **View Pods**:
   Type `:pods` and press Enter to see `springboot-iam-app` pods. Press `l` to view real-time streaming application logs.
3. **View CronJobs**:
   Type `:cronjobs` and press Enter.
   - Press `t` on any CronJob to trigger a manual run immediately.
   - Press `Enter` to see past executions (Jobs).
4. **View Jobs**:
   Type `:jobs` and press Enter to see completed or running executions. Press `l` to view the `curl` response and exit status.

---

## 🧹 Cleanup / Teardown

To delete all deployed K8s resources:
```bash
kubectl delete -f k8s/cronjob-cleanup-otps.yaml
kubectl delete -f k8s/cronjob-purge-webhook-events.yaml
kubectl delete -f k8s/cronjob-cleanup-unverified-users.yaml
kubectl delete -f k8s/deployment.yaml
kubectl delete secret springboot-iam-secrets
```
