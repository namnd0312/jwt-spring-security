# Phase 7: Compile, Test & Verify

## Context Links
- [Plan Overview](./plan.md)
- All phase files: [Phase 1](./phase-01-build-config-and-dependencies.md) through [Phase 6](./phase-06-docker-and-infrastructure.md)

## Overview
- **Priority:** P1 (validation gate — nothing ships without passing this)
- **Status:** pending
- **Effort:** 1h
- **Description:** Compile the project, run tests, fix any remaining issues, smoke-test endpoints

## Key Insights
- `mvn clean compile` will catch all import/API errors from Phases 1-5
- Existing test is minimal (`SpringJwtApplicationTests.contextLoads()`) — context load test will validate all bean wiring
- Context load test requires running PostgreSQL and Redis — use docker-compose or local instances
- Hibernate 6 may generate different DDL with `ddl-auto: update` — watch for schema warnings

## Requirements
- `mvn clean compile` succeeds with zero errors
- `mvn test` passes all tests
- Application starts and responds to health check
- JWT login/register/refresh/logout flow works end-to-end

## Architecture
No changes. Validation-only phase.

## Related Code Files
| File | Action |
|------|--------|
| All modified files from Phases 1-6 | Verify compilation |
| `src/test/java/com/namnd/springjwt/SpringJwtApplicationTests.java` | Run |

## Implementation Steps

### 1. Compile
```bash
mvn clean compile
```

Expected: BUILD SUCCESS. If errors:
- `cannot find symbol` on javax imports -> Phase 2 incomplete
- `cannot find symbol` on WebSecurityConfigurerAdapter -> Phase 3 incomplete
- `cannot find symbol` on JJWT methods -> Phase 4 incomplete
- Dependency resolution errors -> Phase 1 incomplete

### 2. Run tests
```bash
mvn test
```

**Prerequisites:** PostgreSQL and Redis must be running (tests use `@SpringBootTest` which loads full context).

Start infrastructure if needed:
```bash
docker-compose up -d postgres-service redis-service
```

Expected: `SpringJwtApplicationTests.contextLoads()` passes — confirms all beans wire correctly.

### 3. Start application
```bash
mvn spring-boot:run
```

Watch for:
- `Started SpringJwtApplication in X seconds` — success
- Any deprecated property warnings — should be zero after Phase 5
- Redis connection errors — check `spring.data.redis.*` config
- Hibernate DDL output — verify no unexpected schema changes

### 4. Smoke test API endpoints

**Register:**
```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser","password":"test123","email":"test@example.com","fullName":"Test User","roles":[{"name":"ROLE_USER"}]}'
```
Expected: 200 OK

**Login:**
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"test123"}'
```
Expected: 200 OK with JWT response (or 401 if account not activated — activate first)

**Protected endpoint (with token):**
```bash
curl -H "Authorization: Bearer <token>" http://localhost:8080/api/test/user
```
Expected: 200 OK (if TestController has this endpoint)

**Logout:**
```bash
curl -X POST http://localhost:8080/api/auth/logout \
  -H "Authorization: Bearer <token>"
```
Expected: 200 OK "Logged out successfully."

### 5. Docker full-stack test
```bash
mvn clean package -DskipTests
docker-compose up --build
```

Verify all 3 containers start and app connects to postgres + redis.

### 6. Fix any remaining issues

Common post-migration issues:
- **Hibernate 6 naming**: if column names change, add `spring.jpa.hibernate.naming.physical-strategy=org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl`
- **Redis serialization**: if `ClassCastException` on Redis operations, clear Redis cache (`FLUSHDB`)
- **CORS**: if frontend gets CORS errors, add explicit `CorsConfigurationSource` bean

## Todo List
- [ ] Run `mvn clean compile` — zero errors
- [ ] Run `mvn test` — all tests pass
- [ ] Start app with `mvn spring-boot:run` — starts without errors
- [ ] Verify no deprecated property warnings in logs
- [ ] Smoke test: register endpoint
- [ ] Smoke test: login endpoint (with activation if needed)
- [ ] Smoke test: refresh token endpoint
- [ ] Smoke test: logout endpoint
- [ ] Docker: `docker-compose up --build` — all containers healthy
- [ ] Verify Java 21 in Docker: `docker exec <container> java -version`
- [ ] Remove `spring-boot-properties-migrator` from pom.xml if present

## Success Criteria
- `mvn clean compile` — BUILD SUCCESS
- `mvn test` — BUILD SUCCESS, all tests green
- Application starts and all auth endpoints respond correctly
- Docker compose builds and runs successfully
- No `javax.*` Jakarta EE imports remain in `src/main/`
- No deprecated API usage warnings in logs

## Risk Assessment
- **Medium risk**: integration issues may surface only at runtime
- Hibernate 6 DDL differences: `ddl-auto: update` may attempt schema changes — monitor SQL output
- Redis data format: old serialized data may not deserialize with new Spring Data Redis — clear cache if needed

## Security Considerations
- Verify JWT tokens are signed with HS512 (check JWT header in token: `"alg":"HS512"`)
- Verify expired/invalid tokens are correctly rejected (401)
- Verify blacklisted tokens are correctly rejected
- Verify account lockout still works after 5 failed attempts

## Next Steps
- Update README.md: Java 21, Spring Boot 3.4.3, PostgreSQL 16
- Update `docs/` documentation files with new versions
- Consider adding more comprehensive tests
- Consider switching from WAR to JAR packaging (simpler for Boot 3.x)
