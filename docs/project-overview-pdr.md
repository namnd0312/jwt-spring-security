# Project Overview & Product Development Requirements

**Project:** JWT Spring Security
**Version:** 0.0.1-SNAPSHOT
**Group:** com.namnd
**Status:** Active Development
**Last Updated:** February 2026

## Executive Summary

JWT Spring Security is a Spring Boot-based REST API providing stateless authentication and authorization via JSON Web Tokens (JWT). Designed for secure, scalable microservices architectures, it combines Spring Security with JJWT library to deliver HS512-signed tokens with role-based access control.

**Key Characteristics:**
- Stateless JWT authentication (no session storage)
- BCrypt password hashing
- Role-based authorization (@PreAuthorize annotations)
- PostgreSQL data persistence
- Docker containerization
- RESTful login/register endpoints
- CORS-enabled for cross-origin requests

## Functional Requirements

### Authentication (FR-001)
- **User Login:** Accept username/password, validate credentials, return JWT token
  - Accept JSON payload with username, password
  - Authenticate via Spring AuthenticationManager
  - Generate HS512-signed JWT with 24h expiration
  - Return token + user metadata in JwtResponseDto

- **User Registration:** Accept registration data, create user with roles
  - Accept JSON with username, password, fullName, roles array
  - Validate username uniqueness
  - Encode password via BCrypt
  - Create roles if new, assign existing roles by ID
  - Persist User entity with role associations

### Authorization (FR-002)
- **Token Validation:** Validate JWT on protected requests
  - Extract Bearer token from Authorization header
  - Parse & verify HS512 signature
  - Check token expiration
  - Load user from database via SecurityContext

- **Role-Based Access:** Enforce roles via method-level security
  - Support @PreAuthorize("hasRole('ROLE_ADMIN')")
  - Support @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_PM')")
  - Return 403 Forbidden on insufficient role

### User Management (FR-003)
- **User Entity:** Store user credentials & profile
  - Username (unique)
  - Password (BCrypt hashed)
  - Full name
  - Set of assigned roles

- **Role Entity:** Define permission roles
  - Role name (ROLE_USER, ROLE_PM, ROLE_ADMIN)
  - Many-to-many relationship with User

## Non-Functional Requirements

### Security (NFR-001)
- **Password Encoding:** BCrypt with Spring Security encoder
- **JWT Signing:** HMAC SHA-512 (HS512) algorithm
- **Token Expiration:** 24 hours (86400000 ms, configurable)
- **Session Management:** Stateless (SessionCreationPolicy.STATELESS)
- **CSRF Protection:** Disabled for JWT API (appropriate)
- **CORS:** Enabled for all origins (configurable)

### Performance (NFR-002)
- **Database Queries:** Optimized via Spring Data JPA
- **JWT Processing:** In-memory token validation (no database lookup on validate)
- **Eager Loading:** User roles fetched eagerly to minimize queries
- **Connection Pooling:** Standard Spring Boot datasource (HikariCP)

### Scalability (NFR-003)
- **Stateless Architecture:** No session affinity required
- **Horizontal Scaling:** Multiple instances share same JWT secret
- **Database:** PostgreSQL 13.1 with standard replication/failover
- **Token Signature:** Consistent across all instances via shared jwtSecret

### Availability (NFR-004)
- **Database Dependency:** PostgreSQL required for startup
- **Graceful Degradation:** Token validation fails if signature key corrupted
- **Container Restart:** docker-compose restart policy: unless-stopped

### Maintainability (NFR-005)
- **Code Organization:** Package structure by layer (controller, service, model, repository)
- **Logging:** DEBUG level for app package, SQL query logging
- **Configuration:** YAML-based with environment variable overrides
- **Dependencies:** Minimal, well-maintained (Spring 2.6.4, JJWT 0.9.0)

## Technical Constraints

| Constraint | Specification | Rationale |
|-----------|---------------|-----------|
| Java Version | 1.8 source, 11 runtime | Compatibility with legacy systems |
| Spring Boot | 2.6.4 (LTS baseline) | Stability, active maintenance |
| Database | PostgreSQL 13.1+ | Relational model, JSONB support |
| JWT Library | JJWT 0.9.0 | Lightweight, industry-standard |
| Packaging | WAR (with fallback JAR) | Tomcat deployment flexibility |
| Token Algorithm | HS512 | Deterministic, fast, symmetric |
| Session Policy | STATELESS | Matches JWT stateless design |

## Success Metrics

| Metric | Target | Measurement |
|--------|--------|-------------|
| Login Response Time | < 200ms | Spring Security authentication + JWT generation |
| Token Validation | < 50ms | Token parsing + signature verification |
| Database Availability | 99.5% uptime | PostgreSQL monitored via container |
| Code Coverage | > 70% (unit tests) | Maven jacoco plugin |
| Security Compliance | OWASP Top 10 covered | BCrypt, HS512, CSRF disabled, CORS controlled |

## API Contracts

### Login Endpoint
**POST /api/auth/login**
- Consumes: application/json
- Produces: application/json
- Auth: None (public)

Request:
```json
{
  "username": "john",
  "password": "password123"
}
```

Response (200 OK):
```json
{
  "id": 1,
  "token": "eyJhbGciOiJIUzUxMiJ9...",
  "type": "Bearer",
  "username": "john",
  "name": "John Doe",
  "roles": ["ROLE_USER", "ROLE_PM"]
}
```

Error (401 Unauthorized):
- Invalid credentials

Error (BadCredentialsException):
- Username not found, password mismatch

### Register Endpoint
**POST /api/auth/register**
- Consumes: application/json
- Produces: application/json
- Auth: None (public)

Request:
```json
{
  "username": "jane",
  "password": "secure123",
  "fullName": "Jane Doe",
  "roles": [
    {"name": "ROLE_USER"}
  ]
}
```

Response (200 OK):
```
"User registered successfully!"
```

Error (400 Bad Request):
- Username already taken
- Missing required fields

### Protected Endpoint Example
**GET /api/protected** (or any non-auth endpoint)
- Auth: JWT Bearer token required
- Header: `Authorization: Bearer <token>`

Response (401 Unauthorized):
- Missing/invalid token
- Token expired
- Invalid signature

Response (403 Forbidden):
- User lacks required role

## Architecture Decisions

### Decision: Stateless JWT vs Session-Based
**Chosen:** Stateless JWT
- **Rationale:** Microservices-friendly, horizontal scaling, no server state management
- **Trade-off:** Larger token size vs reduced database load on auth validation

### Decision: Symmetric (HS512) vs Asymmetric (RS256) Signing
**Chosen:** Symmetric HS512
- **Rationale:** Shared-secret deployment, simpler operations, faster validation
- **Trade-off:** All instances must protect secret vs distributed trust model

### Decision: Eager vs Lazy Role Loading
**Chosen:** Eager (FetchType.EAGER)
- **Rationale:** Roles required in SecurityContext, single query more efficient
- **Trade-off:** Always loads roles even if not needed vs N+1 queries

### Decision: Manual Schema vs Hibernate DDL
**Chosen:** Manual (ddl-auto: none)
- **Rationale:** Database as source of truth, version control flexibility
- **Trade-off:** Extra maintenance vs schema evolution control

## Roadmap

### Phase 1: Foundation (Current)
- ✓ Core authentication (login/register)
- ✓ JWT generation & validation
- ✓ Role-based authorization
- ✓ PostgreSQL persistence
- ✓ Docker containerization
- ✓ Basic testing

### Phase 2: Enhancement (Planned)
- [ ] Token refresh mechanism
- [ ] User profile endpoints (GET /api/users/me)
- [ ] Password reset flow
- [ ] Email verification on register
- [ ] Rate limiting on login endpoint
- [ ] OAuth2/social login integration

### Phase 3: Security Hardening (Planned)
- [ ] Add JWT claim validation (issuer, audience)
- [ ] Implement rate limiting
- [ ] Add request signing (for sensitive operations)
- [ ] Audit logging on sensitive actions
- [ ] IP whitelisting
- [ ] Two-factor authentication

### Phase 4: Operations (Planned)
- [ ] Health check endpoint (/actuator/health)
- [ ] Metrics collection (Micrometer)
- [ ] Centralized logging (ELK stack integration)
- [ ] CI/CD pipeline (GitHub Actions)
- [ ] Load testing & performance optimization

## Dependencies

| Library | Version | Purpose |
|---------|---------|---------|
| Spring Boot | 2.6.4 | Framework |
| Spring Security | via Spring Boot | Authentication |
| Spring Data JPA | via Spring Boot | ORM |
| JJWT | 0.9.0 | JWT handling |
| PostgreSQL Driver | latest | Database |
| Lombok | latest | Boilerplate reduction |
| BCrypt | via Spring Security | Password encoding |

**Known Limitations:**
- JJWT 0.9.0 is stable but newer versions available (0.11.x, 0.12.x)
- Spring Boot 2.6.4 LTS (newer 3.x available with Java 17+ requirement)

## Configuration Parameters

| Parameter | Default | Type | Scope |
|-----------|---------|------|-------|
| server.port | 8080 | int | Spring Boot |
| namnd.app.jwtSecret | bezKoderSecretKey | string | Custom |
| namnd.app.jwtExpiration | 86400000 | long | Custom (ms) |
| spring.datasource.url | jdbc:postgresql://localhost:5432/testdb | string | JPA |
| spring.datasource.username | postgres | string | JPA |
| spring.datasource.password | 123456 | string | JPA (should use env var) |

## Acceptance Criteria

### User Story: Login Workflow
**Given** a user with valid credentials registered in the system
**When** user POSTs to /api/auth/login with username & password
**Then** API returns 200 OK with JWT token valid for 24 hours

**And** token can be decoded to extract username
**And** token signature verifies with configured jwtSecret
**And** subsequent requests with Authorization header are authenticated

### User Story: Registration Workflow
**Given** a unique username not in the system
**When** user POSTs to /api/auth/register with credentials & roles
**Then** user account created with BCrypt-encoded password
**And** user assigned to specified roles
**And** username duplicate attempt returns 400 Bad Request

### User Story: Role-Based Access
**Given** a user with ROLE_ADMIN role
**When** accessing endpoint protected by @PreAuthorize("hasRole('ROLE_ADMIN')")
**Then** request succeeds (200 OK)

**And** user without ROLE_ADMIN accessing same endpoint gets 403 Forbidden

## Open Questions

1. **Token Refresh:** Should API support refresh tokens? (Currently no refresh mechanism)
2. **Token Revocation:** How to invalidate tokens before expiration? (Currently no revocation list)
3. **Multi-tenancy:** Future support for multiple organizations?
4. **API Versioning:** How to version endpoints (v1/v2)?
5. **Rate Limiting:** Should login endpoint have rate limiting to prevent brute force?
