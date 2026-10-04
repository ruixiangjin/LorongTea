# Lorong Tea

[简体中文](README.zh-CN.md)

Lorong Tea models a takeaway milk tea chain in Malaysia. The Spring Boot API serves the Bukit Bintang and Petaling Jaya stores. Customers place pickup orders, and staff prepare them and confirm payment at the store.

## Run locally

Install Docker with the Compose plugin. Copy `.env.example` to `.env`. Put the output of `openssl rand -base64 32` in `LORONG_JWT_SECRET` and choose a value for `LORONG_DEMO_PASSWORD`.

```sh
docker compose up -d --build --wait
curl -fsS http://127.0.0.1:8080/api/v1/ping
docker compose logs -f app
```

Flyway migrates MySQL on startup. The `local` profile loads sample menus and creates `demo-customer`, `demo-bb-staff`, `demo-bb-staff-2` and `demo-pj-staff` with the chosen demo password. Existing accounts retain their passwords. The `mysql-data` volume keeps orders across container rebuilds and `docker compose down`. MySQL applies the configured database password when it first initializes an empty volume.

To run the API from source with Temurin 21.0.12+8, export the same database password, JWT key and demo password in your shell, start only MySQL with `docker compose up -d --wait mysql`, and run `./mvnw spring-boot:run -Dspring-boot.run.profiles=local` from `backend/`.

## Redis and monitoring

Compose starts Redis for the menu cache and campaign rate limit. To enable the same behavior when running from source, also start the `redis` service and export `LORONG_REDIS_ENABLED=true`. The default rate is eight new claims per second per store and campaign, with a burst of four. A rate-limited request receives HTTP 429 and a `Retry-After` header. Previously committed receipts remain available during a Redis outage.

Start the monitoring profile with `docker compose --profile monitoring up -d --build --wait`. The dashboard shows campaign outcomes, HTTP latency, menu cache activity and MySQL pool usage. [Open the local Grafana dashboard](http://127.0.0.1:3000/d/lorong-tea-campaign/lorong-tea-campaigns)

## Android

Open `android/` in Android Studio with JDK 17, Android SDK 36 and Build Tools 36.0.0. Start the backend as shown above, then run `app` on an Android emulator. The debug build connects to `http://10.0.2.2:8080`. Sign in with a local demo account.

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

## Campaign orders

Open the limited-time offer in either store's menu. The demo offer is RM 6.90 for one cup per customer at each store. Bukit Bintang starts with 50 offers and Petaling Jaya with 30. The local demo opens when it is first seeded and closes seven days later. Restarting keeps the original window and remaining quotas.

Use `GET /api/v1/stores/{storeId}/campaigns` to browse. Customers post a UUID `claimId` to `/api/v1/stores/{storeId}/campaigns/{campaignId}/claims` and receive the order ID. If the response is lost, check `GET /api/v1/promotion-claims/{claimId}` first. In Android, the pending request appears under **Orders**. Cancelling a pending campaign order returns the cup to shared stock. The used offer and purchase limit stay in effect.

[Reproduce the synchronous campaign benchmark](benchmarks/sync-campaign/README.md)
