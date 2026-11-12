# Lorong Tea

[English](#english) · [简体中文](#简体中文) · [MIT License](LICENSE)

## English

Lorong Tea is an independently developed Android app for milk tea ordering and store operations, built around two stores in Malaysia. Customers browse a store's menu, place pickup orders, and claim limited-time offers. Staff manage stock, prepare orders, confirm payment at the counter, and redeem pickup codes. The app uses Kotlin and Jetpack Compose with a Spring Boot API.

### Run locally

Install Docker with the Compose plugin. Copy `.env.example` to `.env` from the repository root. Generate `LORONG_JWT_SECRET` with `openssl rand -base64 32` and `LORONG_RABBITMQ_PASSWORD` with `openssl rand -hex 16`. Set `LORONG_DEMO_PASSWORD` to a password for the local test accounts.

```sh
cp .env.example .env
# Fill in the three values in .env before starting Compose.
docker compose up -d --build --wait
curl -fsS http://127.0.0.1:8080/api/v1/ping
```

Compose starts the backend, MySQL, Redis, and RabbitMQ. The backend loads sample menus and creates local test accounts using the configured password.

### Run the Android app

Open `android/` in Android Studio with JDK 17, Android SDK 36, and Build Tools 36.0.0. Run the `app` configuration on an Android emulator. The debug app connects to the local backend at `http://10.0.2.2:8080`.

### Customer and staff workflow

1. Sign in as `demo-customer`, select Bukit Bintang, and place a pickup order.
2. Sign in as `demo-bb-staff`, accept the order, mark it ready, and confirm payment at the counter.
3. Copy the pickup code from the customer's order details. Enter it under **Redeem pickup** as staff.
4. As the customer, switch to Petaling Jaya and claim the RM 6.90 offer. The app shows a receipt and opens the order once it is created.

MySQL stores orders and stock. Redis caches menus and limits incoming offer claims. RabbitMQ handles campaign order creation in the background.

[Verification](docs/verification.md) · [API and configuration](docs/reference.md) · [Campaign processing](ops/async-campaign/README.md)

## 简体中文

南巷茶铺是一款个人独立开发的 Android 奶茶点单与门店管理软件，以马来西亚的两家奶茶门店为场景。顾客可以查看菜单、下自提单和参加限时抢购。店员可以管理库存、接单制作、确认到店收款和核销取餐码。移动端使用 Kotlin 和 Jetpack Compose，后端使用 Spring Boot。

### 本地启动

安装 Docker 和 Compose 插件。在仓库根目录把 `.env.example` 复制为 `.env`。用 `openssl rand -base64 32` 生成 `LORONG_JWT_SECRET`，用 `openssl rand -hex 16` 生成 `LORONG_RABBITMQ_PASSWORD`，再给 `LORONG_DEMO_PASSWORD` 设置本地测试账号的密码。

```sh
cp .env.example .env
# 先在 .env 里填好上述三个值，再启动 Compose。
docker compose up -d --build --wait
curl -fsS http://127.0.0.1:8080/api/v1/ping
```

Compose 会启动后端、MySQL、Redis 和 RabbitMQ。后端会载入示例菜单，并使用配置的密码创建本地测试账号。

### 运行 Android 应用

用 Android Studio 打开 `android/`，准备 JDK 17、Android SDK 36 和 Build Tools 36.0.0，在模拟器上运行 `app` 配置。调试版通过 `http://10.0.2.2:8080` 连接本地后端。

### 顾客与店员操作流程

1. 用 `demo-customer` 登录，选择武吉免登店，下一个自提单。
2. 用 `demo-bb-staff` 登录，接单、确认出餐，并确认到店收款。
3. 在顾客的订单详情里复制取餐码，再用店员账号在「取茶核销」中输入。
4. 切回顾客账号，选择八打灵再也店，抢购 RM 6.90 的活动商品。应用会先显示受理回执，成单后打开订单详情。

MySQL 保存订单和库存。Redis 缓存菜单并对抢购请求限流。RabbitMQ 在后台处理活动订单。

[验证记录](docs/verification.md) · [接口和配置](docs/reference.md) · [异步成单](ops/async-campaign/README.md)
