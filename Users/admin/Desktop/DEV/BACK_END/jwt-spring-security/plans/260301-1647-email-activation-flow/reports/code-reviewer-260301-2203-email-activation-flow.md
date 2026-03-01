# Code Review: Email Activation Flow

**Date:** 2026-03-01
**Reviewer:** code-reviewer
**Plan:** plans/260301-1647-email-activation-flow

---

## Code Review Summary

### Scope

- **Files reviewed:** 11 source files + 4 plan files
- **Lines of code analyzed:** ~450 LOC (new/modified)
- **Review focus:** Email activation flow — new entities, services, controller endpoints, Spring Security integration
- **Compile status:** `mvn compile` — PASS (zero errors)

### Overall Assessment

Implementation is clean, structurally correct, and faithfully mirrors the existing `PasswordResetToken` pattern. Spring Security integration via `UserPrinciple.isEnabled()` is the right approach. Three issues must be fixed before merge: two security (information leakage), one missing (DB migration). Documentation update for Phase 4 is partial.

**Overall rating: 6.5 / 10** — functionality correct, blocked by security issues and incomplete docs.

---

### Critical Issues

#### 1. SMTP Fallback Logs Full Token URL — Token Leak

**File:** `src/main/java/com/namnd/springjwt/service/impl/EmailServiceImpl.java` lines 47, 69

```java
// Line 69 — LOGS THE FULL ACTIVATION LINK INCLUDING TOKEN
logger.info("Activation link (SMTP fallback): {}", activationLink);
```

The activation link contains the raw UUID token. Logging it at `INFO` level means it appears in any log aggregation system (ELK, CloudWatch, etc.) in plaintext. Any dev or ops person with log access can steal it and activate arbitrary accounts — this is a token hijack vector.

**Fix:** Either drop this fallback log line entirely, or log only that sending failed without the link:

```java
} catch (Exception e) {
    logger.error("Failed to send activation email to {}: {}", maskEmail(to), e.getMessage());
    // DO NOT log activationLink — it contains a secret token
}
```

Same problem exists for `sendPasswordResetEmail` at line 47 (pre-existing, but same severity).

---

#### 2. `resendActivationToken` Leaks Email Existence and Activation State

**File:** `src/main/java/com/namnd/springjwt/service/impl/ActivationServiceImpl.java` lines 76, 79

```java
.orElseThrow(() -> new RuntimeException("User not found"));   // confirms email not registered

if (user.isActive()) {
    throw new RuntimeException("Account is already active");  // confirms email registered + active
}
```

**File:** `src/main/java/com/namnd/springjwt/controller/AuthController.java` lines 137-143
The controller propagates these exception messages directly to the response body.

An attacker can enumerate registered emails and their activation status by calling `POST /api/auth/resend-activation`. Compare with `forgotPassword` which correctly uses a silent no-op for missing users (returns generic 200 regardless).

**Fix:** Mirror the `forgotPassword` pattern — always return a generic 200, never surface distinct error messages:

```java
// ActivationServiceImpl.resendActivationToken()
@Override
@Transactional
public void resendActivationToken(String email) {
    Optional<User> userOpt = userRepository.findByEmail(email);
    if (userOpt.isEmpty() || userOpt.get().isActive()) {
        // Silent no-op: do not reveal email existence or activation state
        logger.debug("Resend activation requested for non-existent or already-active email");
        return;
    }
    createActivationToken(userOpt.get());
}

// AuthController.resendActivation() — always 200
@PostMapping("/resend-activation")
public ResponseEntity<?> resendActivation(@RequestBody ForgotPasswordDto request) {
    activationService.resendActivationToken(request.getEmail());
    return ResponseEntity.ok("If the email exists and is not yet activated, a new activation link has been sent.");
}
```

---

#### 3. Missing DB Migration for Existing Users (Deployment Blocker)

**File:** `src/main/resources/application.yml` — `spring.jpa.hibernate.ddl-auto: update`

`User.java` adds `@Column(nullable = false) private boolean active = false;`. Hibernate `update` mode will add the column but **will not backfill existing rows**. PostgreSQL defaults the column to `false` for all existing rows, locking out every user who registered before this deployment.

This is noted in the Phase 1 risk table but has **no migration script** and no TODO tracked to completion.

**Fix:** Add a one-time migration SQL to run after deploy, or add Flyway/Liquibase:

```sql
-- Run once after deploying this version:
UPDATE users SET active = true WHERE active = false;
```

A comment in `application.yml` or a `V2__activate_existing_users.sql` Flyway script is required before merge.

---

### High Priority Findings

#### 4. Multiple Valid Activation Tokens Per User (Unbounded Growth)

**File:** `src/main/java/com/namnd/springjwt/service/impl/ActivationServiceImpl.java` lines 36-48

`createActivationToken()` always inserts a new token row without invalidating/deleting previous ones. A user who requests resend 10 times has 10 valid tokens. All unexpired, unused tokens remain functional.

- **Impact:** Old tokens can still activate the account; no rate-limiting; table grows unbounded.
- **Fix:** Delete (or mark used) existing unused tokens for the user before inserting a new one:

```java
// Add to ActivationTokenRepository:
void deleteByUserAndUsedFalse(User user);

// In createActivationToken(), before save:
activationTokenRepository.deleteByUserAndUsedFalse(user);
```

This is explicitly flagged in the Phase 2 risk table as "Low" but the mitigation stated is incorrect — "all valid tokens work" is the problem, not the solution.

---

#### 5. Hardcoded Credentials in `application.yml` Committed to Repo

**File:** `src/main/resources/application.yml` lines 14, 24, 34

```yaml
datasource:
  password: postgres          # hardcoded DB password
mail:
  password: sdxm fmia vuzf bvmq  # hardcoded Gmail app password
jwtSecret: bezKoderSecretKey     # hardcoded JWT secret
```

These are pre-existing but this review confirms they are still present. This is a **Critical** security issue per OWASP secrets management — the Gmail app password in particular is a live credential that can be revoked at any time, and if the repo is public, it is already compromised.

**Fix:** Move all secrets to environment variables (they already have `${ENV_VAR:default}` pattern elsewhere — apply consistently):

```yaml
datasource:
  password: ${DB_PASSWORD:postgres}
mail:
  password: ${MAIL_PASSWORD}
jwtSecret: ${JWT_SECRET}
```

---

### Medium Priority Improvements

#### 6. `activateAccount` Exception Messages Distinguish Token States

**File:** `src/main/java/com/namnd/springjwt/service/impl/ActivationServiceImpl.java` lines 54-61

```java
.orElseThrow(() -> new RuntimeException("Invalid activation token"));
if (activationToken.isUsed()) { throw new RuntimeException("Activation token already used"); }
if (activationToken.getExpiryDate().before(new Date())) { throw new RuntimeException("Activation token expired"); }
```

These distinct messages are propagated to the 400 response body by the controller. An attacker probing tokens learns whether a token exists, is used, or is expired — enabling oracle-style attacks. Not as severe as the resend leak (token space is UUID), but inconsistent with security-in-depth.

**Recommended:** Return a single generic message: `"Invalid or expired activation token."` in the controller, regardless of the specific reason.

---

#### 7. `ForgotPasswordDto` Reused for Resend Activation — Naming Concern

**File:** `src/main/java/com/namnd/springjwt/controller/AuthController.java` line 137

```java
public ResponseEntity<?> resendActivation(@RequestBody ForgotPasswordDto request) {
```

The plan notes this is intentional DRY. Functionally correct (same shape: just `email`). However, `ForgotPasswordDto` is semantically misleading in this context. Consider renaming to `EmailRequestDto` or creating a shared `EmailDto`. Not blocking, but creates confusion during future maintenance.

---

#### 8. `codebase-summary.md` Docs Update Incomplete (Phase 4 Partial)

**File:** `docs/codebase-summary.md`

`ActivationToken` entity is documented but `ActivationService`, `ActivationServiceImpl`, new controller endpoints, and updated code metrics are **not** added. The Phase 4 todo list is not complete.

`docs/system-architecture.md` has **no activation flow** content at all — activation flow diagram, updated registration flow, and removal of "Email Verification" from future work are all missing.

---

### Low Priority Suggestions

#### 9. `@Column` on `active` Could Use `columnDefinition` for Clarity

**File:** `src/main/java/com/namnd/springjwt/model/User.java` line 27-28

```java
@Column(nullable = false)
private boolean active = false;
```

This works but relies on JPA/Hibernate mapping `boolean` to `boolean`/`bit` depending on DB. For PostgreSQL explicit mapping is fine. Optional: add `columnDefinition = "boolean default false"` for DDL clarity. Minor.

#### 10. Token Expiry as a Constant — Not Configurable

**File:** `src/main/java/com/namnd/springjwt/service/impl/ActivationServiceImpl.java` line 23

```java
private static final long TOKEN_EXPIRY_MS = 24 * 60 * 60 * 1000;
```

The plan mentioned it would be configurable via `application.yml` but it is hardcoded. Low impact (24h is reasonable), but inconsistent with the plan's stated design decision. The password reset expiry (30 min) is also hardcoded in the same pattern, so this is consistent with the codebase.

---

### Positive Observations

- **Spring Security integration is correct.** `UserPrinciple.isEnabled()` returning `active` properly hooks into `AbstractUserDetailsAuthenticationProvider` — no manual active-check in login logic needed.
- **Token validation order is correct** in `activateAccount`: existence → used → expired. Prevents wasted DB writes on invalid/replayed tokens.
- **`@Transactional` coverage is complete** on all write methods (createActivationToken, activateAccount, resendActivationToken).
- **`maskEmail` helper reuse** in `EmailServiceImpl` is good DRY practice.
- **`ActivationToken` faithfully mirrors `PasswordResetToken`** — consistent pattern, easy to maintain.
- **`DisabledException` handling** in login correctly distinguishes disabled accounts from bad credentials without leaking which condition triggered.
- **Compile passes clean** — no type errors, no missing dependencies.
- **GET for `/activate` endpoint** is the right HTTP method choice for idempotent link-click activation.

---

### Recommended Actions

1. **[CRITICAL - MUST FIX]** Remove token URL from SMTP fallback log line in `EmailServiceImpl` (lines 47, 69)
2. **[CRITICAL - MUST FIX]** Make `resendActivationToken` a silent no-op for unknown/already-active emails; update controller to always return generic 200
3. **[CRITICAL - DEPLOYMENT]** Add SQL migration script for existing users (`UPDATE users SET active = true WHERE active = false`)
4. **[HIGH]** Invalidate existing unused tokens before creating a new one in `createActivationToken`
5. **[HIGH]** Move hardcoded `mail.password`, `datasource.password`, `jwtSecret` to env vars (pre-existing, but surfaced again)
6. **[MEDIUM]** Collapse distinct token-state error messages in `activateAccount` to single generic response at the controller layer
7. **[MEDIUM]** Complete Phase 4 docs: add `ActivationService`/`ActivationServiceImpl` to `codebase-summary.md`; add activation flow diagram to `system-architecture.md`
8. **[LOW]** Rename `ForgotPasswordDto` to `EmailRequestDto` or create shared DTO to remove semantic confusion

---

### Metrics

- **Type coverage:** N/A (Java — statically typed; compile passes clean)
- **Test coverage:** No tests added for activation flow; existing test suite not run
- **Linting issues:** 0 compile errors; no style violations observed
- **Plan tasks completed:** Phase 1 ✓, Phase 2 ✓, Phase 3 ✓, Phase 4 partial (README ✓, codebase-summary partial, system-architecture not updated)

---

### Unresolved Questions

1. Are existing users in the DB expected to be locked out on deploy, or does the team have a migration procedure? The Phase 1 risk table mentions this but there's no script or Flyway setup.
2. Is there a rate-limiting plan for `/api/auth/resend-activation`? Currently it can be called unlimited times per email (even after the no-op fix, it still triggers DB lookups).
3. Was the 24h activation token expiry meant to be configurable via `application.yml` (the plan says so, but the implementation hardcodes it)?
