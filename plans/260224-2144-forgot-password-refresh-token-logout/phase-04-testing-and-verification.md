# Phase 4: Testing & Verification

## Context Links

- [Plan Overview](./plan.md)
- [Phase 1 - Database & Entity Layer](./phase-01-database-entity-layer.md)
- [Phase 2 - Service Layer](./phase-02-service-layer.md)
- [Phase 3 - Controller & Security Layer](./phase-03-controller-security-layer.md)
- [Code Standards](../docs/code-standards.md)

## Overview

- **Date:** 2026-02-24
- **Priority:** P1
- **Status:** pending
- **Effort:** 1h
- **Description:** Compile the project, verify all new entities/services/endpoints work correctly, test each feature flow end-to-end, and verify edge cases (expired tokens, reused tokens, invalid tokens, blacklisted tokens).

## Key Insights

- Project uses `create-drop` DDL so tables are recreated on each restart; seed data (roles) may need re-insertion
- No existing tests in the project to break; focus on compilation + manual API testing
- SMTP testing requires either real SMTP credentials or a tool like MailHog/Mailtrap for local dev
- Refresh token rotation means the old refresh token is invalid immediately after use
- Blacklist check adds a DB query per authenticated request; verify no noticeable latency

## Requirements

### Functional
- Project compiles without errors (`mvn clean compile`)
- All 6 auth endpoints respond correctly
- Forgot-password sends email (or logs attempt if SMTP not configured)
- Reset-password works with valid token, rejects expired/used/invalid tokens
- Login returns both access token and refresh token
- Refresh-token returns new access + rotated refresh token
- Logout blacklists access token and deletes refresh token
- Blacklisted token is rejected by JwtAuthenticationFilter

### Non-Functional
- No compilation warnings from new code
- Response times under 500ms for all endpoints
- Scheduled cleanup runs without errors

## Architecture

Testing flow covers all layers:

```
API Tests (curl/Postman)
    |
    v
AuthController (endpoints)
    |
    v
Service Layer (business logic)
    |
    v
Repository Layer (DB queries)
    |
    v
PostgreSQL (verify tables created)
```

## Related Code Files

All files created/modified in Phases 1-3.

### Key Files to Verify
- `src/main/java/com/namnd/springjwt/model/PasswordResetToken.java`
- `src/main/java/com/namnd/springjwt/model/RefreshToken.java`
- `src/main/java/com/namnd/springjwt/model/BlacklistedToken.java`
- `src/main/java/com/namnd/springjwt/service/impl/PasswordResetServiceImpl.java`
- `src/main/java/com/namnd/springjwt/service/impl/RefreshTokenServiceImpl.java`
- `src/main/java/com/namnd/springjwt/service/impl/BlacklistedTokenServiceImpl.java`
- `src/main/java/com/namnd/springjwt/controller/AuthController.java`
- `src/main/java/com/namnd/springjwt/config/filter/JwtAuthenticationFilter.java`

## Implementation Steps

### Step 1: Compile check

```bash
cd /Users/admin/Desktop/DEV/BACK_END/jwt-spring-security
mvn clean compile
```

If errors occur, fix them before proceeding. Common issues:
- Missing imports
- Wrong method signatures (check interface matches impl)
- Lombok not generating getters/setters (check annotation processor)

### Step 2: Run existing tests

```bash
mvn test
```

Verify no existing tests broke. If the project has no tests yet, this step simply confirms the test phase passes.

### Step 3: Start application and verify DB tables

```bash
mvn spring-boot:run
```

Connect to PostgreSQL and verify tables were created:

```sql
\dt
-- Expected: users, roles, user_roles, password_reset_tokens, refresh_tokens, blacklisted_tokens
```

Verify columns on new tables:

```sql
\d password_reset_tokens
\d refresh_tokens
\d blacklisted_tokens
\d users  -- verify email column exists
```

### Step 4: Test registration with email

```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "password": "password123",
    "fullName": "Test User",
    "email": "testuser@example.com",
    "roles": [{"name": "ROLE_USER"}]
  }'
```

**Expected:** `200 OK` with `"User registered successfully!"`

### Step 5: Test login with refresh token

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "password": "password123"
  }'
```

**Expected:** `200 OK` with JSON containing `token`, `refreshToken`, `username`, `roles`, etc.

Save the `token` and `refreshToken` values for subsequent tests.

### Step 6: Test protected endpoint with valid token

```bash
# Replace <ACCESS_TOKEN> with actual token from login
curl -X GET http://localhost:8080/api/protected \
  -H "Authorization: Bearer <ACCESS_TOKEN>"
```

**Expected:** `200 OK` (or whatever the protected endpoint returns)

### Step 7: Test refresh token flow

```bash
# Replace <REFRESH_TOKEN> with actual refresh token from login
curl -X POST http://localhost:8080/api/auth/refresh-token \
  -H "Content-Type: application/json" \
  -d '{
    "refreshToken": "<REFRESH_TOKEN>"
  }'
```

**Expected:** `200 OK` with new `accessToken` and `refreshToken`

**Edge case - reuse old refresh token (should fail after rotation):**

```bash
# Use the SAME old refresh token again
curl -X POST http://localhost:8080/api/auth/refresh-token \
  -H "Content-Type: application/json" \
  -d '{
    "refreshToken": "<OLD_REFRESH_TOKEN>"
  }'
```

**Expected:** `400 Bad Request` (old token was deleted during rotation)

### Step 8: Test logout flow

```bash
# Use a valid access token
curl -X POST http://localhost:8080/api/auth/logout \
  -H "Authorization: Bearer <ACCESS_TOKEN>"
```

**Expected:** `200 OK` with `"Logged out successfully."`

**Verify blacklisted token is rejected:**

```bash
# Use the SAME access token that was just logged out
curl -X GET http://localhost:8080/api/protected \
  -H "Authorization: Bearer <ACCESS_TOKEN>"
```

**Expected:** `401 Unauthorized` or `403 Forbidden` (token is blacklisted)

### Step 9: Test forgot password flow

```bash
curl -X POST http://localhost:8080/api/auth/forgot-password \
  -H "Content-Type: application/json" \
  -d '{
    "email": "testuser@example.com"
  }'
```

**Expected:** `200 OK` with `"If the email exists, a password reset link has been sent."`

Check application logs for the email sending attempt. If SMTP is not configured, there should be an error log but the endpoint still returns 200.

**Test with non-existent email:**

```bash
curl -X POST http://localhost:8080/api/auth/forgot-password \
  -H "Content-Type: application/json" \
  -d '{
    "email": "nonexistent@example.com"
  }'
```

**Expected:** Same `200 OK` response (no info leakage)

### Step 10: Test reset password flow

To test this, either:
- (a) Check the email for the reset link and extract the token
- (b) Query the DB directly: `SELECT token FROM password_reset_tokens ORDER BY id DESC LIMIT 1;`

```bash
# Replace <RESET_TOKEN> with the UUID from DB or email
curl -X POST http://localhost:8080/api/auth/reset-password \
  -H "Content-Type: application/json" \
  -d '{
    "token": "<RESET_TOKEN>",
    "newPassword": "newpassword456"
  }'
```

**Expected:** `200 OK` with `"Password reset successful."`

**Verify new password works:**

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "password": "newpassword456"
  }'
```

**Expected:** `200 OK` with JWT response

**Edge case - reuse same reset token:**

```bash
curl -X POST http://localhost:8080/api/auth/reset-password \
  -H "Content-Type: application/json" \
  -d '{
    "token": "<RESET_TOKEN>",
    "newPassword": "anotherpassword"
  }'
```

**Expected:** `400 Bad Request` with `"Password reset token already used"`

### Step 11: Test edge cases

**Invalid refresh token:**

```bash
curl -X POST http://localhost:8080/api/auth/refresh-token \
  -H "Content-Type: application/json" \
  -d '{
    "refreshToken": "invalid-uuid-token"
  }'
```

**Expected:** `400 Bad Request`

**Invalid reset token:**

```bash
curl -X POST http://localhost:8080/api/auth/reset-password \
  -H "Content-Type: application/json" \
  -d '{
    "token": "invalid-token",
    "newPassword": "password123"
  }'
```

**Expected:** `400 Bad Request`

**Logout without token:**

```bash
curl -X POST http://localhost:8080/api/auth/logout
```

**Expected:** `400 Bad Request` with `"No token provided."`

### Step 12: Verify scheduled cleanup

Check application logs after 1 hour (or temporarily reduce `@Scheduled(fixedRate)` to 60000ms for testing):

```
INFO  BlacklistedTokenServiceImpl - Expired blacklisted tokens cleaned up
```

## Todo List

- [ ] Run `mvn clean compile` - fix any errors
- [ ] Run `mvn test` - ensure no existing tests break
- [ ] Start app, verify all 6 DB tables exist
- [ ] Test POST /api/auth/register with email field
- [ ] Test POST /api/auth/login returns refreshToken
- [ ] Test POST /api/auth/refresh-token with valid token
- [ ] Test POST /api/auth/refresh-token with old rotated token (should fail)
- [ ] Test POST /api/auth/logout blacklists access token
- [ ] Test blacklisted token rejected on protected endpoint
- [ ] Test POST /api/auth/forgot-password with valid email
- [ ] Test POST /api/auth/forgot-password with non-existent email (same response)
- [ ] Test POST /api/auth/reset-password with valid token
- [ ] Test POST /api/auth/reset-password with used token (should fail)
- [ ] Test all edge cases (invalid tokens, missing fields)
- [ ] Verify scheduled cleanup runs in logs

## Success Criteria

- `mvn clean compile` passes with zero errors
- `mvn test` passes (no regressions)
- All 6 endpoints return expected responses
- Refresh token rotation works (old token invalidated)
- Logout + blacklist works (logged-out token rejected)
- Forgot-password does not leak email existence
- Reset-password tokens are single-use
- Scheduled cleanup logged

## Risk Assessment

| Risk | Impact | Mitigation |
|------|--------|------------|
| SMTP not configured for local testing | Low | Check logs for email send attempt; use MailHog or Mailtrap |
| create-drop DDL loses data on restart | Low | Expected in dev; re-register user for each test session |
| No automated test suite | Medium | Manual curl tests sufficient for MVP; recommend adding integration tests later |
| Protected endpoint may not exist for testing | Low | Use any non-auth endpoint; if none exist, create a simple test controller |

## Security Considerations

- Verify forgot-password returns identical response for existing/non-existing emails
- Verify blacklisted tokens cannot access protected resources
- Verify reset tokens cannot be reused after password change
- Verify old refresh tokens are invalid after rotation
- Verify passwords are BCrypt-encoded in DB (not plaintext)

## Next Steps

After all tests pass:
- Update `README.md` with new API endpoints documentation
- Update `docs/system-architecture.md` with new flow diagrams
- Update `docs/codebase-summary.md` with new files list
- Update `docs/project-roadmap.md` with feature completion status
- Consider adding integration tests with `@SpringBootTest` + `@AutoConfigureMockMvc`
