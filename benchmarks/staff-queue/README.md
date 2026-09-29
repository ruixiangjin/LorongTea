# Pending staff order queue benchmark

Run from the repository root with Python 3 and Docker.

```sh
python3 benchmarks/staff-queue/run.py /tmp/lorong-queue-baseline
```

The script creates a fresh MySQL 8.4.11 container with two CPUs, 2 GiB of memory and no external network. It applies migrations V1–V5 and generates 100,000 orders for 1,000 customers using a fixed seed. Store 1 has the larger share of all orders, while store 2 has the longer pending queue. Several orders share each minute, so the page exercises both `created_at` and `id` ordering.

`query.sql` mirrors the mapper's first pending page with all selected columns. The script writes the raw `EXPLAIN` and `EXPLAIN ANALYZE` output, ordered IDs, and 20 MySQL statement timings after five warm-up runs per store. It also records MySQL's rows examined. These timings describe server-side query work on the benchmark data.

Use a new output directory for each run. The container is removed on exit.

## Baseline results

MySQL 8.4.11 on Linux ARM64 in Docker Desktop, with the container capped at two CPUs and 2 GiB. Raw plans, samples, ordered IDs and the status distribution are in [results](results/).

| Store | Pending orders | Rows examined | Median (ms) | p95 (ms) |
| --- | ---: | ---: | ---: | ---: |
| Bukit Bintang | 3,496 | 3,516 | 1.9474 | 2.2151 |
| Petaling Jaya | 10,349 | 10,369 | 5.4312 | 5.5969 |

Both baseline plans read every matching order from `ix_orders_store_status` and sort before returning the first 20.
