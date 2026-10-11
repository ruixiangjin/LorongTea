# Async campaign experiment

The default app creates campaign orders synchronously. The experiment uses a separate Compose project, a durable RabbitMQ queue and one consumer. It shares the same API and Android app.

## Start an isolated project

From the repository root, run this block in a dedicated terminal. Use a fresh project name for a fresh demo database.

```sh
export LORONG_DB_PORT=13326 LORONG_REDIS_PORT=16386 LORONG_API_PORT=18086
export LORONG_RABBITMQ_PORT=5673 LORONG_RABBITMQ_UI_PORT=15673
export LORONG_CAMPAIGN_ASYNC_ENABLED=true
export LORONG_RABBITMQ_PASSWORD="$(openssl rand -hex 16)"
export LORONG_JWT_SECRET="$(openssl rand -base64 32)"
export LORONG_DEMO_PASSWORD="$(openssl rand -hex 12)"
docker compose -p lorong-i-demo --profile async up -d --build --wait
```

Keep the RabbitMQ password for subsequent starts of that project's volume. For the same debug APK on the Android emulator, stop the app service using port 8080 and set `LORONG_API_PORT=8080` before starting the experiment. After the experiment, stop its app and restart the original service.

## Follow a claim

A new async claim returns HTTP 202 with `status=ACCEPTED` and a `Location` for the receipt. The Android activity shows **Request accepted · Order pending**. **Check request status** reads the original receipt, and a confirmed order opens the usual order detail. The original ID stays in Room until there is a terminal receipt.

The database commits the stock and quota reservation with the Outbox event. The publisher uses a persistent message and checks both broker confirmation and routing. The consumer commits the order and claim status before acknowledging. When a reservation expires, recovery returns its stock and quota and closes the event. The receipt becomes `FAILED_RELEASED` with code `CAMPAIGN_RESERVATION_EXPIRED`. The default reservation deadline is 300 seconds. The Android customer can submit a fresh claim after release.

Stop RabbitMQ during an isolated experiment to observe a pending receipt. Start it again to resume delivery. Run the reconciliation after the pending reservations have settled.

```sh
docker compose -p lorong-i-demo --profile async exec -T mysql sh -c \
  'MYSQL_PWD="$MYSQL_PASSWORD" mysql -B -u "$MYSQL_USER" "$MYSQL_DATABASE"' \
  < ops/async-campaign/reconcile.sql
```

The SQL uses the local demo opening stock, stock adjustment history, orders that still consume stock and outstanding reservations. Quota counts all created campaign orders, including cancelled ones. The Prometheus endpoint exposes pending reservation and Outbox counts plus publish, consumer and release outcomes.

Drain pending reservations before switching an existing experiment database back to synchronous mode. Start the default stack with `LORONG_CAMPAIGN_ASYNC_ENABLED=false`. The RabbitMQ verification group can run locally with `./mvnw -B verify -Dit.test=CampaignAsyncIT` from `backend/`.

## Local verification

With RabbitMQ paused, the emulator displayed the accepted receipt and an identical retry returned 202. After RabbitMQ resumed, the same receipt opened the original RM 6.90 order. In a second-store outage, the verification moved the reservation deadline into the past and let the scheduler perform the release. A fresh claim then created one order. The two stores had zero pending reservations and Outbox events. [Reconciliation result](reconciliation.tsv)

The broker tests also exercised a mandatory return, retry after an outage, rollback with a delivery left unacknowledged, commit followed by a lost acknowledgement, and a concurrent release. With RabbitMQ stopped and async mode disabled, an ordinary order and a campaign order both returned 201.
