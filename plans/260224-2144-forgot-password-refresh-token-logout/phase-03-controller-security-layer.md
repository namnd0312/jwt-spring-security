# Phase 3: Controller & Security Layer

## Context Links

- [Plan Overview](./plan.md)
- [Phase 1 - Database & Entity Layer](./phase-01-database-entity-layer.md)
- [Phase 2 - Service Layer](./phase-02-service-layer.md)
- [AuthController.java](../src/main/java/com/namnd/springjwt/controller/AuthController.java)
- [JwtAuthenticationFilter.java](../src/main/java/com/namnd/springjwt/config/filter/JwtAuthenticationFilter.java)
- [SecurityConfig.java](../src/main/java/com/namnd/springjwt/config/security/SecurityConfig.java)
- [JwtResponseDto.java](../src/main/java/com/namnd/springjwt/dto/JwtResponseDto.java)
- [RegisterDto.java](../src/main/java/com/namnd/springjwt/dto/RegisterDto.java)
- [SpringJwtApplication.java](../src/main/java/com/namnd/springjwt/SpringJwtApplication.java)

## Overview

- **Date:** 2026-02-24
- **Priority:** P1
- **Status:** pending
- **Effort:** 1.5h
- **Description:** Create 4 new DTOs, update 2 existing DTOs, add 4 new endpoints to AuthController (forgot-password, reset-password, refresh-token, logout), modify JwtAuthenticationFilter to check token blacklist, add `@EnableScheduling` to app, and update SecurityConfig to permit new auth endpoints.

## Key Insights

- All new endpoints are under `/api/auth/**` which is already `permitAll()` in SecurityConfig, except logout which needs authentication
- Logout endpoint requires the authenticated user's info to delete their refresh token; must extract user from SecurityContext
- JwtAuthenticationFilter must check blacklist AFTER validating JWT signature (blacklist check = DB query on every request)
- The existing `JwtResponseDto` uses manual getters/setters (no Lombok); adding `refreshToken` field requires adding getter/setter
- SecurityConfig already permits `/api/auth/**`; logout is also under this path but needs `@PreAuthorize` or manual auth check
- Better approach for logout: place it at `/api/auth/logout` but require Bearer token in the controller logic (check SecurityContextHolder)

## Requirements

### Functional
- **ForgotPasswordDto**: email field; used for POST /api/auth/forgot-password
- **ResetPasswordDto**: token + newPassword fields; used for POST /api/auth/reset-password
- **RefreshTokenRequestDto**: refreshToken field; used for POST /api/auth/refresh-token
- **TokenRefreshResponseDto**: accessToken + refreshToken + tokenType fields; response for refresh
- **JwtResponseDto**: add refreshToken field to login response
- **RegisterDto**: add email field to registration request
- **AuthController**: 4 new endpoints (forgot-password, reset-password, refresh-token, logout)
- **JwtAuthenticationFilter**: check blacklist after JWT validation
- **SpringJwtApplication**: add `@EnableScheduling`

### Non-Functional
- Each file under 200 lines; if AuthController exceeds 200 lines, keep it as-is since splitting a controller is worse than a slightly longer file
- Logout endpoint must return 200 on success
- Forgot-password must always return 200 (no email existence leakage)
- Reset-password returns 400 on invalid/expired/used token
- Refresh-token returns 400 on invalid/expired refresh token

## Architecture

```
dto/
├── JwtResponseDto.java            (MODIFY - add refreshToken field + getter/setter)
├── RegisterDto.java               (MODIFY - add email field + getter/setter)
├── ForgotPasswordDto.java         (NEW)
├── ResetPasswordDto.java          (NEW)
├── RefreshTokenRequestDto.java    (NEW)
└── TokenRefreshResponseDto.java   (NEW)

controller/
└── AuthController.java            (MODIFY - add 4 endpoints)

config/
├── filter/JwtAuthenticationFilter.java  (MODIFY - add blacklist check)
└── security/SecurityConfig.java         (NO CHANGE - /api/auth/** already permitAll)

SpringJwtApplication.java         (MODIFY - add @EnableScheduling)
```

### New Endpoint Flow Diagrams

```
POST /api/auth/forgot-password
  Client -> {email} -> AuthController.forgotPassword()
    -> PasswordResetService.createPasswordResetToken(email)
      -> Find user by email (silently skip if not found)
      -> Generate UUID token, save to DB
      -> EmailService.sendPasswordResetEmail(email, token)
    -> Return 200 "If email exists, reset link sent"

POST /api/auth/reset-password
  Client -> {token, newPassword} -> AuthController.resetPassword()
    -> PasswordResetService.resetPassword(token, newPassword)
      -> Validate token (exists, not used, not expired)
      -> Update user password (BCrypt encoded)
      -> Mark token as used
    -> Return 200 "Password reset successful"
    -> OR Return 400 "Invalid or expired token"

POST /api/auth/refresh-token
  Client -> {refreshToken} -> AuthController.refreshToken()
    -> RefreshTokenRepository.findByToken(refreshToken)
    -> RefreshTokenService.verifyExpiration(token)
    -> JwtService.generateTokenFromUsername(user.getUsername())
    -> RefreshTokenService.createRefreshToken(userId)  // rotation
    -> Return {accessToken, refreshToken, tokenType}

POST /api/auth/logout (requires Bearer token)
  Client -> Authorization: Bearer <token> -> AuthController.logout()
    -> Extract JWT from request header
    -> Extract user from SecurityContext
    -> BlacklistedTokenService.blacklistToken(jwt, expiryDate)
    -> RefreshTokenService.deleteByUserId(userId)
    -> Return 200 "Logged out successfully"
```

## Related Code Files

### Files to Modify
- `src/main/java/com/namnd/springjwt/dto/JwtResponseDto.java` - add refreshToken field
- `src/main/java/com/namnd/springjwt/dto/RegisterDto.java` - add email field
- `src/main/java/com/namnd/springjwt/controller/AuthController.java` - add 4 endpoints
- `src/main/java/com/namnd/springjwt/config/filter/JwtAuthenticationFilter.java` - add blacklist check
- `src/main/java/com/namnd/springjwt/SpringJwtApplication.java` - add @EnableScheduling

### Files to Create
- `src/main/java/com/namnd/springjwt/dto/ForgotPasswordDto.java`
- `src/main/java/com/namnd/springjwt/dto/ResetPasswordDto.java`
- `src/main/java/com/namnd/springjwt/dto/RefreshTokenRequestDto.java`
- `src/main/java/com/namnd/springjwt/dto/TokenRefreshResponseDto.java`

### Files NOT Modified
- `SecurityConfig.java` - `/api/auth/**` already permitAll; no changes needed

## Implementation Steps

### Step 1: Add @EnableScheduling to SpringJwtApplication.java

Add `@EnableScheduling` annotation to the main class:

```java
package com.namnd.springjwt;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SpringJwtApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringJwtApplication.class, args);
    }
}
```

### Step 2: Create ForgotPasswordDto.java

Create `src/main/java/com/namnd/springjwt/dto/ForgotPasswordDto.java`:

```java
package com.namnd.springjwt.dto;

import lombok.Data;

@Data
public class ForgotPasswordDto {

    private String email;
}
```

### Step 3: Create ResetPasswordDto.java

Create `src/main/java/com/namnd/springjwt/dto/ResetPasswordDto.java`:

```java
package com.namnd.springjwt.dto;

import lombok.Data;

@Data
public class ResetPasswordDto {

    private String token;

    private String newPassword;
}
```

### Step 4: Create RefreshTokenRequestDto.java

Create `src/main/java/com/namnd/springjwt/dto/RefreshTokenRequestDto.java`:

```java
package com.namnd.springjwt.dto;

import lombok.Data;

@Data
public class RefreshTokenRequestDto {

    private String refreshToken;
}
```

### Step 5: Create TokenRefreshResponseDto.java

Create `src/main/java/com/namnd/springjwt/dto/TokenRefreshResponseDto.java`:

```java
package com.namnd.springjwt.dto;

import lombok.Data;

@Data
public class TokenRefreshResponseDto {

    private String accessToken;

    private String refreshToken;

    private String tokenType = "Bearer";

    public TokenRefreshResponseDto(String accessToken, String refreshToken) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
    }
}
```

### Step 6: Modify JwtResponseDto.java - add refreshToken field

Add a `refreshToken` field with getter/setter. Also add a new constructor that accepts it. Keep backward compat by keeping the old constructor:

```java
private String refreshToken;

// New constructor with refreshToken
public JwtResponseDto(Long id, String token, String refreshToken, String username,
                      String name, Collection<? extends GrantedAuthority> roles) {
    this.id = id;
    this.token = token;
    this.refreshToken = refreshToken;
    this.username = username;
    this.name = name;
    this.roles = roles;
}

public String getRefreshToken() {
    return refreshToken;
}

public void setRefreshToken(String refreshToken) {
    this.refreshToken = refreshToken;
}
```

### Step 7: Modify RegisterDto.java - add email field

Add `email` field with getter/setter at the end of the class:

```java
private String email;

public String getEmail() {
    return email;
}

public void setEmail(String email) {
    this.email = email;
}
```

### Step 8: Modify JwtAuthenticationFilter.java - add blacklist check

Add `BlacklistedTokenService` dependency and check blacklist after JWT validation:

```java
@Autowired
private BlacklistedTokenService blacklistedTokenService;
```

Modify the `doFilterInternal` method to check blacklist after `validateJwtToken`:

```java
@Override
protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                FilterChain filterChain) throws ServletException, IOException {
    try {
        String jwt = getJwtFromRequest(request);

        if (jwt != null && jwtService.validateJwtToken(jwt)) {
            // Check if token is blacklisted (logout)
            if (blacklistedTokenService.isTokenBlacklisted(jwt)) {
                logger.warn("Blacklisted token used in request");
            } else {
                String username = jwtService.getUserNameFromJwtToken(jwt);
                UserDetails userDetails = userService.loadUserByUsername(username);

                UsernamePasswordAuthenticationToken authentication
                        = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());
                authentication.setDetails(
                    new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }
    } catch (Exception e) {
        logger.error("Can NOT set user authentication -> Message: {}", e);
    }

    filterChain.doFilter(request, response);
}
```

Add import for `BlacklistedTokenService`:
```java
import com.namnd.springjwt.service.BlacklistedTokenService;
```

### Step 9: Modify AuthController.java - add 4 new endpoints

Add new service dependencies via `@Autowired`:

```java
@Autowired
private PasswordResetService passwordResetService;

@Autowired
private RefreshTokenService refreshTokenService;

@Autowired
private BlacklistedTokenService blacklistedTokenService;
```

Add necessary imports:

```java
import com.namnd.springjwt.dto.ForgotPasswordDto;
import com.namnd.springjwt.dto.ResetPasswordDto;
import com.namnd.springjwt.dto.RefreshTokenRequestDto;
import com.namnd.springjwt.dto.TokenRefreshResponseDto;
import com.namnd.springjwt.model.RefreshToken;
import com.namnd.springjwt.service.PasswordResetService;
import com.namnd.springjwt.service.RefreshTokenService;
import com.namnd.springjwt.service.BlacklistedTokenService;
import javax.servlet.http.HttpServletRequest;
import java.util.Date;
```

**Modify existing login endpoint** to include refresh token in response:

```java
@PostMapping("/login")
public ResponseEntity<?> authenticateUser(@RequestBody User user) {

    Authentication authentication = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(
                    user.getUsername(),
                    user.getPassword()
            )
    );

    SecurityContextHolder.getContext().setAuthentication(authentication);

    String jwt = jwtService.generateTokenLogin(authentication);
    UserDetails userDetails = (UserDetails) authentication.getPrincipal();
    User currentUser = userService.findByUserName(user.getUsername()).get();

    RefreshToken refreshToken = refreshTokenService.createRefreshToken(currentUser.getId());

    return ResponseEntity.ok(new JwtResponseDto(
            currentUser.getId(),
            jwt,
            refreshToken.getToken(),
            userDetails.getUsername(),
            currentUser.getFullName(),
            userDetails.getAuthorities()));
}
```

**Add forgot-password endpoint:**

```java
@PostMapping("/forgot-password")
public ResponseEntity<?> forgotPassword(@RequestBody ForgotPasswordDto forgotPasswordDto) {
    passwordResetService.createPasswordResetToken(forgotPasswordDto.getEmail());
    return ResponseEntity.ok("If the email exists, a password reset link has been sent.");
}
```

**Add reset-password endpoint:**

```java
@PostMapping("/reset-password")
public ResponseEntity<?> resetPassword(@RequestBody ResetPasswordDto resetPasswordDto) {
    try {
        passwordResetService.resetPassword(
                resetPasswordDto.getToken(),
                resetPasswordDto.getNewPassword());
        return ResponseEntity.ok("Password reset successful.");
    } catch (RuntimeException e) {
        return ResponseEntity.badRequest().body(e.getMessage());
    }
}
```

**Add refresh-token endpoint:**

```java
@PostMapping("/refresh-token")
public ResponseEntity<?> refreshToken(
        @RequestBody RefreshTokenRequestDto refreshTokenRequestDto) {
    String requestRefreshToken = refreshTokenRequestDto.getRefreshToken();

    return refreshTokenRepository.findByToken(requestRefreshToken)
            .map(refreshTokenService::verifyExpiration)
            .map(RefreshToken::getUser)
            .map(user -> {
                String newAccessToken = jwtService.generateTokenFromUsername(user.getUsername());
                RefreshToken newRefreshToken = refreshTokenService
                        .createRefreshToken(user.getId());
                return ResponseEntity.ok(new TokenRefreshResponseDto(
                        newAccessToken, newRefreshToken.getToken()));
            })
            .orElseGet(() -> ResponseEntity.badRequest()
                    .body(new TokenRefreshResponseDto("", "")));
}
```

**IMPORTANT:** The refresh-token endpoint needs direct access to `RefreshTokenRepository`. Add this dependency:

```java
@Autowired
private RefreshTokenRepository refreshTokenRepository;
```

And import:
```java
import com.namnd.springjwt.repository.RefreshTokenRepository;
```

**Alternative (cleaner):** Add a `findByToken(String token)` method to `RefreshTokenService` interface and impl, then use that instead of injecting the repository into the controller. This is the preferred approach:

Add to `RefreshTokenService.java`:
```java
Optional<RefreshToken> findByToken(String token);
```

Add to `RefreshTokenServiceImpl.java`:
```java
@Override
public Optional<RefreshToken> findByToken(String token) {
    return refreshTokenRepository.findByToken(token);
}
```

Then the controller uses:
```java
@PostMapping("/refresh-token")
public ResponseEntity<?> refreshToken(
        @RequestBody RefreshTokenRequestDto refreshTokenRequestDto) {
    String requestRefreshToken = refreshTokenRequestDto.getRefreshToken();

    Optional<RefreshToken> tokenOptional = refreshTokenService
            .findByToken(requestRefreshToken);

    if (!tokenOptional.isPresent()) {
        return ResponseEntity.badRequest().body("Invalid refresh token.");
    }

    RefreshToken token = refreshTokenService.verifyExpiration(tokenOptional.get());
    User user = token.getUser();

    String newAccessToken = jwtService.generateTokenFromUsername(user.getUsername());
    RefreshToken newRefreshToken = refreshTokenService.createRefreshToken(user.getId());

    return ResponseEntity.ok(new TokenRefreshResponseDto(
            newAccessToken, newRefreshToken.getToken()));
}
```

**Add logout endpoint:**

```java
@PostMapping("/logout")
public ResponseEntity<?> logout(HttpServletRequest request) {
    String jwt = getJwtFromRequest(request);

    if (jwt == null) {
        return ResponseEntity.badRequest().body("No token provided.");
    }

    // Blacklist the current access token
    Date tokenExpiry = new Date(
            System.currentTimeMillis() + jwtService.getExpireTime() * 1000);
    blacklistedTokenService.blacklistToken(jwt, tokenExpiry);

    // Delete the user's refresh token
    String username = jwtService.getUserNameFromJwtToken(jwt);
    Optional<User> userOptional = userService.findByUserName(username);
    userOptional.ifPresent(user -> refreshTokenService.deleteByUserId(user.getId()));

    return ResponseEntity.ok("Logged out successfully.");
}

private String getJwtFromRequest(HttpServletRequest request) {
    String authHeader = request.getHeader("Authorization");
    if (authHeader != null && authHeader.startsWith("Bearer ")) {
        return authHeader.replace("Bearer ", "");
    }
    return null;
}
```

### Step 10: Verify AuthController file size

After all changes, AuthController will be approximately 160-180 lines. This is within the 200-line limit, so no splitting needed.

### Step 11: Run compile check

```bash
mvn clean compile
```

Fix any compilation errors before proceeding to Phase 4.

## Todo List

- [ ] Add @EnableScheduling to SpringJwtApplication.java
- [ ] Create ForgotPasswordDto.java
- [ ] Create ResetPasswordDto.java
- [ ] Create RefreshTokenRequestDto.java
- [ ] Create TokenRefreshResponseDto.java
- [ ] Add refreshToken field to JwtResponseDto.java
- [ ] Add email field to RegisterDto.java
- [ ] Add blacklist check to JwtAuthenticationFilter.java
- [ ] Add findByToken to RefreshTokenService interface and impl
- [ ] Modify login endpoint in AuthController to include refresh token
- [ ] Add forgot-password endpoint to AuthController
- [ ] Add reset-password endpoint to AuthController
- [ ] Add refresh-token endpoint to AuthController
- [ ] Add logout endpoint to AuthController
- [ ] Run mvn clean compile and fix errors

## Success Criteria

- `mvn clean compile` passes with zero errors
- All 4 new DTO classes exist
- JwtResponseDto has refreshToken field
- RegisterDto has email field
- AuthController has 6 endpoints total (login, register, forgot-password, reset-password, refresh-token, logout)
- JwtAuthenticationFilter checks blacklist before setting SecurityContext
- SpringJwtApplication has @EnableScheduling
- AuthController stays under 200 lines

## Risk Assessment

| Risk | Impact | Mitigation |
|------|--------|------------|
| Blacklist DB query on every authenticated request | Medium - performance | Blacklist table is small (only active tokens); add index on token column if needed; consider caching later |
| Logout endpoint under /api/auth/** is permitAll | Medium - anyone can call it | Endpoint requires valid Bearer token to extract JWT; returns error if no token |
| Refresh token rotation creates new token before deleting old | Low | `createRefreshToken` deletes existing first via `findByUser` + `delete` |
| AuthController approaching 200 lines | Low | Estimated ~170 lines; acceptable for a single auth controller |
| Login response shape changes (adds refreshToken) | Medium - breaking API change | Frontend must be updated to handle new field; old clients ignore extra field (JSON) |

## Security Considerations

- **Forgot-password** always returns 200 regardless of email existence (prevents email enumeration)
- **Reset-password** validates token state (exists, not expired, not used) before changing password
- **Refresh-token** uses rotation: old token deleted, new token issued on each refresh
- **Logout** blacklists the access token AND deletes the refresh token (double protection)
- **Blacklist check** happens before SecurityContext is set, blocking blacklisted tokens from accessing any protected resource
- **Token extraction** in logout uses the same parsing logic as JwtAuthenticationFilter
- No sensitive data (passwords, full tokens) is logged

## Next Steps

- Phase 4: Testing & verification of all flows
- After Phase 4: Update README.md with new API endpoints
- After Phase 4: Update docs/system-architecture.md with new flow diagrams
