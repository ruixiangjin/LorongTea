# Synchronous campaign baseline

The fixed-rate scenario warms up with 20 claims across two stores, then submits 20 claims per second for 15 seconds. Each request uses a distinct demo customer and claim ID. Login completes before warm-up. Both stores have enough inventory and quota for the full run. The load generator checks every receipt for an order ID and measures elapsed client time through receipt validation with millisecond resolution.

## Reproduce locally

Use Temurin 21.0.12+8, Docker Compose and k6 1.5.0. From the repository root, start an isolated MySQL and a local JVM on separate ports.

```sh
export LORONG_DB_PORT=13317 LORONG_DB_PASSWORD="$(openssl rand -hex 16)"
export LORONG_JWT_SECRET="$(openssl rand -base64 32)"
export LORONG_DEMO_PASSWORD="$(openssl rand -hex 12)"
docker compose -p lorong-campaign-bench up -d --wait mysql
(cd backend && ./mvnw -B -DskipTests package && exec java -jar target/lorong-tea-0.1.0-SNAPSHOT.jar \
  --spring.profiles.active=local --server.port=18087) &
```

Once `curl -fsS http://127.0.0.1:18087/actuator/health/readiness` succeeds, seed and run from the same shell.

```sh
export BASE_URL=http://127.0.0.1:18087 DEMO_PASSWORD="$LORONG_DEMO_PASSWORD"
export CAMPAIGN_ID="$(docker compose -p lorong-campaign-bench exec -T mysql sh -c \
  'MYSQL_PWD="$MYSQL_PASSWORD" mysql -BN -u "$MYSQL_USER" "$MYSQL_DATABASE"' \
  < benchmarks/sync-campaign/seed.sql)"
k6 run --summary-export /tmp/lorong-campaign-result.json benchmarks/sync-campaign/claims.js
docker compose -p lorong-campaign-bench exec -T mysql sh -c \
  'MYSQL_PWD="$MYSQL_PASSWORD" mysql -B -u "$MYSQL_USER" "$MYSQL_DATABASE"' \
  < benchmarks/sync-campaign/reconcile.sql
```

A complete run leaves 160 orders, 160 claims, 160 units of stock used and 160 quota units used at each store, including warm-up. For another run, stop the benchmark JVM and remove the isolated project and its fixture volume with `docker compose -p lorong-campaign-bench down -v` before seeding again.

## Measured on 2026-10-03

Apple M4 with 24 GiB of RAM, native macOS Temurin 21.0.12+8 JVM, MySQL 8.4.11 in Docker Desktop's Linux ARM64 VM with 10 CPUs and about 7.75 GiB of RAM, and k6 1.5.0 on the same host.

| Request rate | Confirmed orders | Business rejections | Technical failures | Dropped iterations | Median | p95 | p99 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 20/s for 15 s | 300 | 0 | 0 | 0 | 15 ms | 24 ms | 40 ms |

[Measured metrics](result.json) and [database reconciliation](reconciliation.tsv) are available for comparison. This is a baseline at one arrival rate on the stated setup.

The Android walkthrough used both demo stores. At Bukit Bintang, the server committed a claim before its response was dropped. The customer reopened the app, signed in, and recovered the original order under **Orders**. Staff accepted it, marked it ready, confirmed in-store payment and redeemed the customer's pickup code. The same customer then placed a separate RM 6.90 order in Petaling Jaya.
