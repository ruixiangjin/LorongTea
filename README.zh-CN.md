# 南巷茶铺

[English](README.md)

南巷茶铺以马来西亚连锁奶茶店为背景。Spring Boot 后端提供武吉免登和八打灵再也两家店的菜单。店员登录后可以管理本店售价、上下架状态和库存。

## 本地运行

使用 Eclipse Temurin 21.0.12+8 和 Docker Compose。在仓库根目录执行：

```sh
export LORONG_JWT_SECRET="$(openssl rand -base64 32)"
export LORONG_DEMO_PASSWORD='choose-a-local-password'
docker compose up -d --wait mysql
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

启动时，Flyway 初始化 MySQL。`local` 配置会载入示例菜单，并使用设定的演示密码创建 `demo-customer`、`demo-bb-staff`、`demo-bb-staff-2` 和 `demo-pj-staff`。已有账号保留原密码。

## 接口

后端地址为 `http://localhost:8080`。店员接口需要 `Authorization: Bearer <token>` 请求头。价格以马来西亚仙为单位，使用整数。

| 接口 | 用途 |
| --- | --- |
| `GET /api/v1/stores` 和 `GET /api/v1/stores/{storeId}/menu` | 浏览门店及菜单 |
| `POST /api/v1/auth/login` | 使用 `username` 和 `password` 登录，获取有效期为 30 分钟的令牌 |
| `GET /api/v1/staff/store` | 查看所属门店 |
| `GET /api/v1/staff/stores/{storeId}/menu-items` | 查看本店菜单和库存 |
| `PATCH /api/v1/staff/stores/{storeId}/menu-items/{productId}` | 修改 `priceCents` 或 `listed` |
| `POST /api/v1/staff/stores/{storeId}/stock-adjustments` | 提交 `operationId`（UUID）、`productId`、`delta` 和 `reason`，重试时沿用同一操作 ID |
| `GET /actuator/health/readiness` | 检查后端和数据库是否就绪 |
