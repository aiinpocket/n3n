# N3N Technical Documentation

This document provides technical details for developers working on or integrating with N3N.

## Table of Contents

- [Architecture Overview](#architecture-overview)
- [Technology Stack](#technology-stack)
- [Backend Modules](#backend-modules)
- [API Reference](#api-reference)
- [Database Schema](#database-schema)
- [Development Guide](#development-guide)
- [Testing](#testing)

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────┐
│  Flow Editor (React + React Flow)                       │
│  src/main/frontend/                                     │
└────────────────────────┬────────────────────────────────┘
                         │ HTTP/WebSocket (proxied in dev)
┌────────────────────────▼────────────────────────────────┐
│  API Gateway (Spring Boot 4)                            │
│  - Flow API, Execution API, Registry API, WebSocket     │
└────────────────────────┬────────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────────┐
│  Flow Engine                                            │
│  - DAG Parser: Parses flow definitions into DAGs        │
│  - Scheduler: Orchestrates node execution               │
│  - State Manager: Redis-backed execution state          │
│  - Concurrency Manager: allow/deny/queue/replace modes  │
│  - Event Publisher: WebSocket notifications             │
└────────────────────────┬────────────────────────────────┘
                         │
         ┌───────────────┼───────────────┐
         ▼               ▼               ▼
   ┌─────────┐     ┌──────────┐    ┌──────────┐
   │  Redis  │     │PostgreSQL│    │K8s/Docker│
   │ State   │     │ Persist  │    │Components│
   └─────────┘     └──────────┘    └──────────┘
```

### Flow Execution Model

1. Client triggers flow via `POST /api/executions`
2. Engine checks concurrency constraints
3. DAG is parsed and root nodes are scheduled
4. Nodes execute asynchronously via Virtual Threads
5. Components receive execution requests via HTTP
6. On node completion, downstream dependencies are checked (fan-in)
7. WebSocket pushes real-time status updates to frontend

---

## Technology Stack

### Backend

| Technology | Version | Purpose |
|------------|---------|---------|
| Java | 21 | Runtime with Virtual Threads |
| Spring Boot | 4.0.1 | Web framework |
| Spring Security | 6.x | Authentication & Authorization |
| Spring Data JPA | - | Database ORM |
| Spring Data Redis | - | Caching & State |
| PostgreSQL | 15+ | Primary database |
| Redis | 7.0+ | Execution state, caching |
| Flyway | - | Database migrations |
| GraalVM Polyglot | 24.0 | JavaScript node execution |
| jjwt | 0.12.6 | JWT tokens |

### Frontend

| Technology | Version | Purpose |
|------------|---------|---------|
| React | 18 | UI framework |
| TypeScript | 5 | Type safety |
| Vite | - | Build tooling |
| React Flow | @xyflow/react | Flow editor |
| Zustand | - | State management |
| Ant Design | 5 | UI components |
| Axios | - | HTTP client |

---

## Backend Modules

The backend is organized into the following modules:

```
com.aiinpocket.n3n/
├── admin/           # Admin user management
├── agent/           # AI agent functionality
├── ai/              # AI provider integration
├── api/             # Common API controllers
├── auth/            # Authentication & authorization
├── activity/        # User activity logging
├── common/          # Shared utilities, configs, exceptions
├── component/       # Custom component registry
├── credential/      # Credential management (encryption)
├── execution/       # Flow execution engine
├── flow/            # Flow CRUD & versioning
├── oauth2/          # OAuth2 integration
├── scheduler/       # Schedule triggers (Quartz)
├── service/         # External service management
├── template/        # Flow templates
├── webhook/         # Webhook triggers
└── workspace/       # Workspace management
```

### Module Details

#### auth/
Authentication and authorization with JWT tokens.
- `AuthController` - Login, register, refresh, logout endpoints
- `JwtService` - JWT token generation and validation
- `SecurityConfig` - Spring Security configuration
- Rate limiting for login attempts

#### flow/
Flow management with version control.
- `FlowService` - CRUD operations, version management
- `FlowShareService` - Flow sharing with permission levels
- `DagParser` - Validates flow definitions as DAGs

#### execution/
Flow execution engine with node handlers.
- `ExecutionService` - Trigger, cancel, retry executions
- `StateManager` - Redis-backed execution state
- `NodeHandler` - Interface for node type handlers
- Built-in handlers: HTTP, Code (JS), Condition, Loop, Wait

#### credential/
Secure credential storage with AES-256-GCM encryption.
- `EncryptionService` - Encrypt/decrypt sensitive data
- `MasterKeyProvider` - Master key management
- `CredentialService` - CRUD with encryption

---

## API Reference

### Authentication

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/auth/login` | Login with email/password |
| POST | `/api/auth/register` | Register new user |
| POST | `/api/auth/refresh` | Refresh access token |
| POST | `/api/auth/logout` | Logout (revoke refresh token) |
| GET | `/api/auth/me` | Get current user info |
| GET | `/api/auth/setup-status` | Check if setup is required |

**Login Request:**
```json
{
  "email": "user@example.com",
  "password": "password123"
}
```

**Login Response:**
```json
{
  "accessToken": "eyJ...",
  "refreshToken": "abc...",
  "expiresIn": 900,
  "user": {
    "id": "uuid",
    "email": "user@example.com",
    "name": "User Name",
    "roles": ["USER"]
  }
}
```

### Flows

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/flows` | List flows (paginated) |
| GET | `/api/flows?search=query` | Search flows |
| POST | `/api/flows` | Create flow |
| GET | `/api/flows/{id}` | Get flow details |
| PUT | `/api/flows/{id}` | Update flow |
| DELETE | `/api/flows/{id}` | Delete flow (soft delete) |

#### Flow Versions

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/flows/{id}/versions` | List all versions |
| GET | `/api/flows/{id}/versions/{version}` | Get specific version |
| GET | `/api/flows/{id}/versions/published` | Get published version |
| POST | `/api/flows/{id}/versions` | Save version (draft) |
| POST | `/api/flows/{id}/versions/{version}/publish` | Publish version |
| GET | `/api/flows/{id}/versions/{version}/validate` | Validate version |

#### Flow Sharing

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/flows/{id}/shares` | List shares |
| POST | `/api/flows/{id}/shares` | Share flow |
| PUT | `/api/flows/{id}/shares/{shareId}?permission=edit` | Update permission |
| DELETE | `/api/flows/{id}/shares/{shareId}` | Remove share |
| GET | `/api/flows/shared-with-me` | Flows shared with me |

**Permission Levels:** `view`, `edit`, `admin`

### Executions

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/executions` | List all executions |
| GET | `/api/executions/by-flow/{flowId}` | List by flow |
| GET | `/api/executions/{id}` | Get execution details |
| GET | `/api/executions/{id}/nodes` | Get node executions |
| GET | `/api/executions/{id}/output` | Get execution output |
| POST | `/api/executions` | Create/trigger execution |
| POST | `/api/executions/{id}/cancel` | Cancel execution |
| POST | `/api/executions/{id}/retry` | Retry failed execution |

**Create Execution Request:**
```json
{
  "flowId": "uuid",
  "version": "1.0.0",
  "input": { "key": "value" }
}
```

### Credentials

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/credentials` | List credentials |
| POST | `/api/credentials` | Create credential |
| GET | `/api/credentials/{id}` | Get credential (masked) |
| PUT | `/api/credentials/{id}` | Update credential |
| DELETE | `/api/credentials/{id}` | Delete credential |
| GET | `/api/credentials/types` | List credential types |

**Credential Types:** `http_basic`, `http_bearer`, `api_key`, `oauth2`, `database`, `ssh`

### Components

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/components` | List components |
| POST | `/api/components` | Register component |
| GET | `/api/components/{id}` | Get component details |
| PUT | `/api/components/{id}` | Update component |
| DELETE | `/api/components/{id}` | Delete component |

### Skills

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/skills` | List all skills |
| GET | `/api/skills/builtin` | List built-in skills |
| GET | `/api/skills/categories` | List skill categories |
| GET | `/api/skills/category/{category}` | List skills by category |
| GET | `/api/skills/{id}` | Get skill details |
| POST | `/api/skills/{id}/execute` | Execute skill (test) |

**Built-in Skill Categories:** `http`, `web`, `data`, `file`, `notify`, `system`

### Webhooks

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/webhooks` | List webhooks |
| GET | `/api/webhooks/flow/{flowId}` | List webhooks for flow |
| POST | `/api/webhooks` | Create webhook |
| GET | `/api/webhooks/{id}` | Get webhook details |
| POST | `/api/webhooks/{id}/activate` | Activate webhook |
| POST | `/api/webhooks/{id}/deactivate` | Deactivate webhook |
| DELETE | `/api/webhooks/{id}` | Delete webhook |

#### Webhook Trigger Endpoints (Public)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/webhook/{path}` | Trigger via GET |
| POST | `/webhook/{path}` | Trigger via POST |
| PUT | `/webhook/{path}` | Trigger via PUT |
| DELETE | `/webhook/{path}` | Trigger via DELETE |

**Create Webhook Request:**
```json
{
  "flowId": "uuid",
  "name": "GitHub Push Trigger",
  "path": "github-push",
  "method": "POST",
  "authType": "signature",
  "authConfig": { "secret": "your-secret" }
}
```

**Trigger Response:**
```json
{
  "success": true,
  "executionId": "uuid",
  "message": "Flow execution started"
}
```

### Admin (Admin role required)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/admin/users` | List all users |
| POST | `/api/admin/users` | Create user |
| PUT | `/api/admin/users/{id}/status` | Update user status |
| PUT | `/api/admin/users/{id}/roles` | Update user roles |

### WebSocket

Connect to `/ws/executions/{executionId}` for real-time execution updates.

**Message Types:**
- `EXECUTION_STARTED`
- `NODE_STARTED`
- `NODE_COMPLETED`
- `NODE_FAILED`
- `EXECUTION_COMPLETED`
- `EXECUTION_FAILED`

---

## Database Schema

### Core Tables

#### users
```sql
CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    avatar_url VARCHAR(500),
    status VARCHAR(50) DEFAULT 'pending',
    email_verified BOOLEAN DEFAULT FALSE,
    last_login_at TIMESTAMP,
    login_attempts INTEGER DEFAULT 0,
    locked_until TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);
```

#### flows
```sql
CREATE TABLE flows (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    created_by UUID NOT NULL REFERENCES users(id),
    visibility VARCHAR(50) DEFAULT 'private',
    workspace_id UUID,
    is_deleted BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);
```

#### flow_versions
```sql
CREATE TABLE flow_versions (
    id UUID PRIMARY KEY,
    flow_id UUID NOT NULL REFERENCES flows(id),
    version VARCHAR(50) NOT NULL,
    definition JSONB NOT NULL,
    settings JSONB DEFAULT '{}',
    status VARCHAR(50) DEFAULT 'draft',
    created_by UUID REFERENCES users(id),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(flow_id, version)
);
```

#### executions
```sql
CREATE TABLE executions (
    id UUID PRIMARY KEY,
    flow_id UUID NOT NULL REFERENCES flows(id),
    version VARCHAR(50) NOT NULL,
    status VARCHAR(50) DEFAULT 'pending',
    trigger_type VARCHAR(50),
    input JSONB,
    output JSONB,
    error TEXT,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    triggered_by UUID REFERENCES users(id),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

#### credentials
```sql
CREATE TABLE credentials (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(50) NOT NULL,
    owner_id UUID NOT NULL REFERENCES users(id),
    encrypted_data BYTEA NOT NULL,
    iv BYTEA NOT NULL,
    visibility VARCHAR(50) DEFAULT 'private',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);
```

### Redis Keys

| Pattern | Purpose |
|---------|---------|
| `execution:{id}:state` | Current execution state |
| `execution:{id}:nodes` | Node execution states |
| `flow:{id}:lock` | Concurrency control lock |
| `data:{uuid}` | Large data blob storage |

---

## Development Guide

### Local Setup

1. **Start dependencies:**
```bash
docker compose up -d
```

2. **Run backend:**
```bash
./mvnw spring-boot:run
```

3. **Run frontend (hot reload):**
```bash
cd src/main/frontend
npm install
npm run dev
```

### Build Commands

```bash
# Full build (backend + frontend)
./mvnw clean install

# Backend only
./mvnw clean install -Dfrontend.skip=true

# Run tests
./mvnw test

# Run single test
./mvnw test -Dtest=AuthServiceTest

# Native image (requires GraalVM)
./mvnw native:compile -Pnative
```

### Code Style

**Backend:**
- Use Lombok annotations (`@Data`, `@Builder`, `@RequiredArgsConstructor`)
- Controllers return DTOs, not entities
- Service interfaces with `Impl` suffix for implementations
- Use Virtual Threads for async operations

**Frontend:**
- Functional components with hooks
- Zustand for state management
- TypeScript strict mode
- Ant Design components

### Adding a New Node Type

1. Create handler in `execution/handler/`:
```java
@Component
public class MyNodeHandler implements NodeHandler {
    @Override
    public String getType() {
        return "my-node";
    }

    @Override
    public ValidationResult validate(NodeExecutionContext ctx) {
        // Validate node configuration
    }

    @Override
    public NodeExecutionResult execute(NodeExecutionContext ctx) {
        // Execute node logic
    }
}
```

2. Add frontend component in `frontend/src/components/flow/nodes/`
3. Register in node registry

---

## Testing

### Test Configuration

Tests use H2 in-memory database and mock Redis.

`src/test/resources/application-test.properties`:
```properties
spring.datasource.url=jdbc:h2:mem:testdb;MODE=PostgreSQL
spring.jpa.hibernate.ddl-auto=create-drop
spring.flyway.enabled=false
management.health.redis.enabled=false
```

### Running Tests

```bash
# All tests
./mvnw test

# Specific test class
./mvnw test -Dtest=AuthServiceTest

# Specific test method
./mvnw test -Dtest=AuthServiceTest#register_firstUser_shouldBeAdmin

# With coverage report
./mvnw test jacoco:report
```

### Test Structure

```
src/test/java/com/aiinpocket/n3n/
├── base/                    # Base test classes
│   ├── BaseRepositoryTest   # @DataJpaTest base
│   ├── BaseServiceTest      # Service test base with mocks
│   └── BaseControllerTest   # MockMvc test base
├── auth/
│   ├── AuthServiceTest
│   └── AuthControllerTest
├── flow/
│   ├── FlowServiceTest
│   └── DagParserTest
└── ...
```

### Writing Tests

**Repository Test:**
```java
@DataJpaTest
@ActiveProfiles("test")
class FlowRepositoryTest extends BaseRepositoryTest {
    @Autowired
    private FlowRepository flowRepository;

    @Test
    void findByNameAndIsDeletedFalse_exists_returnsFlow() {
        // Given
        Flow flow = createFlow("Test Flow");

        // When
        Optional<Flow> result = flowRepository.findByNameAndIsDeletedFalse("Test Flow");

        // Then
        assertThat(result).isPresent();
    }
}
```

**Service Test:**
```java
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AuthService authService;

    @Test
    void register_firstUser_shouldBeAdmin() {
        // Given
        when(userRepository.count()).thenReturn(0L);

        // When
        AuthResponse response = authService.register(request);

        // Then
        assertThat(response.getUser().getRoles()).contains("ADMIN");
    }
}
```

**Controller Test:**
```java
@WebMvcTest(AuthController.class)
@ActiveProfiles("test")
class AuthControllerTest extends BaseControllerTest {
    @MockBean
    private AuthService authService;

    @Test
    void login_validCredentials_returnsToken() throws Exception {
        // Given
        when(authService.login(any(), any(), any())).thenReturn(authResponse);

        // When/Then
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").exists());
    }
}
```

---

## Deployment

### Docker

```bash
# Build image
docker build -t n3n:latest .

# Run with compose
docker compose up -d
```

### Kubernetes

Kubernetes manifests are in `k8s/` directory (when available):
- `k8s/base/` - Base configurations
- `k8s/overlays/dev/` - Development overlay
- `k8s/overlays/prod/` - Production overlay

### Environment Variables

See [README.md](README.md#environment-variables) for complete list.

**Critical for Production:**
- `JWT_SECRET` - Must be changed from default
- `DATABASE_PASSWORD` - Secure database password
- `N3N_MASTER_KEY` - Encryption master key
