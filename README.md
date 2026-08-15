# 南巷茶铺 · Lorong Tea

Lorong Tea is a takeaway tea shop demo for Bukit Bintang and Petaling Jaya.

## Backend

Use Eclipse Temurin 21.0.12+8 and the Maven Wrapper in `backend/`.
The wrapper downloads Apache Maven 3.9.16 on first use.
Start Docker before running the integration tests or the local database.
`./mvnw -B test` runs the HTTP unit tests. `./mvnw -B verify` also
starts a disposable MySQL 8.4.11 instance and checks Flyway and the store mapper.

```sh
docker compose up -d --wait mysql
cd backend
./mvnw -B verify
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

`GET http://localhost:8080/api/v1/ping` returns `{"data":{"status":"pong"}}`.

Compose publishes MySQL on `127.0.0.1:3307` and keeps the database in a named
volume. Set `LORONG_DB_PORT` or `LORONG_DB_PASSWORD` in the shell before
starting Compose and Spring Boot to override the local defaults. MySQL applies
the password during initial volume setup. Use `docker compose down` from
the project root to stop the database and keep its data.

## Store catalog

The `local` profile seeds two stores and three products per store on startup.
Restarting the backend preserves changes to existing demo rows.

| Request | Response |
| --- | --- |
| `GET /api/v1/stores` | Active stores with string IDs, codes and names |
| `GET /api/v1/stores/{storeId}/menu` | Product ID, SKU, name, price in MYR cents and listing status for that store |

For example, brown sugar boba is listed at RM 9.90 in Bukit Bintang and
unlisted at RM 10.50 in Petaling Jaya. The menu includes both listing states.

## API errors

Every response includes a server-generated UUID in `X-Request-ID`. Error
responses include the same ID in `requestId`, alongside `code` and `message`.

| Status | Code | Example |
| --- | --- | --- |
| 400 | `INVALID_REQUEST` | The store ID is malformed or is less than 1 |
| 404 | `STORE_NOT_FOUND` | The store is missing or inactive |
| 404 | `NOT_FOUND` | The API route does not exist |

## Health and request logs

```sh
curl -fsS http://localhost:8080/actuator/health/liveness
curl -fsS http://localhost:8080/actuator/health/readiness
curl -fsS http://localhost:8080/api/v1/ping
curl -fsS http://localhost:8080/actuator/metrics/http.server.requests
```

Liveness tracks the application lifecycle. Readiness also checks the database
connection. The Actuator endpoints are for local development in this phase.
Search the JSON console logs for the `requestId` returned by the API. Each
request completion records the HTTP method, matched route template, and status.

## Phase A build baseline

The version cutoff was 15 August 2026 in Malaysia. The build and integration
tests used the versions below. Maven Central timestamps for the resolved
dependencies and build artifacts were checked against that cutoff.

| Component | Verified version and release source |
| --- | --- |
| JDK | Eclipse Temurin 21.0.12+8 ([4 August 2026](https://adoptium.net/news/2026/08/eclipse-temurin-8u502-11032-17020-21012-2504-2602-available)) |
| Maven | Maven 3.9.16 ([13 May 2026](https://maven.apache.org/docs/history.html)) with Wrapper 3.3.4 ([8 September 2025](https://maven.apache.org/plugins/index.html)) |
| Backend | Spring Boot 4.1.0 ([10 June 2026](https://spring.io/blog/2026/06/10/spring-boot-4/)) and MyBatis-Plus 3.5.17 ([8 July 2026](https://central.sonatype.com/artifact/com.baomidou/mybatis-plus-spring-boot4-starter/3.5.17)) |
| Database | MySQL 8.4.11 with the [28 July 2026 multi-architecture image digest](https://github.com/docker-library/repo-info/blob/f67487c09ee0af8358204dda2a0eb0eb3a0c0f84/repos/mysql/remote/8.4.11.md) pinned in Compose and the integration tests |

The Spring Boot dependency management resolved Flyway 12.4.0, MySQL Connector/J
9.7.0, Hibernate Validator 9.1.0.Final, Micrometer 1.17.0, JUnit Jupiter 6.0.3,
and Testcontainers 2.0.5. The resolved MyBatis and MyBatis-Spring versions were
3.5.19 and 4.0.0. Run `./mvnw -B dependency:tree` from `backend/` to inspect
the resolved dependency graph.

Testcontainers used `testcontainers/ryuk:0.14.0` for test container cleanup,
observed at digest
`sha256:7c1a8a9a47c780ed0f983770a662f80deb115d95cce3e2daa3d12115b8cd28f0`.
