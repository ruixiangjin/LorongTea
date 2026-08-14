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
./mvnw spring-boot:run
```

`GET http://localhost:8080/api/v1/ping` returns `{"data":{"status":"pong"}}`.

Compose publishes MySQL on `127.0.0.1:3307` and keeps the database in a named
volume. Set `LORONG_DB_PORT` or `LORONG_DB_PASSWORD` in the shell before
starting Compose and Spring Boot to override the local defaults. MySQL applies
the password during initial volume setup. Use `docker compose down` from
the project root to stop the database and keep its data.
