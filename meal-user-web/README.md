# MealFlow User H5

MealFlow 用户端移动 H5，覆盖浏览商家、点餐、购物车、优惠券、提交订单、排队提示、模拟支付、订单状态和通知消息。

## Stack

- Vue 3
- Vite
- Pinia
- Vue Router
- Axios

## Local Run

```bash
npm install
npm run dev
```

默认开发端口为 `5174`，Vite 会把 `/api` 代理到 `http://localhost:8080`。

如需直接指定后端地址：

```bash
cp .env.example .env.local
```

再设置：

```env
VITE_API_BASE_URL=http://127.0.0.1:8080
```

`.env.local` 不应提交到 Git，`.env.example` 应提交。

## Demo Account

- 手机号：`13800000000`
- 验证码：任意

## Main Pages

- `/` 首页和商家列表
- `/merchant/:merchantId` 商家点餐
- `/cart` 购物车
- `/checkout` 结算
- `/order-result` 下单结果
- `/orders` 订单列表
- `/orders/:orderId` 订单详情
- `/vouchers` 优惠券
- `/messages` 通知消息
- `/mine` 我的
