# Phase 4: Verify and Test

## Context Links

- [plan.md](./plan.md)
- [Phase 1](./phase-01-add-redis-dependency-and-config.md)
- [Phase 2](./phase-02-rewrite-blacklisted-token-service.md)
- [Phase 3](./phase-03-remove-postgresql-blacklist-artifacts.md)

## Overview

- **Priority:** P2
- **Status:** pending
- **Description:** End-to-end verification: compile, unit test, manual integration test of login/logout/blacklist flow, Redis key inspection.

## Key Insights

- Existing test (`SpringJwtApplicationTests`) is a context load smoke test -- will verify Spring context still boots with Redis config
- Manual testing needed for logout + blacklist verification (no integration tests exist)
- Redis CLI useful for inspecting keys and TTLs directly

## Requirements

**Functional:**
- Project compiles without errors
- Spring context loads successfully
- Login, logout, token rejection flow works end-to-end
- Blacklisted tokens auto-expire in Redis

**Non-functional:**
- No regression in existing auth flows
- Redis keys follow `blacklist:{jti}` pattern
- TTL matches token expiration

## Architecture

Test flow:
```
1. Start Redis + PostgreSQL + App
2. POST /api/auth/register  -->  create user
3. POST /api/auth/login     -->  get access token + refresh token
4. POST /api/auth/logout    -->  blacklist JTI in Redis
5. GET /api/protected       -->  401 (token blacklisted)
6. redis-cli KEYS "blacklist:*"  -->  verify key exists
7. redis-cli TTL "blacklist:{jti}"  -->  verify TTL > 0
8. Wait for TTL or manually delete  -->  key auto-expires
```

## Related Code Files

No files modified in this phase. Verification only.

## Implementation Steps

### Step 1: Compile verification

```bash
cd /Users/admin/Desktop/DEV/BACK_END/jwt-spring-security
mvn clean compile -q
```

### Step 2: Run existing tests

```bash
mvn test
```

Expected: `SpringJwtApplicationTests` passes (context loads). Note: this test requires PostgreSQL and Redis to be running.

### Step 3: Start dependencies

```bash
# Terminal 1: Start Redis
redis-server

# Terminal 2: Start PostgreSQL (if not running)
# Or use docker-compose for postgres only
docker-compose up postgres-service
```

### Step 4: Start application

```bash
mvn spring-boot:run
```

Verify startup logs show:
- No Redis connection errors
- No missing bean errors for `StringRedisTemplate`

### Step 5: Manual test -- Register + Login

```bash
# Register
curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser","email":"test@example.com","password":"pass123","fullName":"Test User","roles":[{"name":"ROLE_USER"}]}'

# Login
curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser","password":"pass123"}'
```

Save the `token` from login response.

### Step 6: Manual test -- Logout

```bash
# Logout (replace TOKEN with actual token)
curl -s -X POST http://localhost:8080/api/auth/logout \
  -H "Authorization: Bearer TOKEN"
```

Expected response: `"Logged out successfully."`

### Step 7: Verify token rejected after logout

```bash
# Try using the same token (should fail)
curl -s -X POST http://localhost:8080/api/auth/logout \
  -H "Authorization: Bearer TOKEN"
```

Expected response: `"Token already invalidated."`

### Step 8: Inspect Redis keys

```bash
# List blacklist keys
redis-cli KEYS "blacklist:*"

# Check TTL of specific key (replace JTI)
redis-cli TTL "blacklist:{jti}"
```

Expected: key exists with TTL roughly equal to remaining token lifetime (max 900 seconds for access token).

### Step 9: Verify auto-expiry

Wait for token TTL to elapse (or set a short `jwtExpiration` for testing, e.g., 30000ms = 30s):

```bash
# After TTL expires
redis-cli KEYS "blacklist:*"
```

Expected: key no longer exists.

### Step 10: Verify filter still works

```bash
# Login again
curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser","password":"pass123"}'

# Use new token on protected endpoint (should succeed)
curl -s -X GET http://localhost:8080/api/protected \
  -H "Authorization: Bearer NEW_TOKEN"
```

## Todo List

- [ ] `mvn clean compile` passes
- [ ] `mvn test` passes (context loads)
- [ ] App starts without Redis connection errors
- [ ] Register + Login works
- [ ] Logout blacklists token in Redis
- [ ] Blacklisted token rejected on subsequent requests
- [ ] Redis key has correct TTL
- [ ] Key auto-expires after TTL
- [ ] New login + protected endpoint access works after logout

## Success Criteria

- All compilation and test steps pass
- Full logout flow works: token blacklisted, subsequent use rejected
- Redis keys follow `blacklist:{jti}` pattern with correct TTL
- No `blacklisted_tokens` table queries in SQL logs
- `JwtAuthenticationFilter` and `AuthController` work without any code changes

## Risk Assessment

| Risk | Impact | Mitigation |
|------|--------|------------|
| Redis not installed locally | Cannot test | `brew install redis` or `docker run redis:7-alpine` |
| Test DB has stale `blacklisted_tokens` table | Confusion | Ignore or drop manually; Hibernate won't touch it |
| `SpringJwtApplicationTests` fails with Redis | Test suite broken | Ensure Redis running before `mvn test`, or add `@ConditionalOnProperty` for test profile |

## Security Considerations

- Verify blacklisted tokens truly cannot be used for authenticated requests
- Verify Redis key doesn't contain sensitive token content (only JTI + "1" value)
- Verify TTL prevents indefinite key storage

## Next Steps

After all tests pass:
1. Update `docs/codebase-summary.md` -- remove BlacklistedToken entity, BlacklistedTokenRepository, TokenType references; add Redis dependency
2. Update `docs/system-architecture.md` -- update logout flow diagram, add Redis to infrastructure
3. Update `README.md` -- add Redis as prerequisite, update troubleshooting
4. Commit changes with message: `feat: replace PostgreSQL token blacklist with Redis`
