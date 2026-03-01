---
title: "Account Lockout on Failed Login"
description: "Lock accounts after N failed login attempts with configurable duration"
status: completed
priority: P1
effort: 2h
branch: master
tags: [auth, security, lockout, brute-force]
created: 2026-03-01
completed: 2026-03-01
---

# Account Lockout on Failed Login

## Overview

Protect against brute-force attacks by locking accounts after N consecutive failed login attempts. Auto-unlock after configurable duration expires.

## Architecture

```
Login Request
  |
  v
[Check lock expired? -> auto-unlock if yes]
  |
  v
[authenticationManager.authenticate()]
  |--- LockedException --> return 423 + remaining time
  |--- BadCredentialsException --> registerFailedAttempt() --> maybe lock
  |--- DisabledException --> return 401 "not activated"
  |--- Success --> resetFailedAttempts() --> return JWT
```

## Phases

| # | Phase | Effort | Status |
|---|-------|--------|--------|
| 1 | [Entity & Config](./phase-01-entity-and-config.md) | 30m | completed ✓ |
| 2 | [Account Lock Service](./phase-02-account-lock-service.md) | 45m | completed ✓ |
| 3 | [Auth Controller Integration](./phase-03-auth-controller-integration.md) | 45m | completed ✓ |

## Files Modified

- `model/User.java` -- add failedAttempts, lockTime fields
- `model/UserPrinciple.java` -- add locked field, wire isAccountNonLocked()
- `controller/AuthController.java` -- handle lock flow in login endpoint
- `application.yml` -- add lockout config properties
- `service/impl/UserServiceImpl.java` -- save method already exists, no change needed

## Files Created

- `service/AccountLockService.java` -- interface
- `service/impl/AccountLockServiceImpl.java` -- implementation

## Key Dependencies

- JPA ddl-auto=update handles schema migration (adds columns)
- No new Maven dependencies needed
- Spring Security's DaoAuthenticationProvider auto-checks isAccountNonLocked()

## Risks

- **DB migration**: ddl-auto=update adds nullable columns; existing users get failedAttempts=0, lockTime=null (safe defaults) ✓ Mitigated
- **Race condition**: concurrent login attempts could cause inconsistent failedAttempts count; acceptable for this use case (not security-critical if off by 1) ✓ Documented

## Code Review Status

✓ **COMPLETE** - See [code-reviewer report](./reports/code-reviewer-260301-2258-account-lockout.md)

**Overall Rating:** 9/10 - Production-ready with 4 Priority-1 refinements recommended

**Critical Issues:** None
**High Priority Findings:** 3 (race condition comment, input validation, remaining time calculation)
**Recommended Actions:** 8 items (4 before merge, 4 in next sprint)

## Next Steps

1. **Apply Priority 1 fixes** (5-10 minutes):
   - Add null/empty email validation to AccountLockService methods
   - Fix remaining time calculation with proper ceiling division
   - Add explanatory comment on race condition
   - Add Javadoc to UserPrinciple.build()

2. **Optional Priority 2 improvements** (within sprint):
   - Extract locked account response to helper method
   - Add method-level documentation to AccountLockServiceImpl
   - Clarify return value semantics in unlockIfExpired()

3. **Future enhancements** (separate task):
   - Native SQL UPDATE with increment to eliminate race condition
   - Audit logging for lockout events
   - Verify API gateway rate limiting is in place
