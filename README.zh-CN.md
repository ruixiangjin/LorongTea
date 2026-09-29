# 南巷茶铺

[English](README.md)

南巷茶铺以马来西亚连锁奶茶店为背景。Spring Boot API 服务武吉免登和八打灵再也两家门店。顾客可以下自提单，店员负责制作、到店收款和取餐核销。

## 本地运行

安装 Docker 和 Compose 插件。将 `.env.example` 复制为 `.env`，把 `openssl rand -base64 32` 的输出填入 `LORONG_JWT_SECRET`，再为 `LORONG_DEMO_PASSWORD` 设置一个演示密码。

```sh
docker compose up -d --build --wait
curl -fsS http://127.0.0.1:8080/api/v1/ping
docker compose logs -f app
```

Flyway 在启动时迁移 MySQL。`local` 配置载入示例菜单，并用演示密码创建 `demo-customer`、`demo-bb-staff`、`demo-bb-staff-2` 和 `demo-pj-staff`。已有账号保留原密码。重建容器或执行 `docker compose down` 后，订单仍保存在 `mysql-data` 数据卷中。MySQL 首次初始化空卷时会使用配置的数据库密码。

若使用 Temurin 21.0.12+8 从源码启动 API，在终端导出同样的数据库密码、JWT 密钥和演示密码，执行 `docker compose up -d --wait mysql` 启动 MySQL，再在 `backend/` 中执行 `./mvnw spring-boot:run -Dspring-boot.run.profiles=local`。

## Android

在 Android Studio 打开 `android/`，使用 JDK 17、Android SDK 36 和 Build Tools 36.0.0。按上面的步骤启动后端，再在 Android 模拟器运行 `app`。调试版连接 `http://10.0.2.2:8080`，可用本地演示账号登录。

## 接口

后端地址为 `http://localhost:8080`。订单和店员接口需要 `Authorization: Bearer <token>` 请求头。价格以马来西亚仙为单位，使用整数。

| 接口 | 用途 |
| --- | --- |
| `GET /api/v1/stores` 和 `GET /api/v1/stores/{storeId}/menu` | 浏览门店及菜单 |
| `POST /api/v1/auth/login` | 使用 `username` 和 `password` 登录，获取有效期为 30 分钟的令牌 |
| `GET /api/v1/staff/store` | 查看所属门店 |
| `GET /api/v1/staff/stores/{storeId}/menu-items` | 查看本店菜单和库存 |
| `PATCH /api/v1/staff/stores/{storeId}/menu-items/{productId}` | 修改 `priceCents` 或 `listed` |
| `POST /api/v1/staff/stores/{storeId}/stock-adjustments` | 提交 `operationId`（UUID）、`productId`、`delta` 和 `reason`，重试时沿用同一操作 ID |
| `GET /actuator/health/readiness` | 检查后端和数据库是否就绪 |

### 订单

顾客通过 `POST /api/v1/orders` 下单，请求头带 `Idempotency-Key`，请求体包含 `storeId`、`items` 和 `expectedTotalCents`。用 `GET /api/v1/orders` 查询历史，追加 `/{orderId}` 查看详情，或向 `/{orderId}/cancel` 发送 POST 取消订单。

店员通过 `GET /api/v1/staff/stores/{storeId}/orders?status=CREATED` 查看待接单队列。在店员订单路径下，向 `/{orderId}/start`、`/{orderId}/ready` 和 `/{orderId}/confirm-payment` 发送 POST 完成接单、出餐和收款确认。收款后向 `POST /api/v1/staff/stores/{storeId}/orders/redeem` 提交顾客的 `pickupToken`。
