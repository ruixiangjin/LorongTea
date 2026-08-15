# 南巷茶铺

[English](README.md)

南巷茶铺是一个以马来西亚连锁奶茶店为背景的自提点单项目。当前 Spring Boot 后端支持查询武吉免登和八打灵再也两家门店，以及各店的菜单、售价和上架状态。

## 本地运行

使用 Eclipse Temurin 21.0.12+8 和 Docker Compose。从仓库根目录启动 MySQL，再用 `local` 配置启动后端。

```sh
docker compose up -d --wait mysql
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

启动时，Flyway 初始化数据库，`local` 配置会载入两家店的示例菜单。

## 接口

后端地址为 `http://localhost:8080`。

| 接口 | 用途 |
| --- | --- |
| `GET /api/v1/stores` | 查看营业门店 |
| `GET /api/v1/stores/{storeId}/menu` | 查看指定门店的菜单，价格以马来西亚仙计 |
| `GET /actuator/health/readiness` | 检查后端和数据库是否就绪 |
