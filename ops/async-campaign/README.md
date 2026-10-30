# Asynchronous campaign orders

Start the [application stack](../../README.md#run-locally) to run MySQL, Redis, RabbitMQ and the backend. Keep the RabbitMQ password when reusing the broker volume.

## Follow a claim

A new async claim returns HTTP 202 with `status=ACCEPTED` and a `Location` for the receipt. The Android activity shows **Request accepted · Order pending**. The app checks the original receipt while the activity page is visible. **Check request status** also lets customers refresh manually. Once the order is created, the app opens the order detail. The original ID stays in Room until there is a terminal receipt.

The database commits the stock and quota reservation with the Outbox event. The publisher uses a persistent message and checks both broker confirmation and routing. The consumer commits the order and claim status before acknowledging. When a reservation expires, recovery returns its stock and quota and closes the event. The receipt becomes `FAILED_RELEASED` with code `CAMPAIGN_RESERVATION_EXPIRED`. Reservations expire after 300 seconds unless configured otherwise. The Android customer can submit a fresh claim after release.

Stop RabbitMQ in a separate Compose project to observe a pending receipt. Start it again to resume delivery. Run the reconciliation after the pending reservations have settled.

```sh
docker compose exec -T mysql sh -c \
  'MYSQL_PWD="$MYSQL_PASSWORD" mysql -B -u "$MYSQL_USER" "$MYSQL_DATABASE"' \
  < ops/async-campaign/reconcile.sql
```

The SQL uses the local demo opening stock, stock adjustment history, orders that still consume stock and outstanding reservations. Quota counts all created campaign orders, including cancelled ones. The Prometheus endpoint exposes pending reservation and Outbox counts plus publish, consumer and release outcomes.

## Synchronous comparison

Wait for existing async receipts to reach a terminal state before switching the same database. To run the synchronous mode with only its dependencies, use the following command.

```sh
LORONG_CAMPAIGN_MODE=sync docker compose up -d --build --wait mysql redis app
```

The RabbitMQ integration checks run in the normal CI workflow. To run just that group locally, use `./mvnw -B verify -Dit.test=CampaignAsyncIT` from `backend/`.

## Local verification

With RabbitMQ paused, the emulator displayed the accepted receipt and an identical retry returned 202. After RabbitMQ resumed, the app automatically followed the same receipt and opened the original RM 6.90 order. In a second-store outage, the verification moved the reservation deadline into the past and let the scheduler perform the release. A fresh claim then created one order. The two stores had zero pending reservations and Outbox events. [Reconciliation result](reconciliation.tsv)

The broker tests also exercised a mandatory return, retry after an outage, rollback with a delivery left unacknowledged, commit followed by a lost acknowledgement, and a concurrent release. With RabbitMQ stopped and async mode disabled, an ordinary order and a campaign order both returned 201.
