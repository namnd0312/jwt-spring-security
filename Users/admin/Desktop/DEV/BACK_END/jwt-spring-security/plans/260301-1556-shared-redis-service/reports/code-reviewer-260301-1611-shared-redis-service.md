# Code Review Report — Shared Redis Utility Service

**Date:** 2026-03-01
**Plan:** `plans/260301-1556-shared-redis-service/`
**Reviewer:** code-reviewer

---

## Code Review Summary

### Scope
- Files reviewed: 5 (RedisConfig.java, RedisKeyPrefix.java, RedisService.java, RedisServiceImpl.java, BlacklistedTokenServiceImpl.java)
- Lines of code analyzed: ~380 total (all new/modified)
- Review focus: correctness, Spring best practices, error handling, line count, security

### Overall Assessment

Implementation is clean, compiles cleanly (`mvn clean compile` — BUILD SUCCESS), and correctly achieves the goal. The abstraction is well-structured. Three issues warrant attention before moving on: one **High** (fail-closed logic gap), one **Medium** (line count breach), one **Medium** (unlock race condition). Several Low-priority items noted.

---

## Critical Issues

None.

---

## High Priority Findings

### H1 — Fail-closed bypass in `isTokenBlacklisted` is partially defeated

**File:** `BlacklistedTokenServiceImpl.java` lines 39–48
**Problem:** `RedisService.hasKey()` swallows all exceptions and returns `false` (the safe default for generic ops). The outer try-catch in `isTokenBlacklisted` is meant to catch Redis unavailability and return `true` (fail-closed). But because `RedisServiceImpl.hasKey()` already catches and returns `false`, the exception **never propagates** to `BlacklistedTokenServiceImpl`. When Redis is down, the token is incorrectly **allowed** (`false` → not blacklisted).

```java
// RedisServiceImpl.hasKey — returns false on exception, never throws
public Boolean hasKey(String key) {
    try {
        return stringRedisTemplate.hasKey(key);
    } catch (Exception e) {
        logger.error("Redis HASKEY failed for key: {}", key, e);
        return false;   // <-- exception consumed here
    }
}

// BlacklistedTokenServiceImpl.isTokenBlacklisted — catch block is dead code
try {
    return Boolean.TRUE.equals(redisService.hasKey(key));  // always returns false on error
} catch (Exception e) {
    // This NEVER runs — exception is swallowed upstream
    return true;  // intended fail-closed, but unreachable
}
```

The plan explicitly documents this concern (phase-03, risk table row: "Double error handling") but the chosen resolution ("acceptable") is incorrect: the security invariant is broken.

**Fix option A — expose Redis errors via return value convention:**
Add a `Boolean hasKeyOrNull(String key)` returning `null` on error (distinct from `false` = key absent). `BlacklistedTokenServiceImpl` treats `null` as fail-closed.

**Fix option B (simpler) — dedicated method on interface:**
```java
// RedisService
Boolean hasKeyFailOpen(String key);   // returns false on error (current behaviour)

// BlacklistedTokenServiceImpl uses raw template directly for fail-closed
// or accept the tradeoff and document it clearly
```

**Fix option C (recommended — minimal change):**
In `RedisServiceImpl.hasKey()`, rethrow after logging on connection failure only (detect `RedisConnectionFailureException`):
```java
import org.springframework.dao.DataAccessResourceFailureException;

public Boolean hasKey(String key) {
    try {
        return stringRedisTemplate.hasKey(key);
    } catch (DataAccessResourceFailureException e) {
        logger.error("Redis HASKEY failed for key: {}", key, e);
        throw e;   // let fail-closed callers handle connectivity loss
    } catch (Exception e) {
        logger.error("Redis HASKEY failed for key: {}", key, e);
        return false;
    }
}
```

---

## Medium Priority Improvements

### M1 — `RedisServiceImpl.java` exceeds 200-line limit

**File:** `RedisServiceImpl.java`
**Line count:** 277 lines (limit per project rules: 200)

The plan targeted ~195 lines but the actual file is 277. Project rules in `.claude/rules/development-rules.md` and plan phase-02 both state the 200-line ceiling. The plan already prescribes a split strategy:

- `RedisKeyValueServiceImpl.java` (key-value + pub/sub + lock) — ~140 lines
- `RedisCollectionServiceImpl.java` (hash + list + set) — ~130 lines

Both implement sub-interfaces of `RedisService`. This needs to be done.

---

### M2 — `unlock()` is not ownership-safe (distributed lock correctness)

**File:** `RedisServiceImpl.java` lines 268–275
**Problem:** `unlock(key)` simply deletes the key. If lock holder A is slow and TTL expires, and lock holder B acquires the lock, then A wakes up and calls `unlock()`, it deletes B's lock. Classic lost-lock bug.

**Fix:** Store a unique token as lock value, verify ownership before delete (Lua script or `GET` + conditional `DEL`):
```java
// tryLock: store UUID as value
String lockValue = UUID.randomUUID().toString();
Boolean result = stringRedisTemplate.opsForValue()
        .setIfAbsent(key, lockValue, timeout, unit);

// unlock: only delete if value matches
// Proper fix requires Lua script for atomicity:
String luaScript = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
```

The plan acknowledges the simple lock is "sufficient for project scope" — acceptable if no concurrent lock usage exists. However the current `isTokenBlacklisted` path does not use locks, and if no other caller uses distributed locks today, this is YAGNI-safe to defer. Flag it with a `// LIMITATION: no ownership check; callers must ensure lock release before TTL` comment at minimum.

---

## Low Priority Suggestions

### L1 — `sRemove` varargs cast is unnecessary noise

**File:** `RedisServiceImpl.java` lines 235–236
```java
Object[] objects = values;   // redundant intermediate variable
return stringRedisTemplate.opsForSet().remove(key, objects);
```
The cast exists to satisfy the compiler overload resolution. Add a comment explaining why:
```java
// Cast to Object[] to disambiguate varargs overload
return stringRedisTemplate.opsForSet().remove(key, (Object[]) values);
```

### L2 — `Jackson2JsonRedisSerializer` deprecated constructor in Spring Data Redis 3.x

**File:** `RedisConfig.java` line 19
```java
new Jackson2JsonRedisSerializer<>(Object.class)
```
The single-arg constructor taking a `Class<T>` is deprecated in Spring Data Redis 3.x+ (Spring Boot 3+). Not an issue if the project is on Boot 2.x, but worth noting for future migration. The fix uses `Jackson2JsonRedisSerializer` with an `ObjectMapper`:
```java
ObjectMapper mapper = new ObjectMapper();
mapper.activateDefaultTyping(LaissezFaireSubTypeValidator.instance, ObjectMapper.DefaultTyping.NON_FINAL);
Jackson2JsonRedisSerializer<Object> serializer = new Jackson2JsonRedisSerializer<>(mapper, Object.class);
```

### L3 — `JwtAuthenticationFilter` still uses field injection (`@Autowired`)

**File:** `JwtAuthenticationFilter.java` lines 25–32 (pre-existing, not in this PR's scope)
Field injection violates the constructor injection pattern mandated by project rules. Not introduced by this PR but noted for future cleanup.

### L4 — `getExpire` returns `0L` on error — ambiguous default

**File:** `RedisServiceImpl.java` lines 97–105
`getExpire` returning `0L` on error means callers cannot distinguish "key has 0 TTL (persists)" from "Redis error". A return of `-2L` would be more consistent with Redis's own convention (key does not exist). Acceptable as-is given current usage, but document in Javadoc.

### L5 — No null-guard on `expiryDate` in `blacklistToken`

**File:** `BlacklistedTokenServiceImpl.java` line 26
`expiryDate.getTime()` will throw NPE if `expiryDate` is null. The method signature accepts `Date expiryDate` with no validation. Minor given JWTs always have `exp`, but defensive:
```java
if (expiryDate == null) {
    logger.warn("Cannot blacklist token with null expiry (JTI: {})", jti);
    return;
}
```

---

## Positive Observations

- **Correct template split:** `StringRedisTemplate` for string ops, `RedisTemplate<String, Object>` (Jackson) for hash/object ops — appropriate and avoids serialization conflicts.
- **Consistent error handling pattern:** Every method has try-catch with error logging and sensible defaults. No exceptions leak to callers from utility methods.
- **`RedisKeyPrefix` constants file:** Clean, prevents key collision, extensible.
- **`blacklistToken` TTL guard:** Correctly skips already-expired tokens — avoids writing stale data to Redis.
- **`tryLock` null-safe:** `Boolean.TRUE.equals(result)` correctly handles null return from `setIfAbsent`.
- **`afterPropertiesSet()` called in `RedisConfig`:** Ensures template is fully initialized before use.
- **Constructor injection throughout** (except pre-existing filter — not this PR's scope).
- **Build passes clean:** `mvn clean compile` — 41 files, 0 errors.

---

## Task Completeness Verification

### Plan Phase Status

| Phase | Tasks | Status |
|-------|-------|--------|
| Phase 01 | RedisConfig.java created, RedisService.java created, mvn compile | DONE |
| Phase 02 | RedisServiceImpl.java created (26 methods), compile | DONE (file over 200 lines — see M1) |
| Phase 03 | BlacklistedTokenServiceImpl refactored, compile | DONE (fail-closed logic gap — see H1) |

### Remaining TODO items from plan phases

- Phase 01 todo: All 3 items done
- Phase 02 todo: All items done except "Confirm file is under 200 lines" — FAILED (277 lines)
- Phase 03 todo: Manual smoke test (login → logout → verify blacklist) — not verified in this review

### Post-implementation items from Next Steps

- `docs/codebase-summary.md` — not yet updated to reflect `RedisService`/`RedisConfig`
- `docs/system-architecture.md` — not yet updated

---

## Recommended Actions

1. **[High — Security]** Fix fail-closed bypass in `isTokenBlacklisted`: use `DataAccessResourceFailureException` rethrow in `RedisServiceImpl.hasKey()`, or revert `BlacklistedTokenServiceImpl` to inject raw template for the security-critical check only.
2. **[Medium — Compliance]** Split `RedisServiceImpl.java` into two files to stay under 200-line limit per project rules.
3. **[Medium — Correctness]** Add ownership comment or lock-value UUID pattern to `unlock()` to prevent accidental cross-holder unlock.
4. **[Low]** Clean up `sRemove` cast with inline cast + comment.
5. **[Low]** Add null-guard for `expiryDate` in `blacklistToken`.
6. **[Docs]** Update `docs/codebase-summary.md` and `docs/system-architecture.md` per plan Next Steps.

---

## Metrics

- **Build:** PASS (`mvn clean compile`, 41 files, 0 errors)
- **Test Coverage:** No new tests for `RedisServiceImpl` or refactored `BlacklistedTokenServiceImpl` — only a stub context-loads test exists
- **Linting Issues:** 0 errors, 0 warnings from compiler
- **Line Count:**
  - `RedisConfig.java`: 24 lines (OK)
  - `RedisKeyPrefix.java`: 15 lines (OK)
  - `RedisService.java`: 46 lines (OK)
  - `RedisServiceImpl.java`: 277 lines (**OVER 200 — needs split**)
  - `BlacklistedTokenServiceImpl.java`: 49 lines (OK)

---

## Unresolved Questions

1. **Is Spring Boot 2.x confirmed?** `Jackson2JsonRedisSerializer(Class)` deprecation only applies to Boot 3.x+. If the project is on Boot 2.x, L2 is not actionable yet.
2. **Are distributed locks actually used anywhere today?** If no caller uses `tryLock`/`unlock`, M2 should be deferred (YAGNI) and documented with a TODO comment rather than implemented now.
3. **Is manual smoke test (login → logout → blacklist) expected as part of this PR review or a separate QA step?**
