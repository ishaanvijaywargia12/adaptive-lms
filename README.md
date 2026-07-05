# 🎓 Adaptive LMS

[![CI — Build & Test](https://github.com/ishaanvijaywargia12/adaptive-lms/actions/workflows/ci.yml/badge.svg)](https://github.com/ishaanvijaywargia12/adaptive-lms/actions/workflows/ci.yml)
[![CD — Docker Images](https://github.com/ishaanvijaywargia12/adaptive-lms/actions/workflows/cd.yml/badge.svg)](https://github.com/ishaanvijaywargia12/adaptive-lms/actions/workflows/cd.yml)
[![GitHub Pages](https://github.com/ishaanvijaywargia12/adaptive-lms/actions/workflows/deploy-pages.yml/badge.svg)](https://github.com/ishaanvijaywargia12/adaptive-lms/actions/workflows/deploy-pages.yml)

A **production-grade, multi-tenant Adaptive Learning Management System** built with modern enterprise Java + React. Features AI-powered doubt resolution (RAG + Qdrant), gamification with leaderboards, live WebRTC classrooms, quiz delivery with secure answer protection, and automated certificate generation.

**Live Demo:** [ishaanvijaywargia12.github.io/adaptive-lms](https://ishaanvijaywargia12.github.io/adaptive-lms)

---

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────┐
│                   GitHub Pages (CDN)                │
│              React 18 + Vite + TypeScript            │
│     Framer Motion · TanStack Query · Keycloak-JS    │
└──────────────────────┬──────────────────────────────┘
                       │ HTTPS API calls
┌──────────────────────▼──────────────────────────────┐
│              Spring Boot 3 (Render.com)             │
│   Multi-tenant (schema-per-tenant) · Kafka · Redis  │
│   Keycloak OAuth2 · WebSocket/STOMP · Actuator      │
└──┬──────┬──────┬──────┬──────┬──────────────────────┘
   │      │      │      │      │
  PG    Redis  Kafka  Qdrant  MinIO
(Neon) (Upstash)(Upstash)(Render)(Render)
```

### Tech Stack

| Layer | Technology |
|---|---|
| **Frontend** | React 18, Vite, TypeScript, TanStack Query, Framer Motion |
| **Backend** | Spring Boot 3.2, Java 17, Maven |
| **Auth** | Keycloak 23 (OAuth2 / OIDC), PKCE |
| **Database** | PostgreSQL 15 (schema-per-tenant multi-tenancy) |
| **Cache** | Redis 7 (leaderboards, session cache, RAG answer cache) |
| **Messaging** | Apache Kafka (KRaft mode, 16 topics) |
| **Vector DB** | Qdrant 1.9 (RAG embeddings, tenant-isolated) |
| **Storage** | MinIO S3-compatible (PDFs, videos, certificates) |
| **AI/RAG** | OpenAI text-embedding-ada-002 + gpt-4o-mini |
| **Real-time** | WebSocket/STOMP + WebRTC (getUserMedia) |
| **CI/CD** | GitHub Actions → GHCR → Render.com + GitHub Pages |
| **Containers** | Docker + Docker Compose (full local stack) |

---

## 🚀 Quick Start — Local Development

### Prerequisites
- Docker Desktop
- Node 20+ (`nvm use 20`)
- Java 17+

### 1. Clone & configure

```bash
git clone https://github.com/ishaanvijaywargia12/adaptive-lms.git
cd adaptive-lms
cp .env.example .env
# Edit .env and add your OPENAI_API_KEY (optional, only needed for AI Doubts)
```

### 2. Start all infrastructure services

```bash
cd infra
docker compose up -d postgres keycloak kafka redis qdrant minio elasticsearch kafka-init minio-init
# Wait ~60 seconds for Keycloak to initialize
```

### 3. Start the backend

```bash
cd backend
# Option A: Using Maven (requires Java 17 + Maven 3.9)
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Option B: Using Docker
docker compose -f ../infra/docker-compose.yml up backend
```

### 4. Start the frontend

```bash
cd frontend
npm install
npm run dev
# Open http://localhost:5173
```

### 5. Access services

| Service | URL |
|---|---|
| **Frontend (dev)** | http://localhost:5173 |
| **Backend API** | http://localhost:8080 |
| **Swagger UI** | http://localhost:8080/swagger-ui.html |
| **Keycloak Admin** | http://localhost:8180 (admin / admin123) |
| **MinIO Console** | http://localhost:9001 (minioadmin / minioadmin123) |

---

## 🌐 Free Cloud Deployment Guide

This project deploys entirely for free using:

| Service | For | Cost |
|---|---|---|
| **GitHub Pages** | React SPA hosting | Free forever |
| **GitHub Actions** | CI/CD automation | Free for public repos |
| **GitHub Container Registry** | Docker images | Free for public repos |
| **Render.com** | Spring Boot + Keycloak | Free (30s cold start) |
| **Neon.tech** | PostgreSQL | Free forever |
| **Upstash** | Redis + Kafka | Free forever |

**No credit card required anywhere.**

---

### Step 1 — Set up Neon.tech (PostgreSQL)

1. Go to [neon.tech](https://neon.tech) → Sign in with GitHub
2. Create a new project (free tier, no card needed)
3. Copy the **connection string** — looks like:
   ```
   postgresql://user:password@ep-xxx.us-east-2.aws.neon.tech/neondb?sslmode=require
   ```
4. Note it down for the GitHub Secrets step below.

---

### Step 2 — Set up Upstash (Redis + Kafka)

**Redis:**
1. Go to [console.upstash.com](https://console.upstash.com) → Sign in with GitHub
2. Create Redis → Region: US-East-1 → Free tier
3. Copy the **Redis URL** (starts with `rediss://`)

**Kafka:**
1. In the same Upstash dashboard → Kafka → Create Cluster → Free tier
2. Create topics matching the names in `infra/docker-compose.yml` (or let the backend auto-create them)
3. Copy:
   - **Bootstrap server** (e.g., `grizzly-xxx.upstash.io:9092`)
   - **Username** and **Password** for SASL auth

---

### Step 3 — Deploy to Render.com

1. Go to [render.com](https://render.com) → Sign in with GitHub
2. New → **Blueprint** → Connect this repository
3. Render reads `render.yaml` automatically and creates the services
4. For each service, set the environment variables manually in the Render dashboard:

**Backend (`adaptive-lms-backend`) env vars:**

| Variable | Value |
|---|---|
| `SPRING_DATASOURCE_URL` | Your Neon JDBC URL: `jdbc:postgresql://ep-xxx.../neondb?sslmode=require` |
| `SPRING_DATASOURCE_USERNAME` | From Neon |
| `SPRING_DATASOURCE_PASSWORD` | From Neon |
| `SPRING_DATA_REDIS_URL` | Your Upstash Redis URL (`rediss://...`) |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | `grizzly-xxx.upstash.io:9092` |
| `SPRING_KAFKA_PROPERTIES_SASL_JAAS_CONFIG` | `org.apache.kafka.common.security.scram.ScramLoginModule required username="xxx" password="xxx";` |
| `KEYCLOAK_AUTH_SERVER_URL` | `https://adaptive-lms-keycloak.onrender.com` |
| `APP_CORS_ORIGINS` | `https://ishaanvijaywargia12.github.io` |
| `APP_BASE_URL` | `https://adaptive-lms-backend.onrender.com` |

**Keycloak (`adaptive-lms-keycloak`) env vars:**

| Variable | Value |
|---|---|
| `KC_DB_URL` | Same Neon connection string but JDBC format |
| `KC_DB_USERNAME` | From Neon |
| `KC_DB_PASSWORD` | From Neon |
| `KEYCLOAK_ADMIN_PASSWORD` | Set a strong password |

5. After deploy, note the service URLs (e.g., `https://adaptive-lms-backend.onrender.com`)

---

### Step 4 — Configure GitHub Secrets

Go to your repo → **Settings → Secrets and variables → Actions → New repository secret**

Add these secrets:

| Secret | Value |
|---|---|
| `VITE_API_URL` | `https://adaptive-lms-backend.onrender.com/api` |
| `VITE_KEYCLOAK_URL` | `https://adaptive-lms-keycloak.onrender.com` |
| `VITE_KEYCLOAK_REALM` | `lms-demo` |
| `VITE_KEYCLOAK_CLIENT_ID` | `lms-frontend` |
| `VITE_WS_URL` | `wss://adaptive-lms-backend.onrender.com/ws` |
| `RENDER_BACKEND_DEPLOY_HOOK_URL` | From Render: Service → Settings → Deploy Hook |

---

### Step 5 — Enable GitHub Pages

1. Go to your repo → **Settings → Pages**
2. Source: **GitHub Actions**
3. Push any change to `frontend/` to trigger the first deploy
4. Your site will be live at `https://ishaanvijaywargia12.github.io/adaptive-lms`

---

## 📁 Project Structure

```
adaptive-lms/
├── .github/
│   └── workflows/
│       ├── ci.yml              # Build & test on every push
│       ├── cd.yml              # Build & push Docker images to GHCR
│       └── deploy-pages.yml    # Deploy frontend to GitHub Pages
├── backend/                    # Spring Boot application
│   ├── src/main/java/com/lms/
│   │   ├── module/             # Feature modules (quiz, course, gamification, etc.)
│   │   ├── kafka/              # Event producers & consumers
│   │   ├── tenant/             # Multi-tenancy (schema-per-tenant)
│   │   └── scheduler/          # Cron jobs (streak reset, leaderboard sync)
│   └── Dockerfile
├── frontend/                   # React SPA
│   ├── src/
│   │   ├── pages/              # Quiz, Leaderboard, LiveSessions, AiDoubts, etc.
│   │   ├── components/         # Shared UI components
│   │   ├── contexts/           # AuthContext, ThemeContext
│   │   └── lib/                # API client, Keycloak setup
│   └── Dockerfile
├── infra/
│   ├── docker-compose.yml      # Full local stack
│   ├── keycloak/               # Realm export for auto-import
│   └── nginx/                  # Reverse proxy config
├── k8s/                        # Kubernetes manifests (for future scale-up)
├── render.yaml                 # Render.com Blueprint (free cloud deploy)
└── README.md
```

---

## 🔑 Key Features

- **Multi-tenant**: Schema-per-tenant isolation — each school/org gets its own DB schema
- **Secure Quiz Delivery**: `isCorrect` fields stripped from API response until after submission
- **Gamification**: XP points, badges, streaks, weekly/all-time leaderboards backed by Redis sorted sets
- **AI Doubts (RAG)**: Upload PDFs → chunk → embed → store in Qdrant → GPT-4o-mini answers with citations
- **Live Sessions**: WebRTC local video preview + WebSocket/STOMP signaling + hand raise + live chat
- **Certificates**: iText 8 PDF generation with QR verification codes
- **Kafka Event Bus**: 16 topics, idempotent consumers, dead-letter queues
- **Keycloak PKCE**: OAuth2 with PKCE (no client secret in browser)

---

## 🧑‍💻 Development Commands

```bash
# Backend
mvn clean package -DskipTests       # Build jar
mvn spring-boot:run                 # Run locally
mvn test                            # Run unit tests

# Frontend
npm run dev                         # Dev server with HMR
npm run build                       # Production build
npx tsc --noEmit                    # Type check only

# Docker (full stack)
cd infra && docker compose up -d    # Start everything
docker compose logs -f backend      # Stream backend logs
docker compose down -v              # Teardown (removes volumes)
```

---

## 📄 License

MIT License — feel free to use this as a portfolio project template.
