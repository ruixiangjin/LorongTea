# Delivery verification

## Default application

Start the [local stack](../README.md#run-locally) from a clean checkout with a fresh MySQL volume. Build the Android debug APK and follow the [customer and staff walkthrough](../README.md#customer-and-staff-workflow). To check persistence, recreate the app and MySQL containers under the same Compose project with `docker compose up -d --wait --force-recreate mysql app` and reopen both orders.

| Check | Verified behavior |
| --- | --- |
| Clean startup | Docker Compose built the backend, Flyway applied migrations V1–V8 to an empty MySQL volume, and the Android debug build succeeded. |
| Ordinary pickup order | A customer placed an order at Bukit Bintang. The assigned staff account accepted it, marked it ready, confirmed payment and redeemed the customer's pickup code. |
| Campaign orders | Standard Compose startup ran RabbitMQ and accepted the claim with `202`. Android opened the original order automatically after the paused broker resumed. The synchronous comparison returned `201` with the broker stopped. |
| Persistence | Both orders remained available after the app and MySQL containers were recreated. |
| Backend checks | One basic test and seventeen MySQL, Redis and RabbitMQ integration checks passed using the CI command. |

## Reproducible checks

| Check | Result | Reproduction |
| --- | --- | --- |
| Pending order queue | With 100,000 synthetic orders, MySQL examined 20 rows for each store's first page after indexing. The previous plans examined 3,516 and 10,369 rows, with the same returned pages. | [SQL, plans and samples](../benchmarks/staff-queue/README.md) |
| Redis admission | At the same 300-request load, 300 orders were created with rate limiting disabled. With rate limiting enabled, 246 were created and 54 requests received HTTP 429. Both stores reconciled. | [k6 scenario and reconciliation](../benchmarks/sync-campaign/README.md) |
| RabbitMQ recovery | The async flow verified re-delivery after rollback and lost acknowledgement, a single order on duplicate delivery, and stock and quota restoration on expiry. Both stores reconciled. | [Async setup and reconciliation](../ops/async-campaign/README.md) |

MySQL is the source of truth for inventory, quotas and orders. Redis provides the menu cache and store-level admission control. RabbitMQ delivers order creation events for asynchronous campaign orders. Payment is an in-store confirmation recorded by staff.

These results cover the workflows verified locally using the seeded data. The repository has a GitHub Actions workflow, and its backend checks ran locally. A public deployment, hosted CI run and production capacity assessment are outside this verification.
