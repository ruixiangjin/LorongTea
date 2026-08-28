# Lorong Tea

[简体中文](README.zh-CN.md)

Lorong Tea models a takeaway milk tea chain in Malaysia. The Spring Boot API serves the Bukit Bintang and Petaling Jaya stores. Customers place pickup orders, and staff prepare them and confirm payment at the store.

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

The backend listens on `http://localhost:8080`. Order and staff routes require an `Authorization: Bearer <token>` header. Prices use integer MYR cents.

| Endpoint | Description |
| --- | --- |
| `GET /api/v1/stores` and `GET /api/v1/stores/{storeId}/menu` | Browse stores and menus |
| `POST /api/v1/auth/login` | Exchange `username` and `password` for a 30-minute token |
| `GET /api/v1/staff/store` | Get the assigned store |
| `GET /api/v1/staff/stores/{storeId}/menu-items` | View the store menu and stock |
| `PATCH /api/v1/staff/stores/{storeId}/menu-items/{productId}` | Update `priceCents` or `listed` |
| `POST /api/v1/staff/stores/{storeId}/stock-adjustments` | Record a stock change with `operationId` (UUID), `productId`, `delta` and `reason`. Reuse the same ID when retrying |
| `GET /actuator/health/readiness` | Check whether the backend and database are ready |

### Orders

Customers use `POST /api/v1/orders` with an `Idempotency-Key` and a body containing `storeId`, `items` and `expectedTotalCents`. Use `GET /api/v1/orders` for history and append `/{orderId}` for details or `/{orderId}/cancel` to cancel with POST.

Staff use `GET /api/v1/staff/stores/{storeId}/orders?status=CREATED` for the pending queue. Under the staff orders path, send POST requests to `/{orderId}/start`, `/{orderId}/ready` and `/{orderId}/confirm-payment`. After payment, send the customer’s `pickupToken` to `POST /api/v1/staff/stores/{storeId}/orders/redeem`.
