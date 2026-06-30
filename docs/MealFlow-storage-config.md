# MealFlow 文件上传与对象存储配置

本文记录商品/菜品图片上传的后端配置方式，以及云存储密钥的本地管理约定。

## 当前能力

- `meal-catalog` 提供商家后台图片上传接口：`POST /catalog/admin/images`。
- 请求使用 `multipart/form-data`，文件字段名为 `file`。
- 支持的图片类型：`image/jpeg`、`image/png`、`image/webp`、`image/gif`。
- 默认最大文件大小为 5MB，可通过 `MEALFLOW_UPLOAD_MAX_SIZE_BYTES` 调整。
- 默认使用本地存储，返回 `/catalog/images/{objectKey}` 形式的访问地址。
- 可通过环境变量切换到阿里云 OSS，返回 OSS 公开访问地址或配置的 CDN/公网域名地址。

## 本地开发

复制 `.env.example` 为 `.env`，真实密钥只写入 `.env`：

```properties
MEALFLOW_STORAGE_PROVIDER=local
MEALFLOW_STORAGE_PUBLIC_BASE_URL=
```

本地上传文件默认写入 `uploads/catalog`。仓库已在 `.gitignore` 中忽略 `.env`、`.env.*` 和 `uploads/`，避免把密钥或上传文件提交到 git。

## 阿里云 OSS

启用 OSS 时设置：

```properties
MEALFLOW_STORAGE_PROVIDER=aliyun-oss
MEALFLOW_STORAGE_PUBLIC_BASE_URL=https://your-cdn-or-bucket-domain.example.com
ALIYUN_OSS_ENDPOINT=https://oss-cn-hangzhou.aliyuncs.com
ALIYUN_OSS_BUCKET=your-bucket
ALIYUN_OSS_ACCESS_KEY_ID=your-access-key-id
ALIYUN_OSS_ACCESS_KEY_SECRET=your-access-key-secret
ALIYUN_OSS_OBJECT_PREFIX=catalog
```

`MEALFLOW_STORAGE_PUBLIC_BASE_URL` 可选。配置后系统会用它拼接图片 URL；不配置时会按 `https://{bucket}.{endpoint}/{objectKey}` 生成默认地址。

## 前端状态

当前仓库没有新增正式前端。本阶段继续以补齐后端能力为主，图片上传接口已预留给后续用户点餐端和商家工作台使用。
