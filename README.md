# 南巷茶铺 · Lorong Tea

Lorong Tea is a takeaway tea shop demo for Bukit Bintang and Petaling Jaya.

## Backend

Use Eclipse Temurin 21.0.12+8 and the Maven Wrapper in `backend/`.
The wrapper downloads Apache Maven 3.9.16 on first use.

```sh
cd backend
./mvnw -B verify
./mvnw spring-boot:run
```

`GET http://localhost:8080/api/v1/ping` returns `{"data":{"status":"pong"}}`.
