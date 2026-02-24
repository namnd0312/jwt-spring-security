# Code Review: Security Features (Forgot Password, Refresh Token, Logout)

**Date:** 2026-02-24
**Reviewer:** code-reviewer agent
**Plan:** `/plans/260224-2144-forgot-password-refresh-token-logout/plan.md`

---

## Scope

- **Files reviewed:** 38 Java source files (all new + modified)
- **Lines of code analyzed:** ~1,100
- **Review focus:** Security correctness, JTI blacklist, transaction safety, error handling, API contract
- **Build status:** `mvn clean compile` PASSES — zero errors

---

## Overall Assessment

Implementation is functionally correct and secure for the stated requirements. All three features (forgot-password, refresh token rotation, logout + blacklist) work cohesively. The JTI blacklist design is sound. Critical security properties (no email enumeration, single-use reset tokens, token rotation, BCrypt passwords) are all properly implemented. Several medium and low priority issues exist, none blocking.

---

## Critical Issues

None.

---

## High Priority Findings

### H1 — Logout does NOT validate the JWT before trusting it
**File:** `AuthController.java` lines 178–180

```java
// No validateJwtToken() call before extracting JTI / username
String jti = jwtService.getJtiFromToken(jwt);
Date tokenExpiry = jwtService.getExpirationFromToken(jwt);
```

`getJtiFromToken()` and `getExpirationFromToken()` call `Jwts.parser().parseClaimsJws()` directly — they throw unchecked `JwtException` variants on a tampered/malformed token. The outer try-catch in `logout()` is missing; only `resetPassword` has one. A crafted malformed Bearer header will return a 500. Fix: validate before parsing, or wrap in try-catch returning 400.

```java
@PostMapping("/logout")
public ResponseEntity<?> logout(HttpServletRequest request) {
    String jwt = getJwtFromRequest(request);
    if (jwt == null) return ResponseEntity.badRequest().body("No token provided.");

    // ADD: validate before parsing claims
    if (!jwtService.validateJwtToken(jwt)) {
        return ResponseEntity.badRequest().body("Invalid token.");
    }

    String jti = jwtService.getJtiFromToken(jwt);
    ...
}
```

### H2 — Duplicate DB query on blacklist check per-request (hot path)
**File:** `JwtAuthenticationFilter.java` line 42-43

```java
String jti = jwtService.getJtiFromToken(jwt);        // DB query: parse claims + key lookup
if (jti != null && blacklistedTokenService.isTokenBlacklisted(jti)) {  // DB query: SELECT EXISTS
```

`getJtiFromToken()` parses the JWT body (CPU); `isTokenBlacklisted()` does `SELECT EXISTS(... WHERE jti=?)` (DB I/O). This is two operations on every authenticated request. The `existsByJti()` query is not catastrophic but lacks an index declaration on the `jti` column. At scale, add `@Index` on `BlacklistedToken.jti` — the column is `unique=true` so most databases auto-index it, but making it explicit is safer.

Actually `unique = true` on `@Column` does create a unique constraint (and index). This is **acceptable**, but worth noting for production sizing.

### H3 — Refresh token endpoint does NOT invalidate the old token before issuing new one
**File:** `AuthController.java` lines 155–166, `RefreshTokenServiceImpl.java` line 40-41

```java
RefreshToken token = refreshTokenService.verifyExpiration(tokenOptional.get());
// ... creates new token (which deletes old one internally)
RefreshToken newRefreshToken = refreshTokenService.createRefreshToken(user.getId());
```

`createRefreshToken()` deletes the old token for the user. This is correct rotation behavior. However, the `verifyExpiration()` call is NOT inside the same `@Transactional` as `createRefreshToken()`. If a second concurrent request arrives with the same refresh token between `verifyExpiration` and `createRefreshToken`, both calls can race past verification before deletion completes. The controller method itself has no `@Transactional` and the two service calls are in separate transactions. Recommend wrapping the refresh flow in a single transactional service method, or adding an optimistic lock / unique constraint on the token column (already present — `unique=true` on `refresh_tokens.token` will cause the second concurrent `save` to fail with a DB constraint error, which is acceptable as a race guard).

Low probability in practice given the single-user-one-token model, but worth noting.

---

## Medium Priority Improvements

### M1 — Password reset token not cleaned up after use
**File:** `PasswordResetServiceImpl.java` line 93

```java
resetToken.setUsed(true);
passwordResetTokenRepository.save(resetToken);
```

Used tokens remain in the DB indefinitely. There is a cleanup scheduled for `blacklisted_tokens` but none for `password_reset_tokens`. Add a scheduled cleanup in `PasswordResetServiceImpl` (similar to `BlacklistedTokenServiceImpl.cleanupExpiredTokens`), or delete on use:

```java
// Option A: delete instead of mark used
passwordResetTokenRepository.delete(resetToken);

// Option B: add scheduled cleanup
@Scheduled(fixedRate = 3600000)
@Transactional
public void cleanupExpiredTokens() {
    passwordResetTokenRepository.deleteByExpiryDateBefore(new Date());
}
```

### M2 — User can accumulate multiple password reset tokens
**File:** `PasswordResetServiceImpl.java` line 57

If a user calls `/forgot-password` multiple times, each call saves a new `PasswordResetToken` row for the same user. All non-expired, non-used tokens remain valid. At minimum, invalidate (or delete) existing tokens for the same user before creating a new one:

```java
// Invalidate previous tokens for this user
passwordResetTokenRepository.deleteByUser(user);
```

Requires adding `deleteByUser(User user)` to `PasswordResetTokenRepository`.

### M3 — `getJwtFromRequest()` duplicated in two classes
**Files:** `AuthController.java` line 190–195, `JwtAuthenticationFilter.java` line 65–73

Identical private method. Violates DRY. Extract to a `JwtUtils` or `RequestUtils` utility class (there's already a `CookieUtils.java` in `util/` as precedent). Low risk but unnecessary duplication.

### M4 — Hardcoded JWT secret in `application.yml`
**File:** `application.yml` line 30

```yaml
jwtSecret: bezKoderSecretKey
```

No env-var override — the actual value is committed in plaintext. The mail credentials correctly use `${MAIL_USERNAME:...}` pattern. Apply same pattern:

```yaml
jwtSecret: ${JWT_SECRET:bezKoderSecretKey}
```

The default fallback keeps dev working; prod must set `JWT_SECRET`. This is in the git-tracked file so the placeholder "bezKoderSecretKey" is public.

### M5 — `application.yml` uses `create-drop` DDL
**File:** `application.yml` line 6

```yaml
ddl-auto: create-drop
```

This will drop all tables on application shutdown. Acceptable for dev/demo but dangerous if someone runs this against a non-ephemeral DB. Should be documented prominently or overridden via env var (`${DDL_AUTO:create-drop}`).

### M6 — `register` endpoint checks username existence twice
**File:** `AuthController.java` lines 86 and 112

```java
if (userService.existsByUsername(...))  // line 86
// ...
Optional<User> user = this.userService.findByUserName(...)  // line 112 -- redundant check
if (user.isPresent()) { ... }
```

The second `findByUserName` + `isPresent` check is unreachable dead code — if the first check passes, the user does not exist. Remove lines 112–117 (`Optional<User> user` and its `if` block).

### M7 — `SMTP fallback` logs the full reset link including the token
**File:** `EmailServiceImpl.java` line 44

```java
logger.info("Password reset link (SMTP fallback): {}", resetLink);
```

This logs the full reset URL (containing the token UUID) at INFO level. In production log aggregation systems (ELK, Splunk), anyone with log read access can extract valid reset tokens. Acceptable only as an intentional dev-mode fallback; add a comment making this explicit, or gate it on a `spring.profiles.active=dev` check.

---

## Low Priority Suggestions

### L1 — `BlacklistedTokenServiceImpl.cleanupExpiredTokens()` is on the interface
**File:** `BlacklistedTokenService.java`

`cleanupExpiredTokens()` is a scheduling concern, not a business API. Exposing it on the interface forces all implementors (including mocks in tests) to implement it. Move `@Scheduled` to a dedicated `@Component` scheduler class or mark the interface method `default` with empty body.

### L2 — `TokenType.REFRESH` is unused
**File:** `TokenType.java`, `BlacklistedTokenServiceImpl.java` line 33

```java
blacklistedToken.setTokenType(TokenType.ACCESS);  // always ACCESS
```

`TokenType.REFRESH` enum value is never used. Either use it (blacklist refresh tokens on logout too) or remove it (YAGNI). Current logout deletes the refresh token from DB rather than blacklisting it, so the enum is dead code.

### L3 — `JwtResponseDto` has two constructors, old one still present
**File:** `JwtResponseDto.java` line 26–32

The 5-param constructor (without `refreshToken`) is a legacy constructor. If nothing uses it, remove it. If it's kept for backward compat, it should be documented.

### L4 — `verifyExpiration` deletes the token on expiry without `@Transactional`
**File:** `RefreshTokenServiceImpl.java` line 54–59

```java
public RefreshToken verifyExpiration(RefreshToken token) {
    if (token.getExpiryDate().before(new Date())) {
        refreshTokenRepository.delete(token);  // no @Transactional
        throw new RuntimeException("...");
    }
```

`delete()` without a transaction boundary may leave orphans if the delete is not committed before the exception propagates. Add `@Transactional` to this method.

### L5 — `User.username` and `User.email` columns lack `@Column(nullable = false)` where appropriate
**File:** `User.java`

`username` and `password` fields have no `@Column` annotation at all. Adding `nullable = false` would enforce DB-level constraints matching application logic.

### L6 — `logger.error` passes full exception object, not just message
**File:** `JwtService.java` lines 52, 54, 56, 58, 60

```java
logger.error("Invalid JWT signature -> Message: {} ", e);
```

Passing the exception object as the `{}` placeholder logs `e.toString()` which is the exception class + message, not a stack trace. For actual stack traces use `logger.error("...", e)` with the exception as the last argument without a placeholder. For error-level JWT failures (malformed, tampered) a stack trace is appropriate.

---

## Positive Observations

- **JTI blacklist design is correct.** UUID stored per-token, checked in filter before setting SecurityContext. Expiry-aware cleanup prevents unbounded table growth.
- **No email enumeration.** Forgot-password always returns 200 regardless of email existence.
- **Single-use reset tokens.** `used` flag + expiry check in `resetPassword` is properly double-validated.
- **Token rotation on refresh.** Old token deleted before new one is issued (within `createRefreshToken`).
- **BCrypt via `RegisterDtoMapper`.** Password encoding is centralized in the mapper, not scattered.
- **Log masking on email send.** `maskEmail()` avoids logging full addresses.
- **`@EnableScheduling` on main class.** Correct placement for scheduled cleanup.
- **Package structure** follows the established `service/impl` split cleanly.
- **`@Transactional`** on all write operations in service impls (blacklist, reset token creation, refresh token creation/deletion).
- **Build is clean** — zero compiler errors or warnings with Maven.

---

## Recommended Actions

1. **[High]** Add JWT validation check at top of `logout()` in `AuthController` before calling `getJtiFromToken()`.
2. **[High]** Wrap refresh-token rotation (verify + delete old + create new) in a single `@Transactional` service method to eliminate the concurrency gap.
3. **[Medium]** Add scheduled cleanup (or delete-on-use) for `password_reset_tokens` table.
4. **[Medium]** Invalidate existing reset tokens for a user when a new one is created.
5. **[Medium]** Remove the dead-code duplicate username check (lines 112–117) in `registerUser`.
6. **[Medium]** Apply `${JWT_SECRET:bezKoderSecretKey}` env-var pattern to JWT secret in `application.yml`.
7. **[Medium]** Add `@Transactional` to `RefreshTokenServiceImpl.verifyExpiration()`.
8. **[Low]** Extract `getJwtFromRequest()` to a shared utility to eliminate duplication between `AuthController` and `JwtAuthenticationFilter`.
9. **[Low]** Remove unused `TokenType.REFRESH` or use it when blacklisting refresh tokens.
10. **[Low]** Add `@Transactional` annotation note or gate SMTP fallback log on dev profile.

---

## Metrics

- **Build:** PASS (`mvn clean compile`)
- **Test Coverage:** No unit tests exist (only `SpringJwtApplicationTests` context load) — known gap, noted in plan as "No existing tests"
- **Linting Issues:** 0 compile errors; ~6 low-severity style findings
- **Files reviewed:** 38
- **New files:** 16 (4 entities, 4 repos, 4 services, 4 DTOs)
- **Modified files:** 12

---

## Plan Task Completeness (Phase 4 Todo)

All items from `plan.md` "Action Items" checklist were implemented:

| Action Item | Status |
|---|---|
| `blacklisted_tokens.jti` column (UUID-sized) | DONE |
| `jwtExpiration: 900000` (15 min) | DONE |
| `generateTokenLogin` adds JTI via `.setId(UUID.randomUUID().toString())` | DONE |
| `getJtiFromToken()` method in JwtService | DONE |
| `blacklistToken()` accepts JTI | DONE |
| `isTokenBlacklisted()` checks by JTI | DONE |
| `BlacklistedToken.jti` field (not `token`) | DONE |
| EmailServiceImpl SMTP failure fallback | DONE |
| Email validation in register (null/empty + duplicate) | DONE |
| `JwtAuthenticationFilter` checks blacklist by JTI | DONE |
| Logout blacklists by JTI | DONE |
| `existsByEmail` in UserRepository + UserService | DONE |

Phase 4 testing todos remain **manual verification steps** (not automated tests). Compile check passes. No automated test suite exists; this is a known gap.

---

## Unresolved Questions

1. **Concurrent refresh token race:** Should the refresh-token endpoint use a DB-level pessimistic lock, or is the unique constraint collision (causing 500 instead of 400) acceptable?
2. **SMTP fallback in production:** Is the reset-link log intentional for prod, or should it be gated to dev profile only?
3. **`create-drop` in committed config:** Is this intentionally left as-is for demo purposes, or should it be environment-overridable?
4. **Logout scope:** Plan confirms "current session only" — no logout-all-sessions feature. Is that still the desired behavior, or should `/logout` also revoke the refresh token for all users (it currently does delete the user's single refresh token, so effectively equivalent)?
