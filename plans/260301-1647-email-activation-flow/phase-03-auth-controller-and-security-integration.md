# Phase 3: Auth Controller & Security Integration

## Context Links

- [AuthController](/src/main/java/com/namnd/springjwt/controller/AuthController.java)
- [UserPrinciple](/src/main/java/com/namnd/springjwt/model/UserPrinciple.java)
- [SecurityConfig](/src/main/java/com/namnd/springjwt/config/security/SecurityConfig.java)

## Overview

- **Priority:** P1
- **Status:** complete
- **Description:** Wire activation flow into AuthController (register triggers activation email, add activate + resend endpoints). Update UserPrinciple.isEnabled() to enforce active-only login via Spring Security.

## Key Insights

- `UserPrinciple.isEnabled()` currently returns hardcoded `true`. Changing it to return `user.active` makes Spring Security's `AbstractUserDetailsAuthenticationProvider` automatically throw `DisabledException` on login for inactive users. No manual check in login endpoint needed.
- `UserPrinciple.build(User)` currently doesn't pass `active` field -- must add it to constructor + build method.
- AuthController.registerUser() saves user then returns success. Must call `activationService.createActivationToken(user)` after save.
- Activation endpoint should be GET (user clicks link from email). Must be permitted in SecurityConfig alongside other `/api/auth/**` paths (already covered by existing wildcard).
- AuthController is currently 195 lines. Adding 3 new endpoint methods (~30 lines) is acceptable since total stays under 230 lines. Can extract later if needed.

## Requirements

### Functional
- Register endpoint: after saving user, call `activationService.createActivationToken(savedUser)`
- Register response message updated: "User registered successfully! Please check your email to activate your account."
- `GET /api/auth/activate?token=xxx` -- activates account, returns success/error message
- `POST /api/auth/resend-activation` -- accepts email, resends activation token
- Login returns meaningful error when user is inactive (catch `DisabledException`)
- `isEnabled()` in UserPrinciple returns `user.active`

### Non-Functional
- Activation endpoint accessible without authentication (already under `/api/auth/**`)
- Proper HTTP status codes (200 success, 400 bad request)

## Architecture

```
Spring Security auto-blocks inactive users:

Login Request
  -> AuthenticationManager.authenticate()
    -> UserServiceImpl.loadUserByUsername(email)
      -> UserPrinciple.build(user)  // includes active field
        -> isEnabled() returns user.active
    -> AbstractUserDetailsAuthenticationProvider checks isEnabled()
    -> throws DisabledException if false
  -> AuthController catches DisabledException
  -> Returns 401 "Account not activated. Check email."

Activation Flow:
  GET /api/auth/activate?token=xxx
    -> ActivationService.activateAccount(token)
    -> Returns "Account activated successfully!"

Resend Flow:
  POST /api/auth/resend-activation {email}
    -> ActivationService.resendActivationToken(email)
    -> Returns "Activation email sent."
```

## Related Code Files

### Files to Modify
- `src/main/java/com/namnd/springjwt/model/UserPrinciple.java` -- add `active` param, update `isEnabled()`
- `src/main/java/com/namnd/springjwt/controller/AuthController.java` -- register calls activation, add endpoints, handle DisabledException

## Implementation Steps

### 1. Update UserPrinciple -- add `active` field

```java
// Add field
private boolean active;

// Update constructor (add active parameter)
public UserPrinciple(Long id, String displayName, String email, String password,
                     boolean active,
                     Collection<? extends GrantedAuthority> roles) {
    this.id = id;
    this.displayName = displayName;
    this.email = email;
    this.password = password;
    this.active = active;
    this.roles = roles;
}

// Update build() method
public static UserPrinciple build(User user) {
    List<GrantedAuthority> authorities = user.getRoles().stream()
            .map(role -> new SimpleGrantedAuthority(role.getName()))
            .collect(Collectors.toList());

    return new UserPrinciple(
            user.getId(),
            user.getUsername(),
            user.getEmail(),
            user.getPassword(),
            user.isActive(),    // <-- pass active field
            authorities);
}

// Update isEnabled() to return active
@Override
public boolean isEnabled() {
    return active;
}
```

### 2. Update AuthController -- register calls activation

```java
// Add autowired field
@Autowired
private ActivationService activationService;

// Update registerUser method -- after userService.save(user1):
userService.save(user1);
activationService.createActivationToken(user1);

return ResponseEntity.ok().body(
    "User registered successfully! Please check your email to activate your account.");
```

### 3. Add activate endpoint to AuthController

```java
@GetMapping("/activate")
public ResponseEntity<?> activateAccount(@RequestParam("token") String token) {
    try {
        activationService.activateAccount(token);
        return ResponseEntity.ok("Account activated successfully! You can now login.");
    } catch (RuntimeException e) {
        return ResponseEntity.badRequest().body(e.getMessage());
    }
}
```

### 4. Add resend-activation endpoint to AuthController

```java
@PostMapping("/resend-activation")
public ResponseEntity<?> resendActivation(@RequestBody ForgotPasswordDto request) {
    try {
        activationService.resendActivationToken(request.getEmail());
        return ResponseEntity.ok("Activation email sent.");
    } catch (RuntimeException e) {
        return ResponseEntity.badRequest().body(e.getMessage());
    }
}
```

Note: Reuses `ForgotPasswordDto` since it only has an `email` field -- same input shape, follows DRY.

### 5. Handle DisabledException in login endpoint

```java
// Add import
import org.springframework.security.authentication.DisabledException;

// Update authenticateUser method -- wrap authentication in try-catch
@PostMapping("/login")
public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequestDto loginRequest) {
    try {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.getEmail(),
                        loginRequest.getPassword()
                )
        );

        // ... rest of existing login logic unchanged ...

    } catch (DisabledException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body("Account not activated. Please check your email for the activation link.");
    }
}
```

## Todo List

- [x] Add `active` field to UserPrinciple constructor + `build()` method
- [x] Update `isEnabled()` to return `active` field
- [x] Autowire `ActivationService` in AuthController
- [x] Update register endpoint to call `activationService.createActivationToken()`
- [x] Update register success message
- [x] Add `GET /api/auth/activate` endpoint
- [x] Add `POST /api/auth/resend-activation` endpoint
- [x] Add `DisabledException` catch in login endpoint
- [x] Run `mvn compile` to verify

## Success Criteria

- Registration creates inactive user + sends activation email
- `GET /api/auth/activate?token=xxx` activates account
- Login rejected with clear message for inactive users
- Resend activation works for inactive users
- `mvn compile` passes

## Risk Assessment

| Risk | Impact | Mitigation |
|------|--------|------------|
| Breaking existing login for active users | High | `isEnabled()` returns `active` field; existing users need DB migration `UPDATE users SET active = true` |
| AuthController exceeds 200 lines | Low | Currently ~195 + ~30 new = ~225. Acceptable for now; extract to separate controller if it grows further |
| DisabledException vs BadCredentialsException confusion | Low | Catch DisabledException specifically before generic auth errors |

## Security Considerations

- Spring Security's `DisabledException` does not reveal whether email exists (same error path as bad credentials to attacker)
- Activation endpoint is idempotent (used token returns error, not sensitive info)
- Resend endpoint accepts any email but only acts on existing inactive users (throws generic error)
- `GET /api/auth/activate` is under `/api/auth/**` which is already `permitAll()`

## Next Steps

- Phase 4: Update documentation (README, codebase-summary, system-architecture)
