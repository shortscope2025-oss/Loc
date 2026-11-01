# Cloudflare Workers Free Backend Deployment Guide

This guide walks through deploying the serverless Cloudflare Worker API for **Together ❤️** without requiring any paid plans or billing accounts.

---

## 1. Prerequisites
- Free [Cloudflare Account](https://dash.cloudflare.com/)
- Node.js (v18+) installed locally
- Cloudflare Wrangler CLI (`npm install -g wrangler`)

---

## 2. Worker Directory
The worker source is located at:
```
backend/worker/
├── src/
│   └── index.ts
├── wrangler.toml
└── package.json
```

---

## 3. Deployment Steps

1. Open your terminal and navigate to the worker directory:
   ```bash
   cd backend/worker
   npm install
   ```

2. Login to your Cloudflare account via Wrangler:
   ```bash
   wrangler login
   ```

3. (Optional) Set Firebase credentials as encrypted Cloudflare Secrets:
   ```bash
   wrangler secret put FIREBASE_PROJECT_ID
   # Enter: together-cf667
   ```

4. Deploy the worker to Cloudflare's global edge network (Free tier):
   ```bash
   wrangler deploy
   ```

5. Once deployed, Wrangler will output your live URL:
   ```
   https://together-worker.<your-subdomain>.workers.dev
   ```

---

## 4. API Endpoints Overview
| Endpoint | Method | Description |
|---|---|---|
| `/api/health` | GET | System health check and status |
| `/api/location/update` | POST | Validates coordinates, filters GPS jitter (>50m), verifies timestamp |
| `/api/geofence/check` | POST | Evaluates 500m entry, 100m proximity, and 500m exit with hysteresis |
