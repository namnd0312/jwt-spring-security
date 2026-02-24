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

### 2. Security Configuration

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
  - **POST /login** (accepts User, returns JwtResponseDto)
    - Authenticates via AuthenticationManager
    - Generates access token + refresh token
    - Returns id, token, refreshToken, username, name, roles
  - **POST /register** (accepts RegisterDto, returns String)
    - Validates username & email uniqueness
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
  - username (String, unique)
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

**BlacklistedToken.java** (~25 lines)
- @Entity, @Data (Lombok)
- **Table:** blacklisted_tokens
- **Columns:**
  - id (BIGSERIAL)
  - jti (String, unique) - JWT ID claim
  - expiryDate (LocalDateTime)

**TokenType.java** (enum)
- ACCESS, REFRESH - discriminator for token types

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
- **Fields:** id (Long), token (String), refreshToken (String), username (String), name (String), roles (Collection<? extends GrantedAuthority>)
- **Purpose:** Response payload for login endpoint

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
- @Component, @EnableScheduling
- Injected: @Value("${namnd.app.jwtSecret}"), @Value("${namnd.app.jwtExpiration}"), @Value("${namnd.app.jwtRefreshExpiration}")
- **Methods:**
  - `generateTokenLogin(Authentication)` - Generates 15-min access token with JTI
  - `generateTokenFromUsername(String)` - Generates access token from username
  - `validateJwtToken(String)` - Validates signature & expiration
  - `validateJwtTokenWithBlacklist(String)` - Validates & checks blacklist
  - `getUserNameFromJwtToken(String)` - Extracts username claim
  - `getJtiFromToken(String)` - Extracts JTI (JWT ID) claim
  - `getExpirationFromToken(String)` - Extracts expiration date
- **Scheduled Tasks:**
  - `cleanupExpiredBlacklistedTokens()` - Runs hourly, deletes expired entries

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
  - blacklistToken(String jti, Date expiry) - Adds to blacklist
  - isBlacklisted(String jti) - Checks membership

**BlacklistedTokenServiceImpl.java** (~30 lines)
- @Service, injected BlacklistedTokenRepository
- Simple save/find operations

**UserService.java** (interface, ~20 lines)
- Extends UserDetailsService (Spring Security)
- **Methods:**
  - save(User)
  - findByUserName(String) - Returns Optional<User>
  - existsByUsername(String) - Returns boolean

**UserServiceImpl.java** (~50 lines)
- @Service, implements UserService
- Injected: UserRepository, PasswordEncoder
- **Methods:**
  - save(User) - Delegates to repo.save()
  - findByUserName(String) - Calls repo.findByUsername()
  - existsByUsername(String) - Calls repo.existsByUsername()
  - loadUserByUsername(String) (from UserDetailsService)
    - Loads User from database
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
  - Optional<User> findByUsername(String)
  - boolean existsByUsername(String)
  - Optional<User> findByEmail(String)
  - boolean existsByEmail(String)

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

**BlacklistedTokenRepository.java** (interface)
- Extends JpaRepository<BlacklistedToken, Long>
- **Methods:**
  - Optional<BlacklistedToken> findByJti(String)
  - void deleteAllByExpiryDateBefore(LocalDateTime date)

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
  - ms-authentication-service (builds from Dockerfile, port 8080, depends_on postgres)
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
| Total Enums | 1 (TokenType) |
| Largest Class | AuthController (~197 lines) |
| New Entities | 4 (RefreshToken, PasswordResetToken, BlacklistedToken, TokenType) |
| New Services | 4 (RefreshTokenService, PasswordResetService, EmailService, BlacklistedTokenService) |
| New Repositories | 3 (RefreshTokenRepository, PasswordResetTokenRepository, BlacklistedTokenRepository) |
| New DTOs | 4 (ForgotPasswordDto, ResetPasswordDto, RefreshTokenRequestDto, TokenRefreshResponseDto) |
| Package Depth | 3-4 levels (com.namnd.springjwt.{service.impl, dto.mapper, config.{filter,security,custom}, util}) |
| Scheduled Tasks | 1 (hourly blacklist cleanup) |
| Test Coverage | 1 smoke test (SpringJwtApplicationTests) |

## External Dependencies

| Dependency | Version | Size (MB) | Security Status |
|------------|---------|-----------|-----------------|
| Spring Boot | 2.6.4 | - | LTS, active maintenance |
| Spring Security | included | - | Active maintenance |
| Spring Mail | included | - | Active maintenance |
| JJWT | 0.9.0 | ~0.1 | Stable (newer: 0.11.x, 0.12.x available) |
| PostgreSQL Driver | ~42.x | ~0.9 | Latest |
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
- JTI-based blacklisting for logout
- Email-driven password reset flow
- Scheduled cleanup of expired tokens
- Configurable token expiration times
- Email validation required on registration

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
