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
- **User Login:** Accept username/password, validate credentials, return JWT tokens
  - Accept JSON payload with username, password
  - Authenticate via Spring AuthenticationManager
  - Generate HS512-signed access token (15-min expiration) with unique JTI
  - Generate refresh token (7-day expiration)
  - Return both tokens + user metadata in JwtResponseDto

- **User Registration:** Accept registration data, create user with roles
  - Accept JSON with username, email (required), password, fullName, roles array
  - Validate username uniqueness
  - Validate email uniqueness and required
  - Encode password via BCrypt
  - Create roles if new, assign existing roles by ID
  - Persist User entity with role associations

- **Token Refresh:** Accept refresh token, return new token pair
  - Accept JSON with refreshToken
  - Validate refresh token exists and not expired
  - Generate new access token with new JTI
  - Rotate refresh token (delete old, create new)
  - Return new token pair in TokenRefreshResponseDto

- **Password Reset:** Email-driven password reset flow
  - Forgot Password: Accept email, generate 24-hour reset token, send email
  - Reset Password: Accept reset token + new password, validate token, update password
  - Security: Returns generic message regardless of email existence

- **Logout:** Blacklist token and delete refresh token
  - Accept Authorization header with access token
  - Extract and blacklist JTI with expiration date
  - Delete user's refresh token from database
  - Scheduled cleanup: hourly job removes expired blacklist entries

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
- **Access Token Expiration:** 15 minutes (900000 ms, configurable)
- **Refresh Token Expiration:** 7 days (604800000 ms, configurable)
- **Token Rotation:** Refresh token replaced on each use
- **Token Revocation:** JTI-based blacklisting for logout
- **Email Validation:** Required unique email on registration
- **Password Reset:** 24-hour expiration tokens via secure email
- **Session Management:** Stateless (SessionCreationPolicy.STATELESS)
- **Scheduled Cleanup:** Hourly job cleans expired blacklist entries
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
  "refreshToken": "eyJhbGciOiJIUzUxMiJ9...",
  "username": "john",
  "name": "John Doe",
  "roles": ["ROLE_USER", "ROLE_PM"]
}
```

Error (401 Unauthorized):
- Invalid credentials, username not found, or password mismatch

### Register Endpoint
**POST /api/auth/register**
- Consumes: application/json
- Produces: application/json
- Auth: None (public)

Request:
```json
{
  "username": "jane",
  "email": "jane@example.com",
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
- Username already taken, email in use, or email required

### Forgot Password Endpoint
**POST /api/auth/forgot-password**
- Consumes: application/json
- Produces: application/json
- Auth: None (public)

Request:
```json
{
  "email": "jane@example.com"
}
```

Response (200 OK):
```
"If the email exists, a password reset link has been sent."
```

### Reset Password Endpoint
**POST /api/auth/reset-password**
- Consumes: application/json
- Produces: application/json
- Auth: None (token-based)

Request:
```json
{
  "token": "reset-token-from-email",
  "newPassword": "newSecure123"
}
```

Response (200 OK):
```
"Password reset successful."
```

Error (400 Bad Request):
- Invalid, expired, or already-used reset token

### Refresh Token Endpoint
**POST /api/auth/refresh-token**
- Consumes: application/json
- Produces: application/json
- Auth: None (refresh token-based)

Request:
```json
{
  "refreshToken": "eyJhbGciOiJIUzUxMiJ9..."
}
```

Response (200 OK):
```json
{
  "accessToken": "eyJhbGciOiJIUzUxMiJ9...",
  "refreshToken": "eyJhbGciOiJIUzUxMiJ9..."
}
```

Error (400 Bad Request):
- Invalid or expired refresh token

### Logout Endpoint
**POST /api/auth/logout**
- Consumes: (none)
- Produces: application/json
- Auth: JWT Bearer token required
- Header: `Authorization: Bearer <accessToken>`

Response (200 OK):
```
"Logged out successfully."
```

Error (400 Bad Request):
- No token provided
- Invalid token

### Protected Endpoint Example
**GET /api/protected** (or any non-auth endpoint)
- Auth: JWT Bearer token required
- Header: `Authorization: Bearer <accessToken>`

Response (401 Unauthorized):
- Missing/invalid token
- Token expired (15 min) - use refresh endpoint
- Token blacklisted (logged out) - re-login required
- Invalid signature

Response (403 Forbidden):
- User lacks required role

## Architecture Decisions

### Decision: Stateless JWT vs Session-Based
**Chosen:** Stateless JWT with Refresh Tokens
- **Rationale:** Microservices-friendly, horizontal scaling, no server state for access tokens
- **Trade-off:** Larger token size vs reduced database load; refresh tokens stored in DB for revocation

### Decision: Symmetric (HS512) vs Asymmetric (RS256) Signing
**Chosen:** Symmetric HS512
- **Rationale:** Shared-secret deployment, simpler operations, faster validation
- **Trade-off:** All instances must protect secret vs distributed trust model

### Decision: Eager vs Lazy Role Loading
**Chosen:** Eager (FetchType.EAGER)
- **Rationale:** Roles required in SecurityContext, single query more efficient
- **Trade-off:** Always loads roles even if not needed vs N+1 queries

### Decision: Manual Schema vs Hibernate DDL
**Chosen:** Manual (ddl-auto: none, create-drop for dev)
- **Rationale:** Database as source of truth, version control flexibility
- **Trade-off:** Extra maintenance vs schema evolution control

### Decision: Token Revocation Strategy
**Chosen:** JTI-based Blacklist with Scheduled Cleanup
- **Rationale:** Efficient revocation without modifying JWT claims, scheduled cleanup prevents table bloat
- **Trade-off:** Database lookup on validation vs complete logout support

### Decision: Refresh Token Rotation
**Chosen:** Replace token on each refresh
- **Rationale:** Limits window of exposure if refresh token compromised
- **Trade-off:** Database updates on refresh vs reduced breach impact

### Decision: Password Reset Delivery
**Chosen:** Email-based with stateful tokens
- **Rationale:** Secure, auditable, familiar to users
- **Trade-off:** Requires SMTP config vs alternative delivery methods

## Roadmap

### Phase 1: Foundation (COMPLETE)
- ✓ Core authentication (login/register)
- ✓ JWT generation & validation with JTI
- ✓ Role-based authorization
- ✓ PostgreSQL persistence
- ✓ Docker containerization
- ✓ Basic testing

### Phase 2: Token Management (COMPLETE)
- ✓ Token refresh mechanism with rotation
- ✓ Password reset flow via email
- ✓ Logout with token blacklisting
- ✓ Scheduled cleanup of expired tokens
- ✓ Email validation on registration

### Phase 3: Enhancement (Planned)
- [ ] User profile endpoints (GET /api/users/me)
- [ ] Email verification (confirmation link)
- [ ] Rate limiting on login endpoint
- [ ] OAuth2/social login integration
- [ ] Two-factor authentication (SMS/authenticator)
- [ ] Account lockout after N failed attempts

### Phase 4: Security Hardening (Planned)
- [ ] Add JWT claim validation (issuer, audience)
- [ ] Audit logging on sensitive actions
- [ ] Request signing for sensitive operations
- [ ] IP whitelisting
- [ ] Token encryption at rest
- [ ] Secret rotation mechanism

### Phase 5: Operations (Planned)
- [ ] Health check endpoint (/actuator/health)
- [ ] Metrics collection (Micrometer)
- [ ] Centralized logging (ELK stack integration)
- [ ] CI/CD pipeline (GitHub Actions)
- [ ] Load testing & performance optimization
- [ ] Kubernetes deployment manifests

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

## Implementation Notes

### Email Configuration
- Uses Spring Mail with Gmail SMTP (smtp.gmail.com:587)
- Requires environment variables:
  - MAIL_USERNAME: Gmail address
  - MAIL_PASSWORD: App-specific password (not account password)
- passwordResetBaseUrl: Configure for frontend redirect after email click

### Database Changes
- Added columns to users: email (UNIQUE)
- New tables: refresh_tokens, password_reset_tokens, blacklisted_tokens
- Recommend running schema migrations for production

### Scheduled Tasks
- JwtService.cleanupExpiredBlacklistedTokens() runs hourly
- Removes entries where expiryDate < now
- Prevents unbounded growth of blacklist table

## Open Questions

1. **Multi-tenancy:** Future support for multiple organizations?
2. **API Versioning:** How to version endpoints (v1/v2)?
3. **Rate Limiting:** Should login endpoint have rate limiting to prevent brute force?
4. **Email Delivery:** Use service like SendGrid/Mailgun instead of SMTP?
5. **Token Revocation TTL:** Should whitelist instead of blacklist for shorter-lived tokens?
