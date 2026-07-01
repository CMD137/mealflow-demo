# MealFlow Web

Vue 3 backstage UI for MealFlow. It follows the classic admin layout: left navigation, top bar, and a right-side business workspace.

## Stack

- Vue 3
- Vite
- TypeScript
- Vue Router
- Pinia
- Element Plus
- Axios

## Local Run

```powershell
cd meal-web
npm.cmd install
npm.cmd run dev
```

Open:

```text
http://localhost:5173/
```

The default dev API base is `/api`, proxied by Vite to:

```text
http://localhost:8080
```

When needed, copy `.env.example` to `.env.local` and change `VITE_API_BASE_URL`.

## Demo Login

Use the seeded backend account:

```text
phone: 13800000000
code: demo
```

The backend accepts any code for the demo login flow.

## Main Pages

- Dashboard: order and queue overview.
- Merchant: merchant profile and capacity settings.
- Catalog: categories, SKU maintenance, image upload, stock and shelf status.
- Orders: query orders, create a demo order, handle queued result, mock pay, cancel, and inspect details.
- Fulfillment: accept, meal-ready, picked-up, delivered.
- Queue: tickets and capacity tokens.
- Promotion: voucher lifecycle and user wallet.
- Employees/Roles: merchant employees and permission roles.
- Notifications: messages, deliveries, consumer records, recover and replay.
- Ops Events: order/payment/fulfillment outbox events and manual dispatch.

## Verification

```powershell
npm.cmd run build
```

The build runs `vue-tsc --noEmit` before Vite production bundling.
