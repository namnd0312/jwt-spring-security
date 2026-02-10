# Codebase Summary

**Project:** jwt-spring-security
**Generated:** February 2026
**Total Java Files:** 19 (main) + 1 (test)
**Total Lines of Code (approx):** ~2,500 LOC

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

**AuthController.java** (96 lines)
- Route: `/api/auth`
- Annotations: @CrossOrigin(origins="*", maxAge=3600), @RestController, @RequestMapping
- **Endpoints:**
  - **POST /login** (accepts User, returns JwtResponseDto)
    - Calls authenticationManager.authenticate()
    - Generates token via JwtService
    - Loads user from database
    - Returns id, token, type, username, name, roles
  - **POST /register** (accepts RegisterDto, returns String)
    - Validates username uniqueness
    - Iterates roles: saves new, reuses existing by ID
    - Maps RegisterDto to User via RegisterDtoMapper
    - Saves user via UserService
    - Returns success message

### 4. Data Models

**User.java** (29 lines)
- @Entity, @Data (Lombok)
- **Table:** users
- **Columns:**
  - id (BIGSERIAL, GenerationType.IDENTITY)
  - username (String)
  - password (String, BCrypt-encoded)
  - fullName (String)
  - roles (Set<Role>, ManyToMany eager, JoinTable user_roles)

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

**JwtResponseDto.java** (~40 lines)
- **Fields:** id (Long), token (String), type (String = "Bearer"), username (String), name (String), roles (Collection<? extends GrantedAuthority>)
- **Purpose:** Response payload for login endpoint

**RegisterDto.java** (~30 lines)
- **Fields:** username (String), password (String), fullName (String), roles (Set<Role>)
- **Purpose:** Request payload for register endpoint

**RegisterDtoMapper.java** (~40 lines)
- Maps RegisterDto → User entity
- Encodes password via PasswordEncoder (BCrypt)
- Copies username, password (encoded), fullName, roles

### 6. Services

**JwtService.java** (61 lines)
- @Component
- Injected: @Value("${namnd.app.jwtSecret}"), @Value("${namnd.app.jwtExpiration}")
- **Methods:**
  - `generateTokenLogin(Authentication)` - Generates HS512 token
    - Subject: username from UserPrinciple
    - Expiration: now + EXPIRE_TIME * 1000 (ms)
    - SignatureAlgorithm.HS512 with SECRET_KEY
  - `validateJwtToken(String authToken)` - Returns boolean
    - Attempts to parse token
    - Catches & logs exceptions: SignatureException, MalformedJwtException, ExpiredJwtException, UnsupportedJwtException, IllegalArgumentException
  - `getUserNameFromJwtToken(String token)` - Extracts subject (username)
    - Parses token, returns getBody().getSubject()

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

**RoleRepository.java** (interface)
- Extends JpaRepository<Role, Long>
- **Methods:**
  - Role findByName(String)

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

**application.yml** (46 lines)
- server.port: 8080
- spring.jpa.hibernate.ddl-auto: none (manual schema)
- spring.jpa.show-sql: true
- spring.datasource.url: jdbc:postgresql://localhost:5432/testdb
- spring.datasource.username: postgres
- spring.datasource.password: 123456
- namnd.app.jwtSecret: bezKoderSecretKey
- namnd.app.jwtExpiration: 86400000 (24h in milliseconds)
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
| Total Java Classes | 19 |
| Total Interfaces | 3 (UserService, RoleService, JwtService as @Component) |
| Total Enums | 0 |
| Largest Class | AuthController (96 lines) |
| Package Depth | 3-4 levels (com.namnd.springjwt.{service.impl, dto.mapper, config.{filter,security,custom}}) |
| Cyclomatic Complexity (est.) | Low (simple validation logic) |
| Test Coverage | 1 smoke test (SpringJwtApplicationTests) |

## External Dependencies

| Dependency | Version | Size (MB) | Security Status |
|------------|---------|-----------|-----------------|
| Spring Boot | 2.6.4 | - | LTS, active maintenance |
| Spring Security | included | - | Active maintenance |
| JJWT | 0.9.0 | ~0.1 | Stable (newer: 0.11.x, 0.12.x available) |
| PostgreSQL Driver | ~42.x | ~0.9 | Latest |
| Lombok | ~1.18.x | ~1.8 | Active maintenance |

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
- Stateless design supports scaling
- Error handling in JWT validation

**Areas for Improvement:**
- Limited test coverage (1 smoke test)
- No validation annotations (@Valid, @NotNull) on DTOs
- Hardcoded jwtSecret in application.yml (should use env var)
- No rate limiting on login endpoint
- No token refresh mechanism
- Manual schema management (no migrations tracked)
- No request/response logging middleware
- CustomAccesDeniedHandler not visible in summary (likely basic 403 response)

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

1. **Authentication Providers:** OAuth2, SAML, LDAP
2. **Advanced Roles:** Implement permissions model (Role → Permission mapping)
3. **Audit Logging:** Track login/register/access attempts
4. **API Gateway:** Kong, Spring Cloud Gateway wrapper
5. **Microservices:** Extract as library for other services
6. **Admin UI:** Dashboard for user/role management
7. **Analytics:** Track authentication metrics, anomalies
