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

## Redis 与监控

Compose 默认启动 Redis，用于菜单缓存和活动限流。从源码运行时，可以额外启动 `redis` 服务，并设置 `LORONG_REDIS_ENABLED=true` 启用相同行为。默认按活动、按门店每秒放行 8 个新请求，允许瞬时突发 4 个。触发限流时返回 HTTP 429 和 `Retry-After`。Redis 不可用时仍可查询已成单的回执。

运行 `docker compose --profile monitoring up -d --build --wait`，即可在 Grafana 查看抢购结果、HTTP 延迟、菜单缓存和 MySQL 连接池。[打开本地 Grafana 面板](http://127.0.0.1:3000/d/lorong-tea-campaign/lorong-tea-campaigns)

## Android

在 Android Studio 打开 `android/`，使用 JDK 17、Android SDK 36 和 Build Tools 36.0.0。按上面的步骤启动后端，再在 Android 模拟器运行 `app`。调试版连接 `http://10.0.2.2:8080`，可用本地演示账号登录。

## 演示流程

1. 用 `demo-customer` 登录，选择武吉免登店，把炭焙乌龙鲜奶加入购物车并下单。
2. 用 `demo-bb-staff` 登录，在「本店订单」打开该订单，依次接单、确认出餐和确认收款。
3. 顾客打开订单详情复制取餐码，店员在「取茶核销」输入取餐码，确认订单已取餐。
4. 顾客选择八打灵再也店，从菜单打开「限时抢购」，抢购 RM 6.90 的活动商品并查看订单详情。

[验收结果与实验复现](docs/verification.md)

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

## 限时抢购

从任一门店的菜单打开限时活动。演示价为 RM 6.90，同一顾客在每家店可买一杯。武吉免登店有 50 个名额，八打灵再也店有 30 个名额。演示活动在首次载入时开放，七天后结束。重启后沿用原活动窗口和剩余配额。

用 `GET /api/v1/stores/{storeId}/campaigns` 查询活动。顾客向 `/api/v1/stores/{storeId}/campaigns/{campaignId}/claims` 提交 UUID 格式的 `claimId`，成单后返回订单 ID。若响应丢失，先查 `GET /api/v1/promotion-claims/{claimId}`。Android 用户可从「我的订单」找回待确认请求。取消待接活动单会回补共享库存，已经用掉的优惠名额和个人资格仍保留。

[同步抢购基线及复现步骤](benchmarks/sync-campaign/README.md)

## 异步抢购实验

默认启动仍使用同步抢购。要体验异步模式，在独立 Compose 项目设置 `LORONG_CAMPAIGN_ASYNC_ENABLED=true` 和 RabbitMQ 密码，再启用 `async` 配置。Android 收到 `202` 后会显示「已受理」，可以用原回执查看成单或超时回补结果。[启动和对账步骤](ops/async-campaign/README.md)
