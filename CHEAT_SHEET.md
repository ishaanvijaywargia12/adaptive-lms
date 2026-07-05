# Adaptive LMS - Project Cheat Sheet

This document serves as a comprehensive guide to understanding the Adaptive Learning Management System (LMS). It can be used as a study guide or provided as context to an AI assistant to explain the codebase.

## 🎯 What the Project is Doing
The **Adaptive LMS** is a modern, production-grade, multi-tenant learning platform. 
It supports multiple organizations (tenants) from a single deployment, where each organization gets its own database schema for strict data isolation. 
"Adaptive" refers to its ability to recommend courses to students based on their quiz performance using caching and matching algorithms. It also features real-time live video sessions, plagiarism detection for assignments, global search, and asynchronous event processing.

## 🛠️ Tech Stack
### Backend
*   **Java 17+** & **Spring Boot 3.2**: Core framework.
*   **Spring Data JPA / Hibernate**: Database ORM and multi-tenancy layer.
*   **Spring Security & OAuth2**: Resource server securing endpoints via JWTs.
*   **Spring WebSocket (STOMP)**: Real-time chat and WebRTC signaling.
*   **Spring Batch**: Scheduled reporting jobs.

### Infrastructure & Data Services
*   **PostgreSQL**: Relational database (using schema-based multi-tenancy).
*   **Redis**: Distributed caching (Lettuce client) and rate limiting.
*   **Apache Kafka**: Asynchronous event streaming (e.g., triggering search indexing, sending notifications).
*   **Elasticsearch**: High-performance full-text search and autocomplete for courses.
*   **MinIO**: S3-compatible object storage for videos, PDFs, and assignment submissions.
*   **Keycloak**: Identity and Access Management (IAM) server handling user logins and JWT minting.
*   **Docker Compose**: Complete container orchestration for local development.

### Frontend
*   **React 18** + **Vite**: UI framework and build tool.
*   **TypeScript**: Static typing.
*   **Tailwind CSS** + **shadcn/ui**: Styling and UI components.
*   **Zustand**: Global state management.
*   **React Query**: Server-state management and data fetching.
*   **Keycloak JS**: Frontend authentication adapter.

---

## 🧠 Major Concepts & Architecture

### 1. Schema-Based Multi-Tenancy
Instead of having a `tenant_id` column on every table (which is error-prone) or running separate databases entirely (which is expensive), this project uses **Schema-per-Tenant**. 
Whenever a request comes in, a filter extracts the `X-Tenant-ID` header (or subdomain) and sets it in a `TenantContext`. Hibernate intercepts all queries and dynamically switches the PostgreSQL schema before executing the SQL.

### 2. Event-Driven Architecture (CQRS Pattern)
When core entities (like Courses) are created or updated, the system doesn't immediately write them to Elasticsearch. Instead, it publishes an event to **Kafka**. A separate consumer listens to this topic and updates Elasticsearch. This decouples the primary transaction from the search index update, improving performance and reliability.

### 3. Adaptive AI Recommendations
The system analyzes a student's quiz scores to identify weak areas. It caches course metadata and tags in **Redis**, and quickly finds courses that target those weak areas, offering a personalized learning path.

### 4. Plagiarism Detection (TF-IDF Cosine Similarity)
When a student uploads a file for an assignment, the system uses Apache Tika to extract the raw text. It then calculates the Term Frequency-Inverse Document Frequency (TF-IDF) vectors for the text and compares it against other submissions using Cosine Similarity to generate a match percentage.

### 5. WebRTC Live Sessions
For live classes, the server acts as a **Signaling Server** using WebSockets (STOMP). It relays WebRTC SDP offers, answers, and ICE candidates between browsers so they can establish a direct peer-to-peer video connection.

---

## 📂 Major Files & Spotlight Code Snippets

### 1. Multi-Tenancy Interceptor
**File:** `backend/src/main/java/com/lms/tenant/TenantInterceptor.java`
**What it does:** Extracts the tenant ID from the JWT token or HTTP headers and stores it in the ThreadLocal context so Hibernate knows which schema to use for the current request.

```java
// Spotlight: Extracting Tenant ID and setting Context
@Override
public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
    String tenantId = request.getHeader("X-Tenant-ID");
    if (tenantId == null) {
        tenantId = "public"; // Default fallback
    }
    TenantContext.setTenantId(tenantId);
    return true;
}
```

### 2. Database Connection Routing
**File:** `backend/src/main/java/com/lms/tenant/MultiTenantConnectionProviderImpl.java`
**What it does:** The core Hibernate integration that physically alters the SQL connection schema before a query runs.

```java
// Spotlight: Switching PostgreSQL schema
@Override
public Connection getConnection(String tenantIdentifier) throws SQLException {
    Connection connection = dataSource.getConnection();
    try {
        if ("public".equals(tenantIdentifier)) {
            connection.setSchema("public");
        } else {
            connection.setSchema(tenantIdentifier);
        }
    } catch (SQLException e) {
        throw e; // Fails fast if tenant schema doesn't exist
    }
    return connection;
}
```

### 3. Kafka Event Consumer
**File:** `backend/src/main/java/com/lms/kafka/consumer/LmsEventConsumer.java`
**What it does:** Listens to Kafka topics asynchronously. When a course is published, it takes the event and indexes it into Elasticsearch for fast searching, ensuring idempotency (not processing the same message twice).

```java
// Spotlight: Kafka Listener
@KafkaListener(topics = "lms-events", groupId = "lms-consumer-group")
public void handleLmsEvent(String messagePayload) {
    // 1. Check idempotency table to prevent duplicate processing
    // 2. Parse event type
    // 3. If CoursePublishedEvent -> searchService.indexCourse(courseData)
}
```

### 4. Plagiarism Detection Logic
**File:** `backend/src/main/java/com/lms/module/plagiarism/service/PlagiarismService.java`
**What it does:** The core algorithm for checking document similarity.

```java
// Spotlight: Cosine Similarity Calculation
private double calculateCosineSimilarity(Map<String, Integer> freq1, Map<String, Integer> freq2) {
    Set<String> uniqueWords = new HashSet<>();
    uniqueWords.addAll(freq1.keySet());
    uniqueWords.addAll(freq2.keySet());

    double dotProduct = 0;
    double norm1 = 0;
    double norm2 = 0;

    for (String word : uniqueWords) {
        int v1 = freq1.getOrDefault(word, 0);
        int v2 = freq2.getOrDefault(word, 0);
        dotProduct += v1 * v2;
        norm1 += Math.pow(v1, 2);
        norm2 += Math.pow(v2, 2);
    }

    if (norm1 == 0 || norm2 == 0) return 0.0;
    return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
}
```

### 5. WebRTC WebSocket Controller
**File:** `backend/src/main/java/com/lms/module/live/controller/LiveSessionWebSocketController.java`
**What it does:** Routes WebRTC signaling messages between participants in a live session room.

```java
// Spotlight: STOMP Message Routing
@MessageMapping("/session/{sessionId}/signal")
public void handleSignaling(@DestinationVariable String sessionId, 
                            @Payload WebRtcSignal signal,
                            SimpMessageHeaderAccessor headerAccessor) {
    String senderId = headerAccessor.getUser().getName();
    
    // Relay the SDP offer/answer or ICE candidate to the specific target peer
    messagingTemplate.convertAndSendToUser(
        signal.getTargetUserId(), 
        "/queue/session/" + sessionId + "/signal", 
        signal
    );
}
```
