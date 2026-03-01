# Code Review: Redis Token Blacklist Migration

## Scope

- Files reviewed: 7 source files + 3 config files
- Lines analyzed: ~250 LOC
- Review focus: Redis migration correctness, security, edge cases, interface compatibility
- Plan: `/Users/admin/Desktop/DEV/BACK_END/jwt-spring-security/plans/260301-1516-redis-token-blacklist/plan.md`

**Files reviewed:**
- `/Users/admin/Desktop/DEV/BACK_END/jwt-spring-security/pom.xml`
- `/Users/admin/Desktop/DEV/BACK_END/jwt-spring-security/src/main/resources/application.yml`
- `/Users/admin/Desktop/DEV/BACK_END/jwt-spring-security/docker-compose.yml`
- `/Users/admin/Desktop/DEV/BACK_END/jwt-spring-security/src/main/java/com/namnd/springjwt/service/BlacklistedTokenService.java`
- `/Users/admin/Desktop/DEV/BACK_END/jwt-spring-security/src/main/java/com/namnd/springjwt/service/impl/BlacklistedTokenServiceImpl.java`
- `/Users/admin/Desktop/DEV/BACK_END/jwt-spring-security/src/main/java/com/namnd/springjwt/config/filter/JwtAuthenticationFilter.java`
- `/Users/admin/Desktop/DEV/BACK_END/jwt-spring-security/src/main/java/com/namnd/springjwt/controller/AuthController.java`
- `/Users/admin/Desktop/DEV/BACK_END/jwt-spring-security/src/main/java/com/namnd/springjwt/service/JwtService.java`
- `/Users/admin/Desktop/DEV/BACK_END/jwt-spring-security/src/main/java/com/namnd/springjwt/SpringJwtApplication.java`

---

## Overall Assessment

Migration is **functionally correct and complete**. Redis dependency wired correctly, deleted JPA artifacts are fully gone, interface preserved, consumers compile unchanged. `mvn clean compile` passes with zero errors. Two security issues require attention before production deployment: hardcoded credentials in `application.yml` and a weak JWT secret.

---

## Critical Issues

### C1. Hardcoded credentials in `application.yml` committed to git

**File:** `src/main/resources/application.yml` lines 14-15, 22-25

```yaml
datasource:
  username: postgres
  password: postgres           # hardcoded
mail:
  username: nghiemducnam0312@gmail.com
  password: sdxm fmia vuzf bvmq  # app password in plaintext
```

**Impact:** If this file is committed to a public or shared repo, credentials and a real email app-password are exposed. The mail app-password appears to be a real Google account app password.

**Fix:** Move all sensitive values to env vars, same pattern already used for Redis:
```yaml
datasource:
  username: ${DB_USER:postgres}
  password: ${DB_PASSWORD:postgres}
mail:
  username: ${MAIL_USERNAME:}
  password: ${MAIL_PASSWORD:}
```

### C2. Weak JWT secret

**File:** `src/main/resources/application.yml` line 34

```yaml
jwtSecret: bezKoderSecretKey
```

Short, predictable, human-readable string used for HS512 signing. An attacker can brute-force or recognize this as the BezKoder tutorial default key.

**Fix:**
```yaml
jwtSecret: ${JWT_SECRET:bezKoderSecretKey}   # dev only default; prod must override
```
Generate a proper secret for production:
```bash
openssl rand -base64 64
```

---

## High Priority Findings

### H1. Redis unavailability silently allows blacklisted tokens through

**File:** `BlacklistedTokenServiceImpl.java` line 41

```java
return Boolean.TRUE.equals(redisTemplate.hasKey(key));
```

If Redis is down, `hasKey()` throws a `RedisConnectionFailureException` which propagates up to `JwtAuthenticationFilter`, gets caught by the broad `catch (Exception e)` at line 58, logged, and the request **proceeds as authenticated** with a potentially blacklisted token.

This is a security-relevant fail-open. The plan's risk table acknowledges it and defers to a future circuit breaker, which is acceptable for now — but should be explicitly documented.

**Recommended short-term fix in `isTokenBlacklisted`:**
```java
@Override
public boolean isTokenBlacklisted(String jti) {
    try {
        String key = BLACKLIST_PREFIX + jti;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    } catch (Exception e) {
        // Fail-safe: if Redis is unavailable, treat token as blacklisted
        // to prevent use of potentially invalidated tokens
        logger.error("Redis unavailable during blacklist check for JTI: {}. Denying request.", jti);
        return true;
    }
}
```

### H2. `application.yml` — datasource URL hardcodes host and DB name

**File:** `src/main/resources/application.yml` line 13

```yaml
url: jdbc:postgresql://localhost:5432/testdb
```

Not parameterized. In Docker Compose the app connects to `postgres-service` not `localhost`, so the `ms-authentication-service` container will fail to reach Postgres unless the env var is provided externally or the URL is overridden.

**Fix:**
```yaml
url: ${DB_URL:jdbc:postgresql://localhost:5432/testdb}
```

---

## Medium Priority Improvements

### M1. `docker-compose.yml` — redis-service has no volume for persistence

**File:** `docker-compose.yml`

```yaml
redis-service:
  image: 'redis:7-alpine'
  ports:
    - "6379:6379"
  restart: unless-stopped
```

No volume mounted. Redis data is ephemeral — on container restart all blacklisted JTIs are lost. For the token blacklist use case this means logged-out users can reuse their access tokens after a Redis restart until TTL expires naturally.

For short-lived access tokens (15 min) this risk window is bounded and likely acceptable. For production with longer-lived tokens, add persistence:
```yaml
redis-service:
  image: 'redis:7-alpine'
  command: redis-server --appendonly yes
  volumes:
    - redis-data:/data
  ...
volumes:
  redis-data:
```

### M2. `docker-compose.yml` — postgres volume path is machine-specific absolute path

**File:** `docker-compose.yml` line 9

```yaml
volumes:
  - /Users/admin/Desktop/DEV/DOCKER/docker-volumes:/var/lib/docker/volumes/postgres/_data
```

Hard-coded absolute path to a specific developer's machine. Use a named volume instead:
```yaml
volumes:
  - postgres-data:/var/lib/postgresql/data
...
volumes:
  postgres-data:
```

### M3. `BlacklistedTokenServiceImpl` — no null guard on `jti` parameter

**File:** `BlacklistedTokenServiceImpl.java` line 25

```java
public void blacklistToken(String jti, Date expiryDate) {
    long ttlSeconds = (expiryDate.getTime() - System.currentTimeMillis()) / 1000;
```

If `jti` is null (older tokens without `setId()` in builder, or malformed tokens), the Redis key becomes `"blacklist:null"` — a valid but incorrect entry. `expiryDate` null would throw NPE.

`JwtService.generateTokenLogin()` and `generateTokenFromUsername()` both call `setId(UUID.randomUUID().toString())` so this is not currently a live bug, but defensive guards are cheap:

```java
public void blacklistToken(String jti, Date expiryDate) {
    if (jti == null || expiryDate == null) {
        logger.warn("blacklistToken called with null jti or expiryDate, skipping");
        return;
    }
    ...
}
```

### M4. `JwtAuthenticationFilter` still uses field injection with `@Autowired`

**File:** `JwtAuthenticationFilter.java` lines 25-32

```java
@Autowired
private JwtService jwtService;
@Autowired
private UserService userService;
@Autowired
private BlacklistedTokenService blacklistedTokenService;
```

`BlacklistedTokenServiceImpl` now correctly uses constructor injection per code standards. `JwtAuthenticationFilter` is inconsistent (not part of this PR's scope but noted).

### M5. `@EnableScheduling` on `SpringJwtApplication` is now unused

**File:** `SpringJwtApplication.java` line 8

The only `@Scheduled` method was `cleanupExpiredTokens()` which was removed. `@EnableScheduling` has no active scheduled tasks. Per YAGNI, remove it unless other scheduled tasks are planned.

```java
// Remove if no @Scheduled methods remain in the project
@EnableScheduling
```

Verify:
```bash
grep -r "@Scheduled" src/main/java/ --include="*.java"
```

---

## Low Priority Suggestions

### L1. Redis key prefix could include app namespace

**File:** `BlacklistedTokenServiceImpl.java` line 16

```java
private static final String BLACKLIST_PREFIX = "blacklist:";
```

If this Redis instance is shared with other services, `blacklist:` is generic. Consider `jwt:blacklist:` or `auth:blacklist:`. YAGNI applies if Redis is dedicated to this service only.

### L2. Log message in `JwtAuthenticationFilter` — blacklist hit should respond 401

**File:** `JwtAuthenticationFilter.java` lines 43-44

```java
if (jti != null && blacklistedTokenService.isTokenBlacklisted(jti)) {
    logger.warn("Blacklisted token used in request");
```

No response code set. The filter just logs and skips setting the `SecurityContext`. Downstream, Spring Security will treat the request as unauthenticated and return 401 for protected endpoints — this is correct behavior. No code change needed, but the log message could include the endpoint path for better observability:
```java
logger.warn("Blacklisted token rejected for request: {}", request.getRequestURI());
```

---

## Positive Observations

- `Boolean.TRUE.equals(redisTemplate.hasKey(key))` — correct null-safe idiom; `hasKey()` can return null, direct `==` comparison would NPE.
- `ttlSeconds <= 0` guard — correctly skips blacklisting already-expired tokens rather than setting a zero/negative TTL (which Redis would interpret as immediate expiry or error).
- TTL calculation at `(expiryDate.getTime() - System.currentTimeMillis()) / 1000` — correct; matches what `jwtExpiration` configured in ms produces.
- Constructor injection in `BlacklistedTokenServiceImpl` — follows code standards.
- Logging uses `{}` placeholders not string concatenation — correct SLF4J usage.
- No JPA imports remain in `BlacklistedTokenServiceImpl`; deleted files confirmed absent.
- `mvn clean compile` — passes cleanly with zero errors or warnings.
- Interface preserved exactly: `blacklistToken(String, Date)` + `isTokenBlacklisted(String)` — zero changes required in `JwtAuthenticationFilter` or `AuthController`.
- Redis `password: ${REDIS_PASSWORD:}` — env var pattern with empty default is correct for optional auth.

---

## Task Completeness Verification

| Phase | Task | Status |
|-------|------|--------|
| Phase 1 | Add `spring-boot-starter-data-redis` to pom.xml | DONE |
| Phase 1 | Add `spring.redis.*` config to application.yml | DONE |
| Phase 1 | Add redis-service to docker-compose.yml | DONE |
| Phase 1 | `mvn clean compile` passes | DONE |
| Phase 2 | Remove `cleanupExpiredTokens()` from interface | DONE |
| Phase 2 | Rewrite `BlacklistedTokenServiceImpl` with `StringRedisTemplate` | DONE |
| Phase 2 | Constructor injection used | DONE |
| Phase 2 | `@EnableScheduling` not removed from `SpringJwtApplication` | DONE (present, see M5) |
| Phase 3 | Delete `BlacklistedToken.java` | DONE (file absent) |
| Phase 3 | Delete `BlacklistedTokenRepository.java` | DONE (file absent) |
| Phase 3 | Delete `TokenType.java` | DONE (file absent) |
| Phase 3 | No remaining compile refs to deleted classes | DONE (verified) |
| Phase 4 | Compile passes | DONE |
| Phase 4 | Manual integration tests | NOT DONE — requires running services |
| Docs | Update `docs/codebase-summary.md` | NOT DONE |
| Docs | Update `docs/system-architecture.md` | NOT DONE |

All code implementation tasks are complete. Doc updates and integration testing remain as noted in the plan's Phase 4 next steps.

---

## Recommended Actions

1. **[Critical]** Parameterize `datasource.password`, `mail.username`, `mail.password` in `application.yml` — remove hardcoded credentials immediately.
2. **[Critical]** Rotate the exposed Google mail app password and regenerate a strong `jwtSecret` for production.
3. **[High]** Add fail-safe try-catch to `isTokenBlacklisted()` to fail-closed on Redis outage (see H1).
4. **[High]** Parameterize `datasource.url` to allow Docker Compose override (see H2).
5. **[Medium]** Add Redis volume + `appendonly yes` to `docker-compose.yml` for persistence (see M1).
6. **[Medium]** Replace machine-specific postgres volume path with named Docker volume (see M2).
7. **[Medium]** Add null guards on `jti` and `expiryDate` in `blacklistToken()` (see M3).
8. **[Low]** Check if `@EnableScheduling` can be removed (grep for `@Scheduled` in project — see M5).
9. **[Post-PR]** Update `docs/codebase-summary.md` and `docs/system-architecture.md` as specified in Phase 4 next steps.

---

## Metrics

- Type Coverage: N/A (Java, no type checker separate from compiler)
- Compile errors: 0
- Deleted artifact references remaining: 0 (clean)
- Hardcoded credential instances in config: 4 (username, password for DB and mail)
- Linting issues: 0 errors, field injection inconsistency in `JwtAuthenticationFilter` (pre-existing, not in scope)

---

## Unresolved Questions

1. Is Redis dedicated to this service only, or shared? (affects key namespace decision — L1)
2. Is `blacklisted_tokens` PostgreSQL table being dropped manually, or left as harmless orphan? (Phase 3 documented this; confirm intent with team)
3. Are there any other `@Scheduled` methods planned? If not, `@EnableScheduling` should be removed (M5).
