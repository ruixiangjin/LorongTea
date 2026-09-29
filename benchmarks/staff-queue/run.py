#!/usr/bin/env python3
import argparse
from pathlib import Path
from statistics import median
import subprocess
import time
from uuid import uuid4

HERE = Path(__file__).resolve().parent
MIGRATIONS = HERE.parents[1] / "backend/src/main/resources/db/migration"
MYSQL_IMAGE = "mysql:8.4.11@sha256:b3b90af2a6552ae30c266fdb7d5dd55f3afb72404bb78d37fe8a23eb857fd3fb"
SAMPLE_COUNT = 20
WARMUP_COUNT = 5


def run(*command, sql=None):
    return subprocess.run(command, input=sql, text=True, stdout=subprocess.PIPE, check=True).stdout


def mysql(container, sql):
    return run("docker", "exec", "-i", container, "mysql", "--user=root",
               "--database=queue_benchmark", "--batch", "--comments", "--raw", "--skip-column-names", sql=sql)


def await_mysql(container):
    # Use TCP to wait for the final server, since the initialization server only listens on a socket.
    for _ in range(60):
        ready = subprocess.run(
            ["docker", "exec", container, "mysql", "--protocol=TCP", "--host=127.0.0.1",
             "--user=root", "queue_benchmark", "-e", "SELECT 1"],
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
        )
        if ready.returncode == 0:
            return
        time.sleep(2)
    raise RuntimeError("Benchmark MySQL did not become ready")


def measure(container, output, phase):
    results = {}
    for store_id in (1, 2):
        query = (HERE / "query.sql").read_text().format(store_id=store_id)
        prefix = output / f"{phase}-store-{store_id}"
        (prefix.with_suffix(".explain.txt")).write_text(
            "id\tselect_type\ttable\tpartitions\ttype\tpossible_keys\tkey\tkey_len\tref\trows\tfiltered\tExtra\n"
            + mysql(container, "EXPLAIN " + query)
            + "\nEXPLAIN ANALYZE\n" + mysql(container, "EXPLAIN ANALYZE " + query).rstrip("\n") + "\n"
        )
        results[store_id] = mysql(container, query)
        ids = [row.split("\t", 1)[0] for row in results[store_id].splitlines()]
        if len(ids) != 20:
            raise RuntimeError(f"Expected a full page for store {store_id}")
        prefix.with_suffix(".ids.txt").write_text("\n".join(ids) + "\n")

        # Read server statement timers in the same connection that ran the SELECTs.
        # A SQL comment tags the samples without changing the query plan.
        tagged_query = query.replace("SELECT id", "SELECT /* staff_queue_sample */ id", 1)
        timing_sql = (
            "SET @thread = PS_CURRENT_THREAD_ID();\n"
            "UPDATE performance_schema.setup_consumers SET ENABLED = 'YES' "
            "WHERE NAME = 'events_statements_history_long';\n"
            + query * WARMUP_COUNT
            + tagged_query * SAMPLE_COUNT
            + "SELECT CONCAT('sample:', TIMER_WAIT / 1000000000, ':', ROWS_EXAMINED) "
              "FROM performance_schema.events_statements_history_long "
              "WHERE THREAD_ID = @thread AND SQL_TEXT LIKE 'SELECT /* staff_queue_sample */ id%' "
              "ORDER BY EVENT_ID;\n"
        )
        samples = [line.split(":")[1:] for line in mysql(container, timing_sql).splitlines()
                   if line.startswith("sample:")]
        if len(samples) != SAMPLE_COUNT:
            raise RuntimeError(f"Expected {SAMPLE_COUNT} timed queries, got {len(samples)}")
        times = sorted(float(ms) for ms, _ in samples)
        examined = sorted({int(rows) for _, rows in samples})
        p95 = times[18]  # Nearest rank for 20 samples.
        prefix.with_suffix(".samples.tsv").write_text(
            "server_ms\trows_examined\n" + "".join(f"{ms}\t{rows}\n" for ms, rows in samples)
        )
        print(f"{phase} store {store_id}: median={median(times):.4f} ms, p95={p95:.4f} ms, "
              f"rows examined={examined}", flush=True)
    return results


def main():
    parser = argparse.ArgumentParser(description="Measure the pending staff queue on isolated MySQL.")
    parser.add_argument("output", type=Path, help="new directory for plans and samples")
    parser.add_argument("--index-migration", type=Path, help="apply this migration and repeat the measurements")
    args = parser.parse_args()
    args.output.mkdir()
    container = "lorong-queue-" + uuid4().hex[:10]
    run("docker", "run", "--rm", "-d", "--name", container, "--cpus", "2", "--memory", "2g",
        "--network", "none", "-e", "MYSQL_ALLOW_EMPTY_PASSWORD=yes", "-e",
        "MYSQL_DATABASE=queue_benchmark", MYSQL_IMAGE, "--default-time-zone=+00:00")
    try:
        await_mysql(container)
        for migration in sorted(MIGRATIONS.glob("V[1-5]__*.sql")):
            mysql(container, migration.read_text())
        mysql(container, (HERE / "seed.sql").read_text())
        environment = run("docker", "info", "--format", "{{.OSType}}/{{.Architecture}} engine {{.ServerVersion}}")
        environment += mysql(container, "SELECT VERSION(); SELECT store_id, status, COUNT(*) FROM orders "
                                        "GROUP BY store_id, status ORDER BY store_id, status;")
        (args.output / "environment.txt").write_text(environment)
        baseline = measure(container, args.output, "baseline")
        if args.index_migration:
            mysql(container, args.index_migration.read_text() + "\nANALYZE TABLE orders;")
            indexed = measure(container, args.output, "indexed")
            if baseline != indexed:
                raise RuntimeError("The indexed query changed the rows or their order")
            print("Both indexed pages match the baseline.", flush=True)
    finally:
        run("docker", "stop", container)


if __name__ == "__main__":
    main()
