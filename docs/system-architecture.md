# System Architecture

**Project:** jwt-spring-security
**Version:** 0.0.1-SNAPSHOT
**Architecture Pattern:** Layered Architecture with JWT Authentication
**Last Updated:** February 2026

## Architecture Overview

JWT Spring Security is built on a **layered architecture** with stateless JWT-based authentication. The system separates concerns into distinct layers: Presentation (REST), Business Logic (Service), Data Access (Repository), and Data Storage (Database).

```
┌─────────────────────────────────────────────────────────────┐
│                      CLIENT (Web/Mobile)                     │
└──────────────────────────┬──────────────────────────────────┘
                           │ HTTP + JWT Bearer Token
┌──────────────────────────▼──────────────────────────────────┐
│                 PRESENTATION LAYER                           │
│  ┌──────────────────────────────────────────────────────┐   │
│  │         AuthController (@RestController)             │   │
│  │  POST /api/auth/login                                │   │
│  │  POST /api/auth/register                             │   │
│  └──────────────────────────────────────────────────────┘   │
│                           ▲                                   │
│                           │                                   │
└───────────────────────────┼───────────────────────────────────┘
                            │
        ┌───────────────────┴───────────────────┐
        │                                       │
┌───────▼────────────────────────────────────┐ │
│  SECURITY LAYER                            │ │
│ ┌──────────────────────────────────────┐  │ │
│ │  SecurityConfig                      │  │ │
│ │  - Filter chain configuration        │  │ │
│ │  - PasswordEncoder (BCrypt)          │  │ │
│ │  - AuthenticationManager             │  │ │
│ │  - CSRF disabled, CORS enabled       │  │ │
│ └──────────────────────────────────────┘  │ │
│ ┌──────────────────────────────────────┐  │ │
│ │  JwtAuthenticationFilter             │  │ │
│ │  - Extracts Bearer token             │  │ │
│ │  - Validates JWT signature           │  │ │
│ │  - Sets SecurityContext              │  │ │
│ └──────────────────────────────────────┘  │ │
│ ┌──────────────────────────────────────┐  │ │
│ │  CustomAccessDeniedHandler           │  │ │
│ │  - Returns 403 on access denied      │  │ │
│ └──────────────────────────────────────┘  │ │
└────────────────────────────────────────────┘ │
                                               │
┌──────────────────────────────────────────────▼─────┐
│               BUSINESS LOGIC LAYER                   │
│  ┌────────────────────────────────────────────┐   │
│  │  UserService (interface)                   │   │
│  │  ├─ save(User)                             │   │
│  │  ├─ findByEmail(String)                    │   │
│  │  ├─ existsByEmail(String)                  │   │
│  │  └─ loadUserByUsername(String) [queries by email] │
│  └────────────────────────────────────────────┘   │
│  ┌────────────────────────────────────────────┐   │
│  │  UserServiceImpl                             │   │
│  │  ├─ delegates to UserRepository             │   │
│  │  └─ loadUserByUsername queries by email     │   │
│  └────────────────────────────────────────────┘   │
│  ┌────────────────────────────────────────────┐   │
│  │  JwtService (@Component)                    │   │
│  │  ├─ generateTokenLogin(Authentication)      │   │
│  │  ├─ generateTokenFromEmail(String)          │   │
│  │  ├─ validateJwtToken(String)                │   │
│  │  └─ getEmailFromJwtToken(String)            │   │
│  └────────────────────────────────────────────┘   │
│  ┌────────────────────────────────────────────┐   │
│  │  BlacklistedTokenServiceImpl                 │   │
│  │  ├─ delegates to RedisService               │   │
│  │  ├─ blacklistToken(jti, expiry)             │   │
│  │  └─ isTokenBlacklisted(jti)                 │   │
│  └────────────────────────────────────────────┘   │
│  ┌────────────────────────────────────────────┐   │
│  │  RoleService (interface)                    │   │
│  │  ├─ save(Role)                              │   │
│  │  ├─ findByName(String)                      │   │
│  │  └─ flush()                                 │   │
│  └────────────────────────────────────────────┘   │
│  ┌────────────────────────────────────────────┐   │
│  │  RoleServiceImpl                             │   │
│  │  └─ delegates to RoleRepository              │   │
│  └────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────┐
│          SHARED UTILITIES LAYER (Redis)              │
│  ┌────────────────────────────────────────────┐   │
│  │  RedisService (interface, 46 lines)        │   │
│  │  ├─ Key-Value: set, get, delete, expire    │   │
│  │  ├─ Hash: hSet, hGet, hGetAll, hDelete     │   │
│  │  ├─ List: lPush, rPush, lRange, lLen       │   │
│  │  ├─ Set: sAdd, sMembers, sIsMember         │   │
│  │  ├─ Pub/Sub: publish(channel, message)     │   │
│  │  └─ Lock: tryLock(key, timeout), unlock()  │   │
│  └────────────────────────────────────────────┘   │
│  ┌────────────────────────────────────────────┐   │
│  │  RedisServiceImpl (276 lines)                │   │
│  │  ├─ Injected: StringRedisTemplate           │   │
│  │  ├─ Injected: RedisTemplate<String, Object>│   │
│  │  ├─ Try-catch error handling per method     │   │
│  │  └─ Jackson2Json serialization              │   │
│  └────────────────────────────────────────────┘   │
│  ┌────────────────────────────────────────────┐   │
│  │  RedisKeyPrefix (constants)                 │   │
│  │  ├─ BLACKLIST = "blacklist:"                │   │
│  │  └─ LOCK = "lock:"                          │   │
│  └────────────────────────────────────────────┘   │
│  ┌────────────────────────────────────────────┐   │
│  │  RedisConfig (@Configuration)               │   │
│  │  └─ Provides RedisTemplate bean             │   │
│  └────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────┐
│            DATA ACCESS LAYER (Repositories)         │
│  ┌────────────────────────────────────────────┐   │
│  │  UserRepository extends JpaRepository       │   │
│  │  ├─ findByUsername(String)                 │   │
│  │  ├─ existsByUsername(String)               │   │
│  │  ├─ findByEmail(String)                    │   │
│  │  └─ existsByEmail(String)                  │   │
│  └────────────────────────────────────────────┘   │
│  ┌────────────────────────────────────────────┐   │
│  │  RoleRepository extends JpaRepository       │   │
│  │  └─ findByName(String)                     │   │
│  └────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────┐
│           DATA PERSISTENCE LAYER (Models)           │
│  ┌────────────────────────────────────────────┐   │
│  │  User (@Entity, table: users)               │   │
│  │  ├─ id: Long (PK)                          │   │
│  │  ├─ username: String (unique)              │   │
│  │  ├─ password: String (BCrypt-encoded)      │   │
│  │  ├─ fullName: String                       │   │
│  │  └─ roles: Set<Role> (ManyToMany, EAGER)   │   │
│  └────────────────────────────────────────────┘   │
│  ┌────────────────────────────────────────────┐   │
│  │  Role (@Entity, table: roles)               │   │
│  │  ├─ id: Long (PK)                          │   │
│  │  └─ name: String (ROLE_USER, etc)          │   │
│  └────────────────────────────────────────────┘   │
│  ┌────────────────────────────────────────────┐   │
│  │  UserPrinciple (implements UserDetails)     │   │
│  │  ├─ adapts User for Spring Security        │   │
│  │  ├─ id, username, password, fullName       │   │
│  │  ├─ authorities (from Role names)          │   │
│  │  └─ account status methods (all true)      │   │
│  └────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────┐
│         DATABASE LAYER (PostgreSQL 13.1)            │
│  ┌─────────────┐  ┌──────────┐  ┌────────────────┐ │
│  │ users       │  │ roles    │  │ user_roles     │ │
│  │ ─────────── │  │ ────────── │  │ ────────────── │ │
│  │ id (PK)     │  │ id (PK)  │  │ user_id (FK)   │ │
│  │ username    │  │ name     │  │ role_id (FK)   │ │
│  │ password    │  │          │  │                │ │
│  │ full_name   │  │          │  │                │ │
│  └─────────────┘  └──────────┘  └────────────────┘ │
└─────────────────────────────────────────────────────┘
```

## Request Flow Diagrams

### Authentication Flow (Login)

```
CLIENT                           SERVER
  │                                │
  ├─ POST /api/auth/login ──────▶ AuthController.authenticateUser()
  │  {email, password}             │
  │                                ├─ AuthenticationManager.authenticate()
  │                                │  ├─ UserServiceImpl.loadUserByUsername(email)
  │                                │  │  └─ UserRepository.findByEmail()
  │                                │  │     └─ Database Query
  │                                │  │        ├─ Load User
  │                                │  │        └─ Eager Load Roles
  │                                │  │        └─ Return User
  │                                │  ├─ PasswordEncoder.matches(inputPassword, userPassword)
  │                                │  │  └─ BCrypt Verification
  │                                │  └─ Return Authentication(UserPrinciple)
  │                                │
  │                                ├─ JwtService.generateTokenLogin()
  │                                │  ├─ Extract email from UserPrinciple
  │                                │  ├─ Build JWT Claims + JTI
  │                                │  │  ├─ subject: email
  │                                │  │  ├─ jti: random UUID
  │                                │  │  ├─ issuedAt: now
  │                                │  │  └─ expiration: now + 15min
  │                                │  ├─ Sign with HS512 + SECRET_KEY
  │                                │  └─ Return JWT string
  │                                │
  │                                ├─ RefreshTokenService.createRefreshToken()
  │                                │  ├─ Generate random refresh token
  │                                │  ├─ Set expiration: now + 7 days
  │                                │  └─ Save RefreshToken to database
  │                                │
  │                                ├─ Load User from DB
  │                                └─ Return JwtResponseDto
  │
  │ ◀─ 200 OK ─────────────────────┤
  │  {token, refreshToken,          │
  │   email, username, roles, ...}  │
  │                                 │
  └─ Store both tokens locally ────┘
```

### Request Authorization Flow

```
CLIENT                           SERVER
  │                                │
  ├─ GET /api/protected ──────────▶ JwtAuthenticationFilter
  │  Authorization: Bearer <token>  │
  │                                 ├─ Extract Authorization header
  │                                 ├─ Parse Bearer token
  │                                 │
  │                                 ├─ JwtService.validateJwtToken()
  │                                 │  ├─ Parse JWT signature
  │                                 │  ├─ Verify HS512 with SECRET_KEY
  │                                 │  └─ Check expiration
  │                                 │     (no DB call for validation)
  │                                 │
  │                                 ├─ JwtService.getEmailFromJwtToken()
  │                                 │  └─ Extract email from claims
  │                                 │
  │                                 ├─ UserServiceImpl.loadUserByUsername(email)
  │                                 │  └─ Load User + Roles from DB by email
  │                                 │
  │                                 ├─ Build UserPrinciple
  │                                 ├─ Create SecurityContext
  │                                 └─ Continue to Handler
  │                                    │
  │                                    ├─ Check @PreAuthorize annotations
  │                                    ├─ Verify user has required role
  │                                    │
  │                                    └─ Execute endpoint
  │
  │ ◀─ 200 OK ─────────────────────┤
  │  {protected resource data}      │
  │
  └─────────────────────────────────┘
```

### Registration Flow

```
CLIENT                              SERVER
  │                                  │
  ├─ POST /api/auth/register ───────▶ AuthController.registerUser()
  │  {username, email, password,       │
  │   fullName, roles}                │
  │                                   ├─ Check email uniqueness
  │                                   │  └─ UserService.existsByEmail()
  │                                   │     └─ DB Query
  │                                   │        └─ Return boolean
  │                                   │
  │                                   ├─ Process roles
  │                                   │  ├─ For each role in request
  │                                   │  │  ├─ RoleService.findByName()
  │                                   │  │  │  └─ If not exists: save new role
  │                                   │  │  └─ Set role ID from DB
  │                                   │  └─ Transaction: flush
  │                                   │
  │                                   ├─ Map RegisterDto → User
  │                                   │  ├─ RegisterDtoMapper.toEntity()
  │                                   │  │  ├─ Copy username, fullName
  │                                   │  │  ├─ PasswordEncoder.encode()
  │                                   │  │  │  └─ BCrypt hash
  │                                   │  │  └─ Copy roles
  │                                   │  │     └─ Return User entity
  │                                   │
  │                                   ├─ UserService.save(user)
  │                                   │  └─ UserRepository.save()
  │                                   │     └─ DB INSERT (with FK to roles)
  │                                   │
  │                                   └─ Return success message
  │
  │ ◀─ 200 OK ──────────────────────┤
  │  "User registered successfully!"  │
  │
  └─────────────────────────────────┘
```

### Token Refresh Flow

```
CLIENT                           SERVER
  │                                │
  ├─ POST /api/auth/refresh-token ▶ AuthController.refreshToken()
  │  {refreshToken}                │
  │                                ├─ RefreshTokenService.findByToken()
  │                                │  └─ Database lookup
  │                                │
  │                                ├─ RefreshTokenService.verifyExpiration()
  │                                │  ├─ Check if expired
  │                                │  └─ Return RefreshToken if valid
  │                                │
  │                                ├─ JwtService.generateTokenFromEmail()
  │                                │  ├─ Extract email from user
  │                                │  ├─ Build JWT with new JTI
  │                                │  └─ Return new access token
  │                                │
  │                                ├─ RefreshTokenService.createRefreshToken()
  │                                │  ├─ Rotate: delete old, create new
  │                                │  └─ Save new RefreshToken
  │                                │
  │                                └─ Return TokenRefreshResponseDto
  │
  │ ◀─ 200 OK ─────────────────────┤
  │  {accessToken, refreshToken}    │
  │                                 │
  └─ Update both tokens locally ───┘
```

### Password Reset Flow

```
CLIENT                           SERVER
  │                                │
  ├─ POST /api/auth/forgot-password ▶ AuthController.forgotPassword()
  │  {email}                        │
  │                                ├─ PasswordResetService.createPasswordResetToken()
  │                                │  ├─ UserRepository.findByEmail()
  │                                │  ├─ Generate reset token (24h expiry)
  │                                │  └─ Save PasswordResetToken to DB
  │                                │
  │                                ├─ EmailService.sendPasswordResetEmail()
  │                                │  ├─ Build reset link with token
  │                                │  │  └─ {passwordResetBaseUrl}?token={token}
  │                                │  ├─ Send via SMTP (Gmail)
  │                                │  └─ Return email sent status
  │                                │
  │                                └─ Return success message
  │
  │ ◀─ 200 OK ─────────────────────┤
  │  "If email exists..."           │
  │                                 │
  │  (User clicks email link)       │
  │                                 │
  ├─ POST /api/auth/reset-password ▶ AuthController.resetPassword()
  │  {token, newPassword}           │
  │                                ├─ PasswordResetService.resetPassword()
  │                                │  ├─ PasswordResetTokenRepository.findByToken()
  │                                │  ├─ Verify not expired
  │                                │  ├─ PasswordEncoder.encode(newPassword)
  │                                │  ├─ Update User.password in DB
  │                                │  └─ Delete reset token
  │                                │
  │                                └─ Return success
  │
  │ ◀─ 200 OK ─────────────────────┤
  │  "Password reset successful"    │
  │                                 │
  └─ User can now login ───────────┘
```

### Logout Flow

```
CLIENT                           SERVER
  │                                │
  ├─ POST /api/auth/logout ──────▶ AuthController.logout()
  │  Authorization: Bearer <token> │
  │                                ├─ Extract Authorization header
  │                                ├─ Parse JWT token
  │                                │
  │                                ├─ JwtService.getJtiFromToken()
  │                                │  └─ Extract JTI claim
  │                                │
  │                                ├─ BlacklistedTokenService.blacklistToken()
  │                                │  ├─ RedisService.set(key, "1", ttl)
  │                                │  │  └─ Write to Redis: blacklist:{jti}=1
  │                                │  ├─ Set TTL = token expiration epoch
  │                                │  └─ On Redis error: fail-closed (reject token)
  │                                │
  │                                ├─ JwtService.getEmailFromJwtToken()
  │                                │  └─ Extract email
  │                                │
  │                                ├─ RefreshTokenService.deleteByUserId()
  │                                │  └─ Delete user's refresh token
  │                                │
  │                                └─ Return success
  │
  │ ◀─ 200 OK ─────────────────────┤
  │  "Logged out successfully"      │
  │                                 │
  └─ Clear tokens locally ────────┘

  (Redis auto-expires blacklist:{jti} when TTL elapses)
```

## Data Model

### Entity Relationships

```
┌──────────────────────┐         ┌──────────────────────┐
│       users          │         │       roles          │
├──────────────────────┤         ├──────────────────────┤
│ id (PK, BIGSERIAL)   │         │ id (PK, BIGSERIAL)   │
│ username (VARCHAR)   │         │ name (VARCHAR)       │
│ email (UNIQUE)       │─────┬──▶│ └─ "ROLE_USER"       │
│ password (VARCHAR)   │     │   │ └─ "ROLE_PM"         │
│ full_name (VARCHAR)  │ M:M │   │ └─ "ROLE_ADMIN"      │
│ ◀─────────────────────┤     │   └──────────────────────┘
└─────────┬────────────┘     │
          │                  │ FK
          │ FK               │
          │ ┌────────────────┘
          │ │
          ▼ ▼
      (through user_roles)

┌──────────────────────┐     ┌──────────────────────────┐
│      user_roles      │     │   refresh_tokens         │
├──────────────────────┤     ├──────────────────────────┤
│ user_id (FK)         │     │ id (PK, BIGSERIAL)       │
│ role_id (FK)         │     │ token (VARCHAR, UNIQUE)  │
│ (PK: composite)      │     │ expiry_date (TIMESTAMP)  │
└──────────────────────┘     │ user_id (FK to users)    │
                             └──────────────────────────┘

┌───────────────────────────┐
│ password_reset_tokens     │
├───────────────────────────┤
│ id (PK, BIGSERIAL)        │
│ token (VARCHAR, UNIQUE)   │
│ expiry_date (TIMESTAMP)   │
│ user_id (FK to users)     │
└───────────────────────────┘

REDIS (Key-Value Store)
├──────────────────────────────────┐
│ blacklist:{jti} (key)            │
│ └─ value: 1 (presence check)     │
│ └─ TTL: token expiration epoch   │
│ └─ Auto-expires when TTL elapsed │
└──────────────────────────────────┘
```

### Security Context Representation

After successful login, SecurityContext contains:
```
SecurityContext
├─ Authentication
│  ├─ principal: UserPrinciple
│  │  ├─ id: 1
│  │  ├─ username: "john@example.com" (email used as Spring Security username)
│  │  ├─ password: "$2a$10$..." (BCrypt hash)
│  │  ├─ fullName: "John Doe"
│  │  └─ authorities: [GrantedAuthority("ROLE_USER"), GrantedAuthority("ROLE_PM")]
│  ├─ credentials: password (not stored after auth)
│  ├─ isAuthenticated: true
│  └─ details: WebAuthenticationDetails
│     └─ remoteAddress: client IP
└─ (No Session - Stateless JWT)
```

## JWT Token Structure

### HS512 Token Anatomy

```
eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJqb2huIiwiaWF0IjoxNjM4MzYwMDAwLCJleHAiOjE2MzgzNjAwMDB9.signature...
┌─────────────────────┬─────────────────────────────────────────────┬──────┐
│      HEADER         │               PAYLOAD                        │ SIGN │
└─────────────────────┴─────────────────────────────────────────────┴──────┘

HEADER (Base64URL-decoded):
{
  "alg": "HS512",
  "typ": "JWT"
}

PAYLOAD (Base64URL-decoded):
{
  "sub": "john@example.com",        // email (used as subject)
  "jti": "uuid-string",             // JWT ID (unique identifier)
  "iat": 1638360000,                // issued at (seconds)
  "exp": 1638360900                 // expiration (15min later)
}

SIGNATURE:
HMACSHA512(
  BASE64URL(HEADER) + "." + BASE64URL(PAYLOAD),
  "bezKoderSecretKey"
)
```

**Access Token Lifecycle:**
1. Generated at login with 15-minute expiration + unique JTI
2. Sent to client in response
3. Client stores token (localStorage, sessionStorage, HttpOnly cookie)
4. Client sends in Authorization header: `Bearer <token>`
5. Server validates signature, expiration, and blacklist on each request
6. Token expires after 15 minutes
7. Before expiration, client uses refresh token to obtain new pair

**Refresh Token Lifecycle:**
1. Generated at login with 7-day expiration
2. Sent to client in response
3. Client stores token separately from access token
4. Client sends to /api/auth/refresh-token endpoint when access token expires
5. Server validates expiration, generates new access + refresh token pair
6. Old refresh token is deleted (rotation on each use)
7. Provides 7 days of extended session before re-login required

**Token Revocation:**
- On logout: JTI added to blacklist_tokens table with expiration date
- Scheduled cleanup: hourly job deletes expired entries from blacklist
- Validation: checks if JTI in blacklist before accepting token

## Component Interactions

### Spring Security Filter Chain

```
Client Request
    ↓
Security Filter Chain
    ↓
┌─────────────────────────────────────────────────────────────┐
│  SPRING SECURITY FILTER CHAIN                                │
├─────────────────────────────────────────────────────────────┤
│  1. FilterSecurityInterceptor (method-level @PreAuthorize)  │
│     └─ Checks role requirements                              │
│  2. ExceptionTranslationFilter (handles auth errors)         │
│     └─ Converts exceptions to HTTP responses                │
│  3. CustomAccessDeniedHandler (custom exception handler)     │
│     └─ Returns 403 JSON on access denied                     │
│  4. JwtAuthenticationFilter (custom, added before #5)        │
│     └─ Extracts & validates JWT, sets SecurityContext       │
│  5. UsernamePasswordAuthenticationFilter (for login POST)    │
│     └─ Authenticates username/password                       │
│  6. BasicAuthenticationFilter (HTTP Basic, disabled here)    │
│  7. CSRF Protection Filter (disabled for JWT API)            │
│  8. CORS Filter (enabled)                                    │
│  9. SessionManagementFilter (creates session if needed)      │
│     └─ Disabled here (STATELESS policy)                      │
└─────────────────────────────────────────────────────────────┘
    ↓
DispatcherServlet
    ↓
Controller Handler
    ↓
Response
```

**Custom Configuration in SecurityConfig:**
```java
http.authorizeRequests()
    .antMatchers("/api/auth/**").permitAll()  // Public endpoints
    .anyRequest().authenticated()              // All others require auth

http.addFilterBefore(
    jwtAuthenticationFilter(),
    UsernamePasswordAuthenticationFilter.class
)  // Add JWT filter before standard filter

http.sessionManagement()
    .sessionCreationPolicy(SessionCreationPolicy.STATELESS)  // No sessions

http.cors()  // Enable CORS
```

## Deployment Architecture

### Docker Compose Setup

```
┌──────────────────────────────────────────────────────────┐
│                  DOCKER HOST                              │
├──────────────────────────────────────────────────────────┤
│                                                            │
│  ┌──────────────────┐  ┌──────────────┐  ┌──────────────┐ │
│  │postgres-service  │  │redis-service │  │ ms-auth-     │ │
│  │(postgres:13.1)   │  │ (redis:latest)   │ service      │ │
│  │                  │  │              │  │ (built)      │ │
│  │ Port: 5432       │  │Port: 6379    │  │Port: 8080    │ │
│  │ Env:             │  │              │  │Base:openjdk11│ │
│  │ POSTGRES_USER    │  │              │  │COPY .jar     │ │
│  │ POSTGRES_PASSWORD│  │              │  │CMD: java -jar│ │
│  │                  │  │              │  │              │ │
│  │ Volume:          │  │              │  │depends_on:   │ │
│  │ /docker/volumes/ │  │              │  │postgres,redis│ │
│  │                  │  │              │  │              │ │
│  └────────┬─────────┘  └────────┬─────┘  └──────┬───────┘ │
│           │                     │                │         │
│           └─────────────────────┼────────────────┘         │
│                 Network Bridge (my-net)                    │
│                                                            │
└──────────────────────────────────────────────────────────┘
     ▲                    ▲                    ▲
     │                    │                    │
     │ 5432               │ 6379               │ 8080
     │ (PostgreSQL)       │ (Redis)            │ (HTTP)
     │                    │                    │
HOST MACHINE
```

### Runtime Environment

```
┌────────────────────────────────────────┐
│     Docker Container                   │
│  (ms-authentication-service)           │
├────────────────────────────────────────┤
│  OpenJDK 11                             │
│  ├─ Java Heap Memory: configurable      │
│  ├─ GC: G1GC (default)                  │
│  └─ JVM args: customizable              │
│                                         │
│  Spring Boot Application                │
│  ├─ Embedded Tomcat (port 8080)         │
│  ├─ Spring Boot: 2.6.4                  │
│  ├─ Spring Security: configured         │
│  └─ Logging: stdout/stderr              │
│                                         │
│  Application Properties                 │
│  ├─ JWT Secret: bezKoderSecretKey       │
│  ├─ JWT Expiration: 86400000ms (24h)    │
│  ├─ Database: PostgreSQL (via network)  │
│  └─ CORS: enabled for all origins       │
└────────────────────────────────────────┘
        ▲
        │ DB Connection Pool
        │ (HikariCP default)
        ▼
PostgreSQL Container
```

## Security Architecture

### Authentication Mechanisms

| Mechanism | Implementation | Purpose |
|-----------|-----------------|---------|
| Password Encoding | BCryptPasswordEncoder | Secure password storage |
| JWT Generation | JJWT 0.9.0 HS512 | Token-based auth |
| Token Validation | JJWT parser | Signature & expiration verification |
| Authorization | Spring Security @PreAuthorize | Role-based access control |
| Session Management | STATELESS | No server-side session storage |

### Security Boundaries

```
┌──────────────────────────────────────────────────────────┐
│  PUBLIC ZONE                                              │
│  ┌──────────────────────────────────────────────────┐   │
│  │ POST /api/auth/login      (no auth required)     │   │
│  │ POST /api/auth/register   (no auth required)     │   │
│  │ POST /api/auth/forgot-password (no auth req)     │   │
│  │ POST /api/auth/reset-password  (token only)      │   │
│  │ POST /api/auth/refresh-token   (refresh token)   │   │
│  └──────────────────────────────────────────────────┘   │
└────────────────────┬─────────────────────────────────────┘
                     │ Client obtains access + refresh tokens
                     ▼
┌──────────────────────────────────────────────────────────┐
│  PROTECTED ZONE                                           │
│  ┌───────────────────────────────────────────────────┐  │
│  │ All other endpoints require:                      │  │
│  │ 1. Authorization: Bearer <accessToken> header     │  │
│  │ 2. Valid token signature (HS512)                  │  │
│  │ 3. Token not expired (15 min)                     │  │
│  │ 4. JTI not in blacklist (logout check)            │  │
│  │ 5. @PreAuthorize role checks                      │  │
│  │                                                   │  │
│  │ POST /api/auth/logout                             │  │
│  │ ├─ Requires: Bearer accessToken                   │  │
│  │ ├─ Blacklists: JTI of current token               │  │
│  │ └─ Deletes: user's refresh token                  │  │
│  └───────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────┘
```

### Security Improvements Implemented

1. **Token Refresh:** Refresh tokens with rotation (7-day expiration, new token on refresh)
2. **Token Revocation:** JTI-based blacklisting with scheduled cleanup
3. **Password Reset:** Email-driven reset flow with 24-hour token expiration
4. **Email Validation:** Required and unique email on registration
5. **Access Token Expiration:** Shortened to 15 minutes for reduced exposure

### Potential Security Improvements (Future)

1. **Rate Limiting:** Add rate limiter on /api/auth/login to prevent brute force
2. **Email Verification:** Verify email ownership during registration
3. **Audit Logging:** Log all authentication attempts with IP, timestamp, success/failure
4. **HTTPS Only:** Enforce HTTPS in production (JWT in Authorization header, not cookies)
5. **Secret Management:** Use vault service (HashiCorp Vault, AWS Secrets Manager) instead of config file
6. **Algorithm Upgrade:** Upgrade JJWT to 0.12.x for newer JWT standards compliance
7. **Asymmetric Keys:** Consider RS256 for distributed systems where multiple services validate tokens
8. **Two-Factor Auth:** SMS or authenticator app for sensitive operations
9. **Token Encryption:** Encrypt refresh tokens in transit/storage
10. **IP Whitelisting:** Restrict access from known IP ranges

## Scaling Considerations

### Horizontal Scaling

Current design supports horizontal scaling:
- **Stateless JWT:** No session affinity required
- **Shared JWT Secret:** All instances use same jwtSecret from config
- **Database-Backed:** PostgreSQL handles concurrent connections (connection pooling)
- **No Local Cache:** Token validation doesn't require distributed cache

**Deployment:**
```
┌─────────┐
│ Load    │
│ Balancer│
└────┬────┘
     │
  ┌──┴──┐
  │     │
┌─┴─┐ ┌┴──┐
│API│ │API│  (multiple instances)
│ 1 │ │ 2 │  (all share same jwtSecret)
└─┬─┘ └┬──┘  (all connect to same DB)
  │     │
  └─────┴─────────→ PostgreSQL
```

### Performance Characteristics

| Operation | Latency | Bottleneck |
|-----------|---------|-----------|
| Login (authenticate + generate token) | ~100-200ms | Database query for user |
| Token Validation (on each request) | ~5-15ms | JWT parsing + Redis blacklist check |
| Token Logout (blacklist) | ~2-5ms | Redis SET operation with TTL |
| Register (create user + roles) | ~150-300ms | Multiple DB transactions |
| Authorization Check (@PreAuthorize) | ~1-2ms | In-memory role lookup |

**Optimization Opportunities:**
- Implement user cache (Redis) to reduce DB queries on loadUserByUsername
- Use connection pooling (HikariCP default)
- Add database indexes on username, role name
- Redis connection pooling (Lettuce default)
- Consider Redis Cluster for horizontal blacklist scaling

## Monitoring & Observability

### Logging Strategy

| Component | Log Level | Information |
|-----------|-----------|-------------|
| JwtService | DEBUG | Token validation failures, claim extraction |
| AuthController | INFO | Login/register attempts |
| SecurityConfig | DEBUG | Filter chain configuration |
| Hibernate | DEBUG | SQL queries, transaction details |
| Application | DEBUG | Custom business logic |

### Health Checks (Future)

```
GET /actuator/health
{
  "status": "UP",
  "components": {
    "db": {"status": "UP"},
    "diskSpace": {"status": "UP"},
    "livenessState": {"status": "UP"},
    "readinessState": {"status": "UP"}
  }
}
```

### Metrics (Future)

Suggested metrics to track:
- Login success/failure rate
- Token validation success/failure rate
- Average response time per endpoint
- Database connection pool utilization
- JWT signature verification failures (potential attacks)
- Unauthorized access attempts (403 responses)

## Technology Stack Summary

| Layer | Technology | Version | Role |
|-------|-----------|---------|------|
| Framework | Spring Boot | 2.6.4 | Application container |
| Security | Spring Security | via Boot | Authentication/Authorization |
| ORM | Spring Data JPA | via Boot | Object-relational mapping |
| JWT | JJWT | 0.9.0 | Token generation/validation |
| Password Hash | BCrypt | via Spring | Secure password encoding |
| Database | PostgreSQL | 13.1+ | Data persistence (users, roles, refresh/reset tokens) |
| Cache/Blacklist | Redis | 7.x | Token blacklist (JTI) with auto-TTL |
| Container | Docker | latest | Deployment container |
| Runtime | OpenJDK | 11 | Java runtime |

## Integration & Service Layer Architecture

### RedisService Abstraction Layer

**Purpose:** Unified interface for all Redis operations, reducing coupling between domain services and Redis.

**Service Layer Boundaries:**

```
Domain Services (Authentication, Token, etc)
         │
         ▼
    RedisService (interface)
         │
         ├─ RedisServiceImpl (implementation)
         │  ├─ StringRedisTemplate
         │  └─ RedisTemplate<String, Object>
         │
    RedisKeyPrefix (constants)
         │
         ▼
    Spring Data Redis
         │
         ▼
    Redis Server (TCP/6379)
```

**Usage Example (Token Blacklist):**
- BlacklistedTokenServiceImpl uses RedisService (not raw StringRedisTemplate)
- Uses RedisKeyPrefix.BLACKLIST constant for key prefix
- Calls: `redisService.set(key, "1", ttlSeconds, TimeUnit.SECONDS)`
- Error handling: Try-catch wraps all operations; fail-closed on errors

**Benefits:**
- Single point of configuration (RedisConfig)
- Consistent error handling across all Redis operations
- Easy to switch Redis implementations (Lettuce/Jedis)
- Key namespacing prevents collisions (RedisKeyPrefix)
- Reusable for future features (caching, rate limiting, distributed locks)

## Dependency Graph

```
Application
├─ Spring Boot Core
│  ├─ Spring Security
│  │  └─ Spring Core (AOP, DI)
│  ├─ Spring Data JPA
│  │  └─ Hibernate
│  │     └─ PostgreSQL Driver
│  ├─ Spring Data Redis
│  │  └─ Lettuce (Redis client)
│  ├─ Spring Web (MVC, REST)
│  │  └─ Embedded Tomcat
│  └─ Logging (SLF4J → Log4j2)
│
├─ JJWT
│  └─ Jackson (JSON parsing)
│
├─ Lombok (annotation processor)
│
├─ PostgreSQL Driver
│  └─ JDBC
│
└─ Redis Client (via Spring Data Redis)
   └─ Lettuce
```

## Architecture Decisions Rationale

| Decision | Chosen | Rationale | Trade-off |
|----------|--------|-----------|-----------|
| Stateless vs Sessions | Stateless JWT | Microservices-ready, no server state | Larger token, can't invalidate early without blacklist |
| HS512 vs RS256 | HS512 | Simpler operations, all servers share secret | Less secure for distributed trust |
| Eager vs Lazy Roles | Eager | Roles needed in SecurityContext immediately | Always loads roles even if unused |
| Manual vs Auto Schema | Manual | Version control, database as source of truth | Extra maintenance burden |
| Single DB vs Sharding | Single | Simpler for now, YAGNI principle | Scalability ceiling at DB level |
| Blacklist Storage | Redis | Fast O(1) lookup, auto-TTL eliminates cleanup jobs | New infrastructure dependency |
| Blacklist Error Handling | Fail-Closed | Conservative security: reject token if Redis unavailable | May block legitimate requests during outage |

## Future Architecture Evolution

### Phase 2: Microservices-Ready
- Extract as library/service
- Add OpenAPI/Swagger documentation
- Implement rate limiting middleware
- Add audit logging service

### Phase 3: Enterprise Features
- Multi-tenancy support
- Advanced role model (permissions, resource-based)
- Token refresh mechanism
- OAuth2/SAML integration
- Distributed tracing (Jaeger, Zipkin)

### Phase 4: Cloud-Native
- Kubernetes deployment manifests
- ConfigMap for secrets management
- Health checks & readiness probes
- Metrics export (Prometheus)
- Centralized logging (ELK/Splunk)
