# Lorong Tea

[简体中文](README.zh-CN.md)

Lorong Tea models a takeaway milk tea chain in Malaysia. The Spring Boot backend currently lists its Bukit Bintang and Petaling Jaya stores and serves each store’s menu with its own prices and listing status.

## Run locally

Use Eclipse Temurin 21.0.12+8 and Docker Compose. From the repository root, start MySQL and run the backend with the `local` profile.

```sh
docker compose up -d --wait mysql
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Flyway sets up the database on startup. The `local` profile loads sample menus for both stores.

## API

The backend listens on `http://localhost:8080`.

| Endpoint | Description |
| --- | --- |
| `GET /api/v1/stores` | List active stores |
| `GET /api/v1/stores/{storeId}/menu` | Get a store’s menu. Prices are in MYR cents |
| `GET /actuator/health/readiness` | Check whether the backend and database are ready |
