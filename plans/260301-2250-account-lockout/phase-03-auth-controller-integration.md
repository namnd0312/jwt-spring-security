# Phase 3: Auth Controller Integration

## Context Links

- [AuthController.java](/src/main/java/com/namnd/springjwt/controller/AuthController.java)
- [UserServiceImpl.java](/src/main/java/com/namnd/springjwt/service/impl/UserServiceImpl.java)
- [Phase 1](./phase-01-entity-and-config.md)
- [Phase 2](./phase-02-account-lock-service.md)

## Overview

- **Priority:** P1
- **Status:** pending
- **Description:** Wire AccountLockService into login endpoint. Handle auth flow ordering: auto-unlock expired locks before auth, catch LockedException and BadCredentialsException.

## Key Insights

### Auth Flow Ordering (Critical)

Spring Security's `DaoAuthenticationProvider` does this internally:
1. Calls `loadUserByUsername()` -> builds `UserPrinciple`
2. Calls `UserPrinciple.isAccountNonLocked()` -> throws `LockedException` if false
3. Checks password -> throws `BadCredentialsException` if wrong

**Problem**: if lock expired, we must unlock BEFORE `authenticate()` is called, otherwise `UserPrinciple.build()` sees stale lockTime and throws LockedException.

**Solution**: before calling `authenticationManager.authenticate()`, look up User, call `unlockIfExpired()`. This clears lockTime in DB. Then `loadUserByUsername()` builds UserPrinciple with lockTime=null -> isAccountNonLocked()=true.

### Flow

```
1. Find user by email (optional -- may not exist)
2. If user exists and locked: unlockIfExpired()
   - If still locked: return 423 with remaining time (skip authenticate entirely)
3. authenticationManager.authenticate()
   - LockedException: should not happen (we checked above), but handle as safety net
   - BadCredentialsException: registerFailedAttempt()
   - DisabledException: return 401 "not activated"
   - Success: resetFailedAttempts(), return JWT
```

## Requirements

### Functional
- Pre-auth lock expiry check
- Increment failed attempts on bad credentials
- Lock account when threshold reached, return 423 with remaining time
- Reset failed attempts on successful login
- Return meaningful error messages with lock remaining time

### Non-functional
- No additional DB queries on happy path beyond what already exists (one extra findByEmail pre-check)
- HTTP 423 (Locked) for locked accounts
- No information leakage: don't reveal if email exists when locked (but current login already reveals this via different error messages -- accepted trade-off)

## Related Code Files

### Modify
- `src/main/java/com/namnd/springjwt/controller/AuthController.java`

## Implementation Steps

### Step 1: Inject AccountLockService in AuthController

Add after existing @Autowired fields:

```java
@Autowired
private AccountLockService accountLockService;
```

Add import:
```java
import com.namnd.springjwt.service.AccountLockService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
```

### Step 2: Rewrite login method

Replace the entire `authenticateUser` method:

```java
@PostMapping("/login")
public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequestDto loginRequest) {
    try {
        // Pre-auth: check if account lock expired, auto-unlock if so
        Optional<User> userOpt = userService.findByEmail(loginRequest.getEmail());
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            if (!accountLockService.unlockIfExpired(user)) {
                // Still locked
                long remainingMs = accountLockService.getRemainingLockTimeMs(user);
                long remainingMin = (remainingMs / 60000) + 1;
                return ResponseEntity.status(HttpStatus.LOCKED)
                        .body("Account is locked. Try again in " + remainingMin + " minute(s).");
            }
        }

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.getEmail(),
                        loginRequest.getPassword()
                )
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);

        // Success: reset failed attempts
        accountLockService.resetFailedAttempts(loginRequest.getEmail());

        String jwt = jwtService.generateTokenLogin(authentication);
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        User currentUser = userService.findByEmail(loginRequest.getEmail())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        RefreshToken refreshToken = refreshTokenService.createRefreshToken(currentUser.getId());

        return ResponseEntity.ok(new JwtResponseDto(
                currentUser.getId(),
                jwt,
                refreshToken.getToken(),
                currentUser.getEmail(),
                currentUser.getUsername(),
                currentUser.getFullName(),
                userDetails.getAuthorities()));

    } catch (BadCredentialsException e) {
        // Wrong password: register failed attempt
        accountLockService.registerFailedAttempt(loginRequest.getEmail());

        // Check if this attempt caused a lock
        Optional<User> userOpt = userService.findByEmail(loginRequest.getEmail());
        if (userOpt.isPresent() && accountLockService.isLocked(userOpt.get())) {
            long remainingMs = accountLockService.getRemainingLockTimeMs(userOpt.get());
            long remainingMin = (remainingMs / 60000) + 1;
            return ResponseEntity.status(HttpStatus.LOCKED)
                    .body("Too many failed attempts. Account locked for " + remainingMin + " minute(s).");
        }

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body("Invalid email or password.");

    } catch (LockedException e) {
        // Safety net: shouldn't reach here due to pre-check, but handle gracefully
        return ResponseEntity.status(HttpStatus.LOCKED)
                .body("Account is locked. Please try again later.");

    } catch (DisabledException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body("Account not activated. Please check your email for the activation link.");
    }
}
```

**Note:** `HttpStatus.LOCKED` is HTTP 423 -- available in Spring's HttpStatus enum.

### Step 3: Add required import

```java
import org.springframework.security.core.userdetails.UsernameNotFoundException;
```

This import may already be present from the existing code. Verify during implementation.

## Todo List

- [ ] Inject AccountLockService into AuthController
- [ ] Add required imports (BadCredentialsException, LockedException)
- [ ] Rewrite login method with pre-auth lock check
- [ ] Handle BadCredentialsException with failed attempt registration
- [ ] Handle LockedException as safety net
- [ ] Keep existing DisabledException handling
- [ ] Compile and verify no errors
- [ ] Test scenarios: successful login resets count, bad password increments, lockout triggers, auto-unlock works

## Success Criteria

- Successful login resets failedAttempts to 0
- Each bad credential increments failedAttempts
- Account locks after N (configurable) failed attempts
- Locked response returns 423 with remaining time
- Lock auto-expires after configured duration
- No regression on existing login/disable flows

## Risk Assessment

- **Extra DB query**: one additional findByEmail before authenticate(). Acceptable -- simple indexed query.
- **HTTP 423**: non-standard for auth APIs. Alternative: use 401 with specific error code. Using 423 is more semantically correct and differentiable from bad credentials.
- **Email enumeration**: locked response could confirm email exists. Same trade-off already present in current DisabledException handling.

## Security Considerations

- Failed attempts tracked per-user in DB, not in-memory (survives restarts)
- Lock cannot be bypassed by restarting server
- No password info leaked in lock responses
- Attacker learns account exists via lock message (accepted; same as existing disable flow)
- Consider rate limiting at network/API gateway level for additional protection (out of scope)

## Next Steps

- After implementation, run `mvn compile` to verify
- Test with Postman/curl: 5 wrong passwords -> lock -> wait 15min -> unlock
- Consider adding lockout event to audit log (future enhancement)
