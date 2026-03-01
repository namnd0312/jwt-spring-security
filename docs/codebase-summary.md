# Codebase Summary

**Project:** jwt-spring-security
**Generated:** February 2026
**Total Java Files:** 38 (main) + 1 (test)
**Total Lines of Code (approx):** ~4,200 LOC

## Directory Structure

```
jwt-spring-security/
├── src/
│   ├── main/
│   │   ├── java/com/namnd/springjwt/
│   │   │   ├── SpringJwtApplication.java (35 lines)
│   │   │   ├── ServletInitializer.java (12 lines)
│   │   │   ├── config/
│   │   │   │   ├── security/SecurityConfig.java (82 lines)
│   │   │   │   ├── filter/JwtAuthenticationFilter.java (~60 lines)
│   │   │   │   └── custom/CustomAccesDeniedHandler.java (~30 lines)
│   │   │   ├── controller/
│   │   │   │   └── AuthController.java (96 lines)
│   │   │   ├── model/
│   │   │   │   ├── User.java (29 lines)
│   │   │   │   ├── Role.java (~20 lines)
│   │   │   │   └── UserPrinciple.java (~80 lines)
│   │   │   ├── dto/
│   │   │   │   ├── JwtResponseDto.java (~40 lines)
│   │   │   │   ├── RegisterDto.java (~30 lines)
│   │   │   │   └── mapper/RegisterDtoMapper.java (~40 lines)
│   │   │   ├── service/
│   │   │   │   ├── JwtService.java (61 lines)
│   │   │   │   ├── UserService.java (~20 lines interface)
│   │   │   │   ├── RoleService.java (~15 lines interface)
│   │   │   │   └── impl/
│   │   │   │       ├── UserServiceImpl.java (~50 lines)
│   │   │   │       └── RoleServiceImpl.java (~30 lines)
│   │   │   └── repository/
│   │   │       ├── UserRepository.java (interface)
│   │   │       └── RoleRepository.java (interface)
│   │   └── resources/
│   │       ├── application.yml (46 lines)
│   │       └── roles.sql (3 lines)
│   └── test/
│       └── SpringJwtApplicationTests.java (15 lines)
├── pom.xml (92 lines)
├── Dockerfile (18 lines)
├── docker-compose.yml (36 lines)
├── README.md
└── docs/
```

## Core Components

### 1. Application Entry Point

**SpringJwtApplication.java** (35 lines)
- `@SpringBootApplication` main entry point
- Scans packages under com.namnd.springjwt
- Runs on port 8080 via application.yml

**ServletInitializer.java** (12 lines)
- Extends SpringBootServletInitializer
- Enables WAR deployment to Tomcat
- Returns SpringBootServletInitializer.configure(application)

### 2. Configuration Classes

**RedisConfig.java** (24 lines)
- @Configuration class
- Provides RedisTemplate<String, Object> bean
- Configures serializers: StringRedisSerializer for keys, Jackson2JsonRedisSerializer for values
- Auto-wired by Spring into RedisServiceImpl

**SecurityConfig.java** (82 lines)
- Extends WebSecurityConfigurerAdapter
- Annotations: @Configuration, @EnableWebSecurity, @EnableGlobalMethodSecurity(prePostEnabled=true)
- **Beans:**
  - `jwtAuthenticationFilter()` - Creates JWT filter
  - `authenticationManagerBean()` - Exposes AuthenticationManager
  - `customAccesDeniedHandler()` - Custom 403 handler
  - `passwordEncoder()` - BCryptPasswordEncoder bean
- **configure(AuthenticationManagerBuilder):** Wires UserService + PasswordEncoder
- **configure(HttpSecurity):**
  - Permits: /api/auth/** (public)
  - Requires auth: all other endpoints
  - CSRF disabled
  - Adds JwtAuthenticationFilter before UsernamePasswordAuthenticationFilter
  - Session policy: STATELESS
  - CORS enabled
  - Exception handling via CustomAccesDeniedHandler

**RedisKeyPrefix.java** (15 lines)
- @final utility class with constants
- BLACKLIST = "blacklist:" (JWT token blacklist prefix)
- LOCK = "lock:" (distributed lock prefix)
- Prevents Redis key collisions across features

**JwtAuthenticationFilter.java** (~60 lines)
- Extends OncePerRequestFilter
- Implements filter per request
- **doFilterInternal():**
  - Extracts "Authorization" header
  - Strips "Bearer " prefix
  - Calls JwtService.validateJwtToken()
  - On valid token: loads UserDetails via UserService, sets SecurityContext
  - On invalid: clears SecurityContext, continues filter chain
  - Catches exceptions, logs errors

**CustomAccesDeniedHandler.java** (~30 lines)
- Implements AccessDeniedHandler
- **handle():** Returns 403 with JSON error on access denied
- Prevents Spring default redirect behavior

### 3. REST Controller

**AuthController.java** (~197 lines)
- Route: `/api/auth`
- Annotations: @CrossOrigin(origins="*", maxAge=3600), @RestController, @RequestMapping
- **Endpoints:**
  - **POST /login** (accepts LoginRequestDto with email+password, returns JwtResponseDto)
    - Authenticates via AuthenticationManager using email as principal
    - Generates access token + refresh token
    - Returns id, token, refreshToken, email, username, name, roles
  - **POST /register** (accepts RegisterDto, returns String)
    - Validates email uniqueness only (duplicate usernames allowed)
    - Validates email required field
    - Encodes password, creates/assigns roles
    - Saves user via UserService
  - **POST /forgot-password** (accepts ForgotPasswordDto)
    - Generates password reset token
    - Sends reset link via email
    - Returns generic success message (security)
  - **POST /reset-password** (accepts ResetPasswordDto)
    - Validates reset token expiration
    - Encodes new password
    - Updates user password
  - **POST /refresh-token** (accepts RefreshTokenRequestDto)
    - Validates refresh token existence & expiration
    - Generates new access token
    - Rotates refresh token (creates new one)
  - **POST /logout** (requires Bearer token)
    - Extracts JWT and JTI
    - Blacklists JTI with expiration date
    - Deletes user's refresh token

### 4. Data Models

**User.java** (~35 lines)
- @Entity, @Data (Lombok)
- **Table:** users
- **Columns:**
  - id (BIGSERIAL, GenerationType.IDENTITY)
  - username (String, not unique - duplicates allowed)
  - email (String, unique)
  - password (String, BCrypt-encoded)
  - fullName (String)
  - roles (Set<Role>, ManyToMany eager, JoinTable user_roles)

**RefreshToken.java** (~30 lines)
- @Entity, @Data (Lombok)
- **Table:** refresh_tokens
- **Columns:**
  - id (BIGSERIAL)
  - token (String, unique)
  - expiryDate (LocalDateTime)
  - user (User, ManyToOne)
- **Methods:**
  - isExpired() - checks if token past expiration

**PasswordResetToken.java** (~30 lines)
- @Entity, @Data (Lombok)
- **Table:** password_reset_tokens
- **Columns:**
  - id (BIGSERIAL)
  - token (String, unique)
  - expiryDate (LocalDateTime)
  - user (User, ManyToOne)
- **Methods:**
  - isExpired() - checks token validity


**Role.java** (~20 lines)
- @Entity, @Data (Lombok)
- **Table:** roles
- **Columns:**
  - id (BIGSERIAL, GenerationType.IDENTITY)
  - name (String, e.g., "ROLE_USER", "ROLE_PM", "ROLE_ADMIN")
- **Relationships:** Many-to-many with User

**UserPrinciple.java** (~80 lines)
- Implements UserDetails (Spring Security interface)
- Wraps User entity for Spring Security
- **Fields:**
  - id, username, password, fullName, roles (from User)
  - authorities (derived from roles)
- **Methods:**
  - getAuthorities() - Returns GrantedAuthority collection from Role names
  - getUsername(), getPassword() - Simple getters
  - isAccountNonExpired(), isAccountNonLocked(), isCredentialsNonExpired(), isEnabled() - All return true

### 5. Data Transfer Objects

**JwtResponseDto.java** (~45 lines)
- **Fields:** id (Long), token (String), refreshToken (String), email (String), username (String), name (String), roles (Collection<? extends GrantedAuthority>)
- **Purpose:** Response payload for login endpoint

**LoginRequestDto.java** (~15 lines)
- **Fields:** email (String), password (String)
- **Purpose:** Request payload for login endpoint (replaces raw User entity)

**RegisterDto.java** (~35 lines)
- **Fields:** username (String), email (String), password (String), fullName (String), roles (Set<Role>)
- **Purpose:** Request payload for register endpoint (email now required)

**ForgotPasswordDto.java** (~15 lines)
- **Fields:** email (String)
- **Purpose:** Request payload for password reset initiation

**ResetPasswordDto.java** (~20 lines)
- **Fields:** token (String), newPassword (String)
- **Purpose:** Request payload for password reset completion

**RefreshTokenRequestDto.java** (~15 lines)
- **Fields:** refreshToken (String)
- **Purpose:** Request payload for token refresh

**TokenRefreshResponseDto.java** (~20 lines)
- **Fields:** accessToken (String), refreshToken (String)
- **Purpose:** Response payload for token refresh endpoint

**RegisterDtoMapper.java** (~40 lines)
- Maps RegisterDto → User entity
- Encodes password via PasswordEncoder (BCrypt)
- Copies username, email, password (encoded), fullName, roles

### 6. Services

**JwtService.java** (~120 lines)
- @Component
- Injected: @Value("${namnd.app.jwtSecret}"), @Value("${namnd.app.jwtExpiration}"), @Value("${namnd.app.jwtRefreshExpiration}")
- **Methods:**
  - `generateTokenLogin(Authentication)` - Generates 15-min access token with JTI (sub = email)
  - `generateTokenFromEmail(String)` - Generates access token from email (used on token refresh)
  - `validateJwtToken(String)` - Validates signature & expiration
  - `validateJwtTokenWithBlacklist(String)` - Validates & checks Redis blacklist
  - `getEmailFromJwtToken(String)` - Extracts email from JWT sub claim
  - `getJtiFromToken(String)` - Extracts JTI (JWT ID) claim
  - `getExpirationFromToken(String)` - Extracts expiration date
- **Note:** JWT sub claim = email (not username). No scheduled cleanup (Redis auto-TTL)

**RefreshTokenService.java** (interface)
- **Methods:**
  - createRefreshToken(Long userId) - Creates new 7-day token
  - findByToken(String) - Optional lookup
  - verifyExpiration(RefreshToken) - Validates & returns token
  - deleteByUserId(Long) - Deletes user's refresh token

**RefreshTokenServiceImpl.java** (~60 lines)
- @Service, injected RefreshTokenRepository, UserRepository
- Implements RefreshTokenService
- Token rotation on refresh (creates new token, old deleted)

**PasswordResetService.java** (interface)
- **Methods:**
  - createPasswordResetToken(String email) - Creates token, sends email
  - resetPassword(String token, String newPassword) - Validates & updates

**PasswordResetServiceImpl.java** (~80 lines)
- @Service, injected repositories, UserService, EmailService
- Generates 24-hour reset tokens
- Sends reset links via email

**EmailService.java** (interface)
- **Methods:**
  - sendPasswordResetEmail(String email, String resetLink) - Sends via SMTP

**EmailServiceImpl.java** (~40 lines)
- @Service, uses JavaMailSender
- Sends HTML-formatted reset emails
- Spring Mail configuration from application.yml

**BlacklistedTokenService.java** (interface)
- **Methods:**
  - blacklistToken(String jti, Date expiry) - Adds to Redis blacklist
  - isTokenBlacklisted(String jti) - Checks Redis membership

**BlacklistedTokenServiceImpl.java** (~50 lines)
- @Service, injected RedisService
- Uses RedisKeyPrefix.BLACKLIST constant + JTI
- Sets auto-TTL based on token expiry date via RedisService.set()
- Fail-closed error handling: returns true on Redis outage (reject token)

**RedisService.java** (interface, ~46 lines)
- Shared utility for all Redis operations
- **Key-Value ops:** set, get, delete, hasKey, expire, getExpire
- **Hash ops:** hSet, hGet, hGetAll, hDelete, hHasKey
- **List ops:** lPush, rPush, lRange, lLen
- **Set ops:** sAdd, sMembers, sIsMember, sRemove
- **Pub/Sub:** publish(channel, message)
- **Distributed Lock:** tryLock, unlock

**RedisServiceImpl.java** (~276 lines)
- @Service, implements RedisService
- Injected: StringRedisTemplate, RedisTemplate<String, Object>
- Try-catch error handling on every method (fail-safe returns)
- Uses Jackson2JsonRedisSerializer for JSON serialization

**UserService.java** (interface, ~20 lines)
- Extends UserDetailsService (Spring Security)
- **Methods:**
  - save(User)
  - findByEmail(String) - Returns Optional<User>
  - existsByEmail(String) - Returns boolean

**UserServiceImpl.java** (~50 lines)
- @Service, implements UserService
- Injected: UserRepository, PasswordEncoder
- **Methods:**
  - save(User) - Delegates to repo.save()
  - findByEmail(String) - Calls repo.findByEmail()
  - existsByEmail(String) - Calls repo.existsByEmail()
  - loadUserByUsername(String) (from UserDetailsService)
    - Queries by email (parameter is email, not username)
    - Returns UserPrinciple wrapping user

**RoleService.java** (interface, ~15 lines)
- **Methods:**
  - save(Role)
  - findByName(String) - Returns Role or null
  - flush()

**RoleServiceImpl.java** (~30 lines)
- @Service, implements RoleService
- Injected: RoleRepository
- Delegates all methods to RoleRepository

### 7. Repositories

**UserRepository.java** (interface)
- Extends JpaRepository<User, Long>
- **Methods:**
  - Optional<User> findByUsername(String) - exists but not used for login
  - boolean existsByUsername(String) - exists but not used for registration check
  - Optional<User> findByEmail(String) - primary lookup (login, password reset)
  - boolean existsByEmail(String) - used for registration uniqueness check

**RoleRepository.java** (interface)
- Extends JpaRepository<Role, Long>
- **Methods:**
  - Role findByName(String)

**RefreshTokenRepository.java** (interface)
- Extends JpaRepository<RefreshToken, Long>
- **Methods:**
  - Optional<RefreshToken> findByToken(String)
  - void deleteByUserId(Long userId)

**PasswordResetTokenRepository.java** (interface)
- Extends JpaRepository<PasswordResetToken, Long>
- **Methods:**
  - Optional<PasswordResetToken> findByToken(String)
  - void deleteByUserId(Long userId)


## Configuration Files

**pom.xml** (92 lines)
- Group: com.namnd
- Artifact: spring-jwt
- Version: 0.0.1-SNAPSHOT
- Packaging: war
- Java: 1.8
- **Dependencies:**
  - spring-boot-starter-data-jpa
  - spring-boot-starter-security
  - spring-boot-starter-web
  - spring-boot-starter-data-redis
  - spring-boot-devtools (runtime)
  - postgresql (runtime)
  - lombok (optional)
  - spring-boot-starter-tomcat (provided, for WAR)
  - spring-boot-starter-test (test scope)
  - spring-security-test (test scope)
  - jjwt 0.9.0
- **Plugins:**
  - spring-boot-maven-plugin (excludes Lombok)

**application.yml** (~51 lines)
- server.port: 8080
- spring.jpa.hibernate.ddl-auto: create-drop (development), should be none (production)
- spring.jpa.show-sql: true
- spring.datasource.url: jdbc:postgresql://localhost:5432/testdb
- spring.datasource.username: postgres
- spring.datasource.password: postgres
- spring.redis.host: localhost
- spring.redis.port: 6379
- spring.mail.host: smtp.gmail.com, port: 587 (for password reset emails)
- namnd.app.jwtSecret: bezKoderSecretKey
- namnd.app.jwtExpiration: 900000 (15 minutes in milliseconds)
- namnd.app.jwtRefreshExpiration: 604800000 (7 days in milliseconds)
- namnd.app.passwordResetBaseUrl: http://localhost:3000/reset-password
- logging: DEBUG for com.namnd.springjwt, SQL queries

**roles.sql** (3 lines)
- INSERT INTO roles: ROLE_USER, ROLE_PM, ROLE_ADMIN

**Dockerfile** (18 lines)
- Base: openjdk:11
- Copies target/spring-jwt.jar to /opt/app/spring-jwt.jar
- ENTRYPOINT: java -jar spring-jwt.jar

**docker-compose.yml** (36 lines)
- Services:
  - postgres-service (postgres:13.1-alpine, port 5432)
  - redis-service (redis:latest, port 6379)
  - ms-authentication-service (builds from Dockerfile, port 8080, depends_on postgres, redis)
- Network: my-net (bridge)
- Volumes: /Users/admin/Desktop/DEV/DOCKER/docker-volumes (persistent)
- Restart policy: unless-stopped

## Key Design Patterns

| Pattern | Location | Purpose |
|---------|----------|---------|
| Service Locator | SecurityConfig | Wires AuthenticationManager, PasswordEncoder |
| Data Mapper | RegisterDtoMapper | DTO → Entity conversion |
| Adapter | UserPrinciple | Adapts User to UserDetails |
| Filter Chain | JwtAuthenticationFilter | JWT extraction & validation |
| Repository | UserRepository, RoleRepository | Data access abstraction |
| Dependency Injection | Throughout (@Autowired, @Inject) | Loose coupling |

## Code Metrics

| Metric | Value |
|--------|-------|
| Total Java Classes | 38 |
| Total Interfaces | 8 (UserService, RoleService, JwtService, RefreshTokenService, PasswordResetService, EmailService, BlacklistedTokenService) |
| Total Enums | 0 |
| Largest Class | AuthController (~197 lines) |
| New Entities | 3 (RefreshToken, PasswordResetToken) |
| New Services | 6 (RefreshTokenService, PasswordResetService, EmailService, BlacklistedTokenService, RedisService) |
| New Service Impls | 4 (RefreshTokenServiceImpl, PasswordResetServiceImpl, EmailServiceImpl, BlacklistedTokenServiceImpl, RedisServiceImpl) |
| New Config Classes | 2 (RedisConfig, RedisKeyPrefix) |
| New Repositories | 2 (RefreshTokenRepository, PasswordResetTokenRepository) |
| New DTOs | 4 (ForgotPasswordDto, ResetPasswordDto, RefreshTokenRequestDto, TokenRefreshResponseDto) |
| Package Depth | 3-4 levels (com.namnd.springjwt.{service.impl, dto.mapper, config, util}) |
| Scheduled Tasks | 0 (Redis auto-TTL replaces scheduled cleanup) |
| Test Coverage | 1 smoke test (SpringJwtApplicationTests) |

## External Dependencies

| Dependency | Version | Size (MB) | Security Status |
|------------|---------|-----------|-----------------|
| Spring Boot | 2.6.4 | - | LTS, active maintenance |
| Spring Security | included | - | Active maintenance |
| Spring Mail | included | - | Active maintenance |
| JJWT | 0.9.0 | ~0.1 | Stable (newer: 0.11.x, 0.12.x available) |
| PostgreSQL Driver | ~42.x | ~0.9 | Latest |
| Redis | via Spring Data Redis | ~7.x | NoSQL cache for token blacklist |
| Lombok | 1.18.30 | ~1.8 | JDK 21 compatible |
| JavaMail | included | - | JDK built-in |

## Build & Artifact

**Maven Build:**
```
mvn clean install
→ Builds: target/spring-jwt.war, target/spring-jwt.jar
```

**Docker Image:**
```
docker build -t ms-authentication-service .
→ Image: OpenJDK 11 + spring-jwt.jar
→ Size: ~500MB (approx, depends on build)
```

## Code Quality Observations

**Strengths:**
- Clean separation of concerns (controller → service → repository)
- Standard Spring Security patterns
- Lombok reduces boilerplate
- Stateless design with token rotation supports scaling
- Token refresh mechanism with rotation
- Redis-based JTI blacklisting for logout (no scheduled cleanup needed)
- Email-driven password reset flow
- Auto-TTL on Redis keys eliminates data cleanup jobs
- Configurable token expiration times
- Email validation required on registration
- Fail-closed blacklist error handling (rejects tokens on Redis outage)

**Areas for Improvement:**
- Limited test coverage (1 smoke test)
- No validation annotations (@Valid, @NotNull) on DTOs
- Hardcoded jwtSecret in application.yml (should use env var)
- No rate limiting on login endpoint
- Password reset tokens need stronger entropy
- Spring Mail credentials in config (use env vars)
- Manual schema management (no migrations tracked)
- Email service error handling could be more robust
- No audit logging for sensitive operations
- Redis connection resilience could be enhanced (circuit breaker)

## Deployment Artifacts

**WAR File:**
- Target: target/spring-jwt.war
- Supports: Tomcat, JBoss, other servlet containers

**JAR File:**
- Target: target/spring-jwt.jar
- Supports: Docker, standalone JVM execution

**Docker Image:**
- Base: openjdk:11
- Includes: Spring Boot JAR + embedded Tomcat

## Integration Points

| System | Integration | Type |
|--------|-----------|------|
| PostgreSQL | Database | Synchronous (JDBC) |
| Redis | Token Blacklist Cache | Synchronous (TCP) |
| Spring Security | Authentication | Internal |
| Spring Data JPA | ORM | Internal |
| Log4j (via Spring) | Logging | Asynchronous |
| Java Security | Crypto (BCrypt) | Internal |

## Future Expansion Points

1. **Two-Factor Authentication:** SMS or authenticator app support
2. **OAuth2/Social Login:** Google, GitHub, Facebook integration
3. **Advanced Roles:** Implement permissions model (Role → Permission mapping)
4. **Audit Logging:** Track login/register/password reset/logout attempts
5. **Rate Limiting:** Prevent brute force on login/forgot-password
6. **Email Verification:** Verify email on registration
7. **Token Encryption:** Add encryption layer to refresh tokens
8. **API Gateway:** Kong, Spring Cloud Gateway wrapper
9. **Admin Dashboard:** User/role management UI
10. **Token Introspection:** Endpoint to check token validity
