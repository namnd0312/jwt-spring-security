# Code Review Report — Auth Email Login Refactor

**Date:** 2026-03-01
**Reviewer:** code-reviewer agent
**Plan:** `plans/260301-1620-refactor-auth-email-login`

---

## Code Review Summary

### Scope
- Files reviewed: 8 source files + `UserRepository.java`, `User.java`, `RegisterDto.java`
- Lines of code analyzed: ~600
- Review focus: Recent auth refactor (username → email as principal)
- Updated plans: `plans/260301-1620-refactor-auth-email-login/plan.md`, phase-01, phase-02, phase-03

### Overall Assessment

Refactor is **functionally correct and compiles cleanly** (`mvn clean compile` exits 0). The email-based auth chain is coherent end-to-end. Several issues exist, ranging from one high-severity unchecked `Optional.get()` to medium dead-code and DRY violations, but nothing is a showstopper unless the `.get()` race is triggered in production.

---

## Critical Issues

None — no security vulnerabilities or data-loss risks introduced.

---

## High Priority Findings

### H1 — Unchecked `Optional.get()` on login (AuthController.java:71)

```java
User currentUser = userService.findByEmail(loginRequest.getEmail()).get();
```

Authentication succeeded immediately before this line, meaning `loadUserByUsername(email)` just returned a valid `UserPrinciple` — so the user almost certainly exists. However, a concurrent account deletion between auth and this line causes an unhandled `NoSuchElementException` and returns a 500. Fix:

```java
User currentUser = userService.findByEmail(loginRequest.getEmail())
        .orElseThrow(() -> new UsernameNotFoundException("User not found"));
```

**File:** `src/main/java/com/namnd/springjwt/controller/AuthController.java:71`
**Rating impact:** reduces login reliability under concurrent delete

---

### H2 — No input validation on `LoginRequestDto`

`LoginRequestDto` has no `@NotBlank` / `@Email` annotations and the controller has no `@Valid`. A request with `{ "email": null, "password": null }` passes straight into `AuthenticationManager` and produces a confusing 500 instead of 400.

```java
// LoginRequestDto.java — add:
import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;

@NotBlank @Email
private String email;

@NotBlank
private String password;

// AuthController.java — add @Valid:
public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequestDto loginRequest)
```

**File:** `src/main/java/com/namnd/springjwt/dto/LoginRequestDto.java`
`src/main/java/com/namnd/springjwt/controller/AuthController.java:58`

---

## Medium Priority Improvements

### M1 — Dead code in `UserRepository` — `findByUsername` and `existsByUsername` never called

After the refactor, `UserService` interface no longer exposes `findByUserName` or `existsByUsername`, and no service/controller calls them. The repository still declares:

```java
Optional<User> findByUsername(String userName);
Boolean existsByUsername(String userName);
```

These are dead code. Remove them to keep the repo surface minimal (YAGNI).

**File:** `src/main/java/com/namnd/springjwt/repository/UserRepository.java:12-15`

---

### M2 — DRY violation — `getJwtFromRequest()` duplicated

Identical private helper exists in both `JwtAuthenticationFilter` and `AuthController`. Extract to a utility or to `JwtService`.

```java
// JwtService.java — add:
public String extractBearerToken(HttpServletRequest request) {
    String authHeader = request.getHeader("Authorization");
    if (authHeader != null && authHeader.startsWith("Bearer ")) {
        return authHeader.replace("Bearer ", "");
    }
    return null;
}
```

**Files:** `JwtAuthenticationFilter.java:65-73`, `AuthController.java:185-190`

---

### M3 — `JwtResponseDto` uses manual boilerplate instead of Lombok

`LoginRequestDto` uses `@Data` (consistent with `User.java`), but `JwtResponseDto` has 100+ lines of manual getters/setters. Inconsistent style and violates DRY. Add `@Data` (or at minimum `@Getter @Setter`) and remove the manual methods.

**File:** `src/main/java/com/namnd/springjwt/dto/JwtResponseDto.java`

---

### M4 — Stale logger message in `JwtAuthenticationFilter`

```java
logger.error("Can NOT set user authentication -> Message: {}", e);
```

Passing the exception object `e` as `{}` placeholder logs only the exception class name / message, not the stack trace. Use:

```java
logger.error("Can NOT set user authentication", e);
```

**File:** `src/main/java/com/namnd/springjwt/config/filter/JwtAuthenticationFilter.java:59`

---

### M5 — `UserPrinciple` missing `hashCode()`

`equals()` is overridden (compares by `id`) but `hashCode()` is not. This violates the Java contract and causes undefined behaviour if `UserPrinciple` instances are put in `HashSet`/`HashMap`.

```java
@Override
public int hashCode() {
    return Objects.hashCode(id);
}
```

**File:** `src/main/java/com/namnd/springjwt/model/UserPrinciple.java:103-110`

---

## Low Priority Suggestions

### L1 — Deprecated JJWT API (`Jwts.parser()`, `SignatureAlgorithm`)

All usages of `Jwts.parser().setSigningKey()` and `SignatureAlgorithm.HS512` are deprecated in JJWT 0.11+. Consider migrating to the fluent builder API when upgrading JJWT, but not required for this refactor.

### L2 — `CORS origins = "*"` on `AuthController`

`@CrossOrigin(origins = "*")` on the auth controller is appropriate for development but should be locked down in production. Not introduced by this refactor, but worth noting.

### L3 — `register` endpoint accepts roles from client

`registerDto.getRoles()` comes directly from the request body — clients can self-assign any role. Pre-existing issue, not introduced here, but high-risk.

---

## Per-File Ratings

| File | Score | Notes |
|------|-------|-------|
| `UserPrinciple.java` | 7/10 | Correct, well-commented; missing `hashCode()` |
| `UserService.java` | 9/10 | Clean, minimal interface |
| `UserServiceImpl.java` | 9/10 | Clean; `Optional.isPresent()` could use `orElseThrow` |
| `JwtService.java` | 7/10 | Correct renaming; deprecated JJWT API (pre-existing) |
| `JwtAuthenticationFilter.java` | 7/10 | Correct; logger arg format issue; DRY violation |
| `LoginRequestDto.java` | 5/10 | No validation annotations — should block merge |
| `JwtResponseDto.java` | 6/10 | Manual boilerplate; two constructors (usage clear); `email` field added correctly |
| `AuthController.java` | 6/10 | Unchecked `.get()`; DRY violation; logic is correct |

**Overall Score: 7/10** — Functionally solid refactor, compile clean, auth chain correct. Blocked from merge by H2 (no input validation) and H1 (unsafe `.get()`).

---

## Positive Observations

- Auth chain is end-to-end consistent: `LoginRequestDto.email` → `authenticate()` → `loadUserByUsername(email)` → `findByEmail()` → JWT `sub=email` → filter resolves by email. No gaps.
- Comments on `getUsername()` returning email and on `loadUserByUsername` param semantic are helpful.
- `UserService` interface is clean and minimal after removing username methods.
- Blacklist JTI check is correctly placed before setting `SecurityContext`.
- `generateTokenFromEmail` rename is clear and accurate.

---

## Recommended Actions

1. **[Must before merge]** Add `@Valid @NotBlank @Email` to `LoginRequestDto` + `@Valid` on controller param — H2
2. **[Must before merge]** Replace `.get()` with `.orElseThrow()` at `AuthController:71` — H1
3. **[Should]** Add `hashCode()` to `UserPrinciple` — M5
4. **[Should]** Remove `findByUsername` / `existsByUsername` from `UserRepository` — M1
5. **[Should]** Extract `getJwtFromRequest` to shared utility — M2
6. **[Nice to have]** Add Lombok `@Data` to `JwtResponseDto` — M3
7. **[Nice to have]** Fix logger exception arg format in `JwtAuthenticationFilter` — M4

---

## Metrics

- Type Coverage: N/A (Java, compiler-enforced)
- Test Coverage: Not assessed (no test run requested)
- Build: PASS (`mvn clean compile` exits 0)
- Linting Issues: 0 compilation errors, 2 high, 4 medium, 3 low findings

---

## Unresolved Questions

1. Is `User.username` field now truly optional at the DB level? `@Table(name="users")` has no `NOT NULL` constraint shown, but the column still exists and `RegisterDto` still accepts it. Should the field be documented as "display name, optional"?
2. Phase 3 (docs update) — no code was changed; is documentation update still pending or intentionally deferred?
