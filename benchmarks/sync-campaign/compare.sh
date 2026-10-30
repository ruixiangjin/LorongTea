#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$root"
output="${1:?Usage: bash benchmarks/sync-campaign/compare.sh NEW_OUTPUT_DIRECTORY}"
mkdir "$output"
output="$(cd "$output" && pwd)"
project="lorong-campaign-compare-$(openssl rand -hex 4)"
compose=(docker compose -p "$project" --profile monitoring)
k6_bin="${K6_BIN:-k6}"

export LORONG_DB_PORT="${LORONG_DB_PORT:-13329}"
export LORONG_REDIS_PORT="${LORONG_REDIS_PORT:-16389}"
export LORONG_API_PORT="${LORONG_API_PORT:-18098}"
export LORONG_PROMETHEUS_PORT="${LORONG_PROMETHEUS_PORT:-19098}"
export LORONG_GRAFANA_PORT="${LORONG_GRAFANA_PORT:-13098}"
export LORONG_DB_PASSWORD="$(openssl rand -hex 16)"
export LORONG_JWT_SECRET="$(openssl rand -base64 32)"
export LORONG_DEMO_PASSWORD="$(openssl rand -hex 12)"
export LORONG_CAMPAIGN_MODE=sync
export LORONG_CAMPAIGN_PERMITS_PER_SECOND=8 LORONG_CAMPAIGN_BURST=4
export BASE_URL="http://127.0.0.1:$LORONG_API_PORT" DEMO_PASSWORD="$LORONG_DEMO_PASSWORD"
mysql='MYSQL_PWD="$MYSQL_PASSWORD" mysql -B -u "$MYSQL_USER" "$MYSQL_DATABASE"'

cleanup() {
    "${compose[@]}" down --volumes >> "$output/compose.log" 2>&1
}
trap cleanup EXIT

for enabled in false true; do
    export LORONG_CAMPAIGN_RATE_LIMIT_ENABLED="$enabled" EXPECT_RATE_LIMIT="$enabled"
    name=unlimited
    if [[ "$enabled" == true ]]; then name=limited; fi

    echo "Starting $name comparison"
    "${compose[@]}" up -d --build --wait --wait-timeout 120 mysql redis app prometheus grafana >> "$output/compose.log" 2>&1
    CAMPAIGN_ID="$("${compose[@]}" exec -T mysql sh -c "$mysql -N" < benchmarks/sync-campaign/seed.sql)"
    export CAMPAIGN_ID
    "$k6_bin" run --quiet --summary-export "$output/$name.json" benchmarks/sync-campaign/claims.js \
        > "$output/$name.log" 2>&1
    "${compose[@]}" exec -T mysql sh -c "$mysql" \
        < benchmarks/sync-campaign/reconcile.sql > "$output/$name.tsv"
    curl -fsS -G --data-urlencode 'query=max_over_time(hikaricp_connections_pending[1m])' \
        "http://127.0.0.1:$LORONG_PROMETHEUS_PORT/api/v1/query" > "$output/$name-pool.json"
    cleanup
done
trap - EXIT

python3 - "$output" <<'PY'
import csv
import json
from pathlib import Path
import sys

directory = Path(sys.argv[1])
for name in ("unlimited", "limited"):
    metrics = json.loads((directory / f"{name}.json").read_text())["metrics"]
    created = metrics["created_orders"]["count"]
    limited = metrics["rate_limited"]["count"]
    with (directory / f"{name}.tsv").open() as data:
        stores = list(csv.DictReader(data, delimiter="\t"))
    assert len(stores) == 2
    for store in stores:
        assert len({store[key] for key in ("quota_used", "stock_used", "claims", "orders")}) == 1
    assert sum(int(store["orders"]) for store in stores) == created + 20
    assert created + limited == metrics["attempted_claims"]["count"]
    print(f"{name}: {created} created, {limited} limited, both stores reconciled")
PY
