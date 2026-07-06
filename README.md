# Railio Shopping Agent

A chat-first AI shopping assistant. Users browse a product catalog and check out
through a simulated Iranian card flow (**Card → Expiry → CVV2 → OTP**) — all inside
a streaming chat with inline **product cards** and **payment forms**.

- **Backend** — Kotlin + Ktor + [Koog](https://docs.koog.ai) tool-based agent, Koin DI.
- **LLM** — Ollama local-first, fully swappable to a cloud model via env vars.
- **Frontend** — Next.js (App Router) responsive chat UI.
- **Everything** runs locally with Docker Compose.

---

## Architecture

Clean Architecture, single Gradle module, layered by package. Dependencies point
inward: `web → application → domain`, and `infrastructure → domain` (it implements
the domain's interfaces). The domain is pure Kotlin.

```
backend/src/main/kotlin/ai/railio/shop/
  domain/           Pure model + ports (Product, PaymentProvider, AgentEvent, …)
  application/      Use cases (CatalogService, PaymentService, ChatAgent port)
  infrastructure/   Adapters: JSON catalog, mock PSP, Koog agent, Ollama, Koin DI
  web/              Ktor routes (REST + chat SSE), DTOs, plugins
frontend/           Next.js chat UI (components, Zustand store, SSE client)
```

### Swap points (future-ready, not over-built)

Each is an interface with one implementation today; replace the implementation
without touching the domain:

| Concern            | Port (interface)     | Today                        | Later |
|--------------------|----------------------|------------------------------|-------|
| Catalog storage    | `CatalogRepository`  | `JsonCatalogRepository`      | Postgres/Exposed |
| Payments           | `PaymentProvider`    | `MockPaymentProvider`        | Real Shaparak/IPG |
| LLM                | `LlmExecutorFactory` | Ollama                       | OpenAI / any cloud |
| Conversation memory| `ConversationStore`  | `InMemoryConversationStore`  | Redis / DB |

### How chat, cards and payment forms interleave

`POST /api/chat` streams **Server-Sent Events**. A per-turn Koog `AIAgent` runs the
catalog + checkout **tools**; each tool pushes structured events (`product_cards`,
`payment_form`) into the stream while the assistant's prose streams as `token`
events. The frontend renders each event type inline in the conversation. The card
"Buy" button and the payment form also work directly against the REST payment
endpoints, so checkout is resilient to LLM tool-calling variance.

---

## Prerequisites

- JDK 21 (backend build uses the Gradle wrapper; toolchain auto-resolves)
- Node.js 22 (frontend)
- [Ollama](https://ollama.com) running locally, **or** use Docker Compose
- Docker + Docker Compose (for the containerized stack)

---

## Configuration

Copy `.env.example` to `.env` and adjust. Key variables:

| Variable            | Default                   | Purpose |
|---------------------|---------------------------|---------|
| `LLM_PROVIDER`      | `ollama`                  | `ollama` or `openai` |
| `LLM_MODEL`         | `qwen2.5:7b`              | Any pulled Ollama model, or an OpenAI model id |
| `OLLAMA_BASE_URL`   | `http://localhost:11434`  | Ollama server URL |
| `OPENAI_API_KEY`    | _(empty)_                 | Required when `LLM_PROVIDER=openai` |
| `MOCK_OTP`          | `12345`                   | OTP the mock PSP accepts |
| `AGENT_SECRET`      | `dev-secret-change-me`    | Reserved for future API auth |
| `CORS_ORIGINS`      | `http://localhost:3000`   | Allowed web origins (`*` for any) |
| `PORT`              | `8080`                    | Backend port |
| `NEXT_PUBLIC_API_URL` | `http://localhost:8080` | Backend URL the browser calls |

> Choose a model with solid tool-calling for the checkout flow (e.g. `qwen2.5:7b`
> or `llama3.1:8b`). Small models may mis-call tools.

---

## Run locally

**1. Backend + Ollama**

```bash
ollama pull qwen2.5:7b            # once
cd backend
./gradlew run                     # starts on http://localhost:8080
```

**2. Frontend**

```bash
cd frontend
cp .env.local.example .env.local
npm install
npm run dev                       # http://localhost:3000
```

## Run with Docker Compose

Brings up Ollama, pulls the model, then starts backend and frontend:

```bash
cp .env.example .env
docker compose up --build
# Frontend: http://localhost:3000   Backend: http://localhost:8080
```

The first run downloads the Ollama model into a named volume (`ollama-models`),
so subsequent starts are fast.

---

## API reference

| Method | Path                               | Description |
|--------|------------------------------------|-------------|
| GET    | `/health`                          | Liveness check |
| GET    | `/api/catalog?q=&category=&limit=` | List / search products |
| GET    | `/api/catalog/{id}`                | Product by id |
| GET    | `/api/categories`                  | Categories |
| POST   | `/api/chat`                        | Chat turn → SSE stream (body: `{sessionId, message}`) |
| POST   | `/api/payment/checkout`            | Start checkout (body: `{productId}`) |
| GET    | `/api/payment/{sessionId}`         | Payment session state |
| POST   | `/api/payment/{sessionId}/{step}`  | Submit `card` \| `expiry` \| `cvv2` \| `otp` \| `resend-otp` (body: `{value}`) |

**Chat SSE events** (each `data:` line is JSON with a `type` field):
`token`, `product_cards`, `payment_form`, `payment_result`, `error`, `done`.
The resolved session id is returned in the `X-Session-Id` response header.

---

## Testing

```bash
# Backend — domain + payment state machine + catalog search
cd backend && ./gradlew test

# Frontend — component tests (product card, payment steps)
cd frontend && npm run test
```

---

## Tech versions

Kotlin 2.3.21 · Ktor 3.5.0 · Koog 1.0.0 · Koin 4.2 (annotations/KSP) ·
Coroutines 1.11.0 · Next.js 15 · React 19 · Tailwind 3.

> Notes: the Koog `koog-ktor` plugin has no stable 1.0.0, so the agent is wired
> directly via `koog-agents`. Koin annotations use the KSP 2.3.1 compiler line
> (which pins `koin-core-annotations` 4.1.0) alongside Koin runtime 4.2 — no BOM,
> to avoid overriding the compiler's expected meta classes.
