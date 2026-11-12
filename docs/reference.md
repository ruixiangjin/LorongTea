# Lorong Tea technical reference

[Quick start and Android demo](../README.md#run-locally)

## Local configuration

Flyway migrates MySQL on startup. The `local` profile loads sample menus and creates `demo-customer`, `demo-bb-staff`, `demo-bb-staff-2` and `demo-pj-staff` with the chosen demo password. Existing accounts retain their passwords. The `mysql-data` volume keeps orders across container rebuilds and `docker compose down`. MySQL applies the configured database password when it first initializes an empty volume.

To run the API from source with Temurin 21.0.12+8, export the same database and RabbitMQ credentials, JWT key and demo password in your shell, start the dependencies with `docker compose up -d --wait mysql redis rabbitmq`, and run `./mvnw spring-boot:run -Dspring-boot.run.profiles=local` from `backend/`.

## Redis and monitoring

Compose starts Redis for the menu cache and campaign rate limit. To enable the same behavior when running from source, also start the `redis` service and export `LORONG_REDIS_ENABLED=true`. The rate is eight new claims per second per store and campaign, with a burst of four. A rate-limited request receives HTTP 429 and a `Retry-After` header. Previously committed receipts remain available during a Redis outage.

Start the monitoring profile with `docker compose --profile monitoring up -d --build --wait`. The dashboard shows campaign outcomes, HTTP latency, menu cache activity and MySQL pool usage. [Open the local Grafana dashboard](http://127.0.0.1:3000/d/lorong-tea-campaign/lorong-tea-campaigns)

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

Use `GET /api/v1/stores/{storeId}/campaigns` to browse. Customers post a UUID `claimId` to `/api/v1/stores/{storeId}/campaigns/{campaignId}/claims` and receive `202 Accepted` after stock, quota and the Outbox event are saved. RabbitMQ delivers the event for order creation. Android checks the original receipt in the foreground and opens the order once it is created. If the response is lost, look up `GET /api/v1/promotion-claims/{claimId}`. The pending request appears under **Orders**. Cancelling a pending campaign order returns the cup to shared stock. The used offer and purchase limit stay in effect.

[Campaign order processing and recovery](../ops/async-campaign/README.md)

### Synchronous comparison

Set `LORONG_CAMPAIGN_MODE=sync` to create campaign orders in the request transaction. Use this mode to reproduce the [synchronous benchmarks](../benchmarks/sync-campaign/README.md). Finish processing pending receipts before switching a database to synchronous mode.
