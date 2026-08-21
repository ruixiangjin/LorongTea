# Lorong Tea

[简体中文](README.zh-CN.md)

Lorong Tea models a takeaway milk tea chain in Malaysia. The Spring Boot backend serves menus for Bukit Bintang and Petaling Jaya. Staff sign in to manage prices, listings and stock at their assigned store.

## Run locally

Use Eclipse Temurin 21.0.12+8 and Docker Compose. From the repository root:

```sh
export LORONG_JWT_SECRET="$(openssl rand -base64 32)"
export LORONG_DEMO_PASSWORD='choose-a-local-password'
docker compose up -d --wait mysql
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Flyway sets up MySQL on startup. The `local` profile loads sample menus and creates `demo-customer`, `demo-bb-staff`, `demo-bb-staff-2` and `demo-pj-staff` with the chosen demo password. Existing accounts retain their passwords.

## API

The backend listens on `http://localhost:8080`. Staff routes require an `Authorization: Bearer <token>` header. Prices use integer MYR cents.

| Endpoint | Description |
| --- | --- |
| `GET /api/v1/stores` and `GET /api/v1/stores/{storeId}/menu` | Browse stores and menus |
| `POST /api/v1/auth/login` | Exchange `username` and `password` for a 30-minute token |
| `GET /api/v1/staff/store` | Get the assigned store |
| `GET /api/v1/staff/stores/{storeId}/menu-items` | View the store menu and stock |
| `PATCH /api/v1/staff/stores/{storeId}/menu-items/{productId}` | Update `priceCents` or `listed` |
| `POST /api/v1/staff/stores/{storeId}/stock-adjustments` | Record a stock change with `operationId` (UUID), `productId`, `delta` and `reason`. Reuse the same ID when retrying |
| `GET /actuator/health/readiness` | Check whether the backend and database are ready |
