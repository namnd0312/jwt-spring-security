# Phase 1: Add Redis Dependency and Configuration

## Context Links

- [plan.md](./plan.md)
- [pom.xml](/Users/admin/Desktop/DEV/BACK_END/jwt-spring-security/pom.xml)
- [application.yml](/Users/admin/Desktop/DEV/BACK_END/jwt-spring-security/src/main/resources/application.yml)
- [docker-compose.yml](/Users/admin/Desktop/DEV/BACK_END/jwt-spring-security/docker-compose.yml)

## Overview

- **Priority:** P2
- **Status:** pending
- **Description:** Add `spring-boot-starter-data-redis` Maven dependency and Redis connection properties to `application.yml`. Optionally add Redis service to `docker-compose.yml`.

## Key Insights

- Spring Boot 2.6.4 auto-configures `StringRedisTemplate` when `spring-boot-starter-data-redis` is on classpath
- Default Lettuce client included; no extra client dependency needed
- Redis defaults to `localhost:6379` with no password -- explicit config allows env var override

## Requirements

**Functional:**
- Project compiles with Redis on classpath
- `StringRedisTemplate` bean auto-configured by Spring Boot

**Non-functional:**
- Redis connection configurable via env vars for production
- No breaking changes to existing functionality

## Architecture

```
Application  --->  StringRedisTemplate  --->  Redis (localhost:6379)
                   (auto-configured)
```

No new beans needed. Spring Boot auto-config handles `RedisConnectionFactory` + `StringRedisTemplate`.

## Related Code Files

**Modified:**
- `pom.xml` -- add dependency
- `application.yml` -- add `spring.redis.*` config
- `docker-compose.yml` -- add redis service (optional)

## Implementation Steps

### Step 1: Add Maven dependency to `pom.xml`

Add after the `spring-boot-starter-mail` dependency block (line ~78):

```xml
<!-- For Redis-based token blacklisting -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

### Step 2: Add Redis config to `application.yml`

Add under the `spring:` block, after the `mail:` section (after line ~26):

```yaml
  redis:
    host: ${REDIS_HOST:localhost}
    port: ${REDIS_PORT:6379}
    password: ${REDIS_PASSWORD:}
```

### Step 3 (Optional): Add Redis to `docker-compose.yml`

Add a new service before `ms-authentication-service`:

```yaml
  redis-service:
    image: 'redis:7-alpine'
    ports:
      - "6379:6379"
    networks:
      - my-net
    restart: unless-stopped
```

Update `ms-authentication-service.depends_on` to include `redis-service`.

### Step 4: Verify compilation

```bash
cd /Users/admin/Desktop/DEV/BACK_END/jwt-spring-security
mvn clean compile -q
```

## Todo List

- [ ] Add `spring-boot-starter-data-redis` to pom.xml
- [ ] Add `spring.redis.*` config to application.yml
- [ ] (Optional) Add redis-service to docker-compose.yml
- [ ] Verify `mvn clean compile` succeeds

## Success Criteria

- `mvn clean compile` passes with no errors
- No runtime errors related to Redis connection at startup (Redis not required to be running for compile)
- Existing tests still pass (`mvn test`)

## Risk Assessment

| Risk | Impact | Mitigation |
|------|--------|------------|
| Redis not running at app startup | App fails to start | Spring Boot 2.6.x tolerates missing Redis for compile; runtime requires Redis |
| Version incompatibility | Compile error | `spring-boot-starter-data-redis` version managed by Spring Boot parent POM |

## Security Considerations

- Redis password configurable via `REDIS_PASSWORD` env var
- Default empty password acceptable for local dev only
- Production: use strong password + TLS + network isolation

## Next Steps

Proceed to [Phase 2: Rewrite BlacklistedTokenServiceImpl](./phase-02-rewrite-blacklisted-token-service.md)
