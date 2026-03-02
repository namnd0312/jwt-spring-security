# Phase 5: Application Config & Properties

## Context Links
- [Research: Spring Boot Migration](./research/researcher-01-spring-boot-2.6-to-3.4-java21-jakarta-security6-jjwt-migration.md)
- [Plan Overview](./plan.md)
- Current file: `src/main/resources/application.yml`
- Current file: `src/main/java/com/namnd/springjwt/config/RedisConfig.java`

## Overview
- **Priority:** P1 (deprecated properties cause startup warnings or failures)
- **Status:** pending
- **Effort:** 30min
- **Description:** Update renamed/deprecated Spring Boot 3.x properties and fix RedisConfig for Boot 3.x compatibility

## Key Insights
- `spring.redis.*` renamed to `spring.data.redis.*` in Boot 3.x — app will fail to connect to Redis without this
- `spring.datasource.platform` property deprecated/removed in Boot 3.x — currently set to Hibernate dialect (wrong usage anyway)
- `Jackson2JsonRedisSerializer` constructor changed in Spring Data Redis 3.x — single-arg `Class<T>` constructor still works but generic `Object.class` usage is fine
- jwtSecret already updated in Phase 4; this phase handles remaining config
- `spring-boot-properties-migrator` can be added temporarily to catch any missed renames

## Requirements
- `spring.redis.*` -> `spring.data.redis.*` in application.yml
- Remove `spring.datasource.platform` (it was incorrectly used as Hibernate dialect)
- Verify all other properties are Boot 3.x compatible
- RedisConfig: no code changes needed (constructor API still works)

## Architecture
No architectural changes. Configuration-only updates.

## Related Code Files
| File | Action |
|------|--------|
| `src/main/resources/application.yml` | Modify (redis prefix, remove platform) |
| `src/main/java/com/namnd/springjwt/config/RedisConfig.java` | Verify — no changes needed |
| `pom.xml` | Remove `spring-boot-properties-migrator` if added in Phase 1 |

## Implementation Steps

### 1. Update Redis properties in application.yml

```yaml
# OLD
spring:
  redis:
    host: ${REDIS_HOST:localhost}
    port: ${REDIS_PORT:6379}
    password: ${REDIS_PASSWORD:}

# NEW
spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}
```

### 2. Remove spring.datasource.platform

```yaml
# OLD — incorrect usage: this was meant for schema-{platform}.sql loading, not dialect
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/testdb
    username: postgres
    password: postgres
    platform: org.hibernate.dialect.PostgreSQLDialect

# NEW — remove platform entirely; Hibernate 6 auto-detects dialect from JDBC URL
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/testdb
    username: postgres
    password: postgres
```

**Note:** In Spring Boot 3.x + Hibernate 6, the PostgreSQL dialect is auto-detected from the JDBC URL. The `platform` property was for `spring.sql.init.platform` (schema initialization), not Hibernate dialect. Removing it is correct.

### 3. Verify remaining properties are Boot 3.x compatible

Properties that are UNCHANGED and valid in Boot 3.x:
- `server.port: 8080` — valid
- `spring.jpa.hibernate.ddl-auto: update` — valid
- `spring.jpa.show-sql: true` — valid
- `spring.jpa.properties.hibernate.format_sql: true` — valid
- `spring.datasource.url/username/password` — valid
- `spring.mail.*` — valid
- `namnd.app.*` (custom namespace) — valid
- `logging.*` — valid

### 4. Verify RedisConfig.java — no changes needed

Current `RedisConfig.java`:
```java
template.setValueSerializer(new Jackson2JsonRedisSerializer<>(Object.class));
```

`Jackson2JsonRedisSerializer(Class<T>)` constructor still exists in Spring Data Redis 3.x. No change required.

### 5. Remove spring-boot-properties-migrator (if added in Phase 1)

If `spring-boot-properties-migrator` was added to pom.xml:
```xml
<!-- REMOVE after verifying all properties are migrated -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-properties-migrator</artifactId>
    <scope>runtime</scope>
</dependency>
```

### 6. Full application.yml after all changes (Phases 4+5 combined)

```yaml
server:
  port: 8080
spring:
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true
    properties:
      hibernate:
        format_sql: true
  datasource:
    url: jdbc:postgresql://localhost:5432/testdb
    username: postgres
    password: postgres
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}
  mail:
    host: smtp.gmail.com
    port: 587
    username: nghiemducnam0312@gmail.com
    password: sdxm fmia vuzf bvmq
    properties:
      mail:
        smtp:
          auth: true
          starttls:
            enable: true

namnd:
  app:
    jwtSecret: ${JWT_SECRET:kBJb8FEOvTCWEcfZB6RLMM5BLoI8p0FWOWEu7FSZBYn+ItVi7mHRePYCvum5Ic6l4M2nFw+kdl8du99Bxnb7zg==}
    jwtExpiration: 900000
    jwtRefreshExpiration: 604800000
    passwordResetBaseUrl: ${PASSWORD_RESET_BASE_URL:http://localhost:3000/reset-password}
    activationBaseUrl: ${ACTIVATION_BASE_URL:http://localhost:8080/api/auth/activate}
    maxFailedAttempts: ${MAX_FAILED_ATTEMPTS:5}
    lockDurationMs: ${LOCK_DURATION_MS:900000}

logging:
  level:
    root: info
    com.namnd.springjwt: debug
    org:
      springframework:
        jdbc:
          core:
            JdbcTemplate: DEBUG
            StatementCreatorUtils: TRACE
      hibernate:
        SQL: DEBUG
        type:
          descriptor:
            sql:
              BasicBinder: TRACE
```

## Todo List
- [ ] Rename `spring.redis.*` to `spring.data.redis.*` in application.yml
- [ ] Remove `spring.datasource.platform` line
- [ ] Update jwtSecret (from Phase 4) if not already done
- [ ] Verify RedisConfig.java needs no changes
- [ ] Remove `spring-boot-properties-migrator` from pom.xml if present
- [ ] Start application and verify no "deprecated property" warnings in logs

## Success Criteria
- No `spring.redis.*` keys in application.yml (must be `spring.data.redis.*`)
- No `spring.datasource.platform` in application.yml
- Application connects to Redis successfully at startup
- No deprecated property warnings in startup logs

## Risk Assessment
- **Low risk**: straightforward property renaming
- Redis connection failure if prefix not updated — easy to detect at startup
- Removing `platform` has zero impact since it was misconfigured (dialect string, not platform name)

## Security Considerations
- Mail credentials still hardcoded in application.yml — pre-existing issue, out of scope for this migration
- Consider wrapping `spring.datasource.password` and `spring.mail.password` in env vars (improvement, not blocking)

## Next Steps
Proceed to Phase 6 (Docker & Infrastructure).
