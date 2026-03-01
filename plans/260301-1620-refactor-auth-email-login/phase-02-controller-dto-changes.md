# Phase 2: Controller & DTO Changes

## Context Links
- [Plan Overview](./plan.md)
- [Phase 1: Core Auth Refactor](./phase-01-core-auth-refactor.md)
- Depends on: Phase 1 completion

## Overview
- **Priority:** P1
- **Status:** complete
- **Description:** Create LoginRequestDto, update AuthController to use email-based login, update JwtResponseDto to include email, fix logout/refresh flows

## Key Insights
- Login currently accepts raw `User` entity as `@RequestBody` -- bad practice. Introducing `LoginRequestDto` improves API design
- Registration must remove username uniqueness check but keep email uniqueness
- Logout extracts identifier from JWT (`sub`=email now) and uses `findByEmail()` instead of `findByUserName()`
- Refresh token flow calls `generateTokenFromUsername()` with `user.getUsername()` -- must change to `generateTokenFromEmail()` with `user.getEmail()`
- `JwtResponseDto` should include `email` field so clients can display it

## Requirements

### Functional
- `POST /api/auth/login` accepts `{ "email": "...", "password": "..." }`
- `POST /api/auth/register` no longer checks username uniqueness
- Login response includes `email` field
- Logout resolves user by email from JWT
- Refresh token generates new token using email

### Non-Functional
- Maintain backward compatibility for `username` field in JwtResponseDto (keep but add `email`)

## Architecture

```
POST /api/auth/login
    { "email": "user@example.com", "password": "pass123" }
    |
    v
AuthController.authenticateUser(@RequestBody LoginRequestDto)
    |
    v
authenticationManager.authenticate(
    new UsernamePasswordAuthenticationToken(loginRequest.getEmail(), loginRequest.getPassword())
)
    |
    v
UserServiceImpl.loadUserByUsername(email)  // Spring calls this internally
    |
    v
JwtService.generateTokenLogin(authentication)  // sub = email
    |
    v
JwtResponseDto { id, token, refreshToken, email, username, name, roles }
```

## Related Code Files

### Files to Create
- `src/main/java/com/namnd/springjwt/dto/LoginRequestDto.java`

### Files to Modify
- `src/main/java/com/namnd/springjwt/controller/AuthController.java`
- `src/main/java/com/namnd/springjwt/dto/JwtResponseDto.java`

## Implementation Steps

### Step 1: Create `LoginRequestDto.java`

**File:** `src/main/java/com/namnd/springjwt/dto/LoginRequestDto.java`

```java
package com.namnd.springjwt.dto;

import lombok.Data;

@Data
public class LoginRequestDto {
    private String email;
    private String password;
}
```

Uses Lombok `@Data` for consistency with codebase style (User entity uses `@Data`).

### Step 2: Update `JwtResponseDto.java`

**File:** `src/main/java/com/namnd/springjwt/dto/JwtResponseDto.java`

1. Add `email` field:
   ```java
   private String email;
   ```

2. Update constructors to include email parameter:
   ```java
   public JwtResponseDto(Long id, String token, String refreshToken, String email,
                          String username, String name,
                          Collection<? extends GrantedAuthority> roles) {
       this.id = id;
       this.token = token;
       this.refreshToken = refreshToken;
       this.email = email;
       this.username = username;
       this.name = name;
       this.roles = roles;
   }
   ```

3. Add getter/setter for email:
   ```java
   public String getEmail() { return email; }
   public void setEmail(String email) { this.email = email; }
   ```

4. Remove the old 5-arg constructor (without refreshToken) or update it too -- check if it's used anywhere. Based on code review, only the 6-arg constructor is used in AuthController. Keep the old one but add email param for safety, or remove if unused.

### Step 3: Update `AuthController.java` -- Login

**File:** `src/main/java/com/namnd/springjwt/controller/AuthController.java`

1. Change login method signature from `@RequestBody User user` to `@RequestBody LoginRequestDto loginRequest`:
   ```java
   @PostMapping("/login")
   public ResponseEntity<?> authenticateUser(@RequestBody LoginRequestDto loginRequest) {
   ```

2. Update `UsernamePasswordAuthenticationToken` to use email:
   ```java
   Authentication authentication = authenticationManager.authenticate(
           new UsernamePasswordAuthenticationToken(
                   loginRequest.getEmail(),
                   loginRequest.getPassword()
           )
   );
   ```

3. Update user lookup to use `findByEmail()`:
   ```java
   User currentUser = userService.findByEmail(loginRequest.getEmail()).get();
   ```

4. Update `JwtResponseDto` construction to include email:
   ```java
   return ResponseEntity.ok(new JwtResponseDto(
           currentUser.getId(),
           jwt,
           refreshToken.getToken(),
           currentUser.getEmail(),
           currentUser.getUsername(),
           currentUser.getFullName(),
           userDetails.getAuthorities()));
   ```

### Step 4: Update `AuthController.java` -- Register

1. Remove username uniqueness check (lines 86-89):
   ```java
   // REMOVE this block:
   // if (userService.existsByUsername(registerDto.getUsername())) {
   //     return new ResponseEntity<>("Fail -> Username is already taken!",
   //             HttpStatus.BAD_REQUEST);
   // }
   ```

2. Keep email validation and uniqueness check (already exists at lines 92-99).

### Step 5: Update `AuthController.java` -- Logout

1. Change `getUserNameFromJwtToken()` to `getEmailFromJwtToken()`:
   ```java
   String email = jwtService.getEmailFromJwtToken(jwt);
   ```

2. Change `findByUserName()` to `findByEmail()`:
   ```java
   Optional<User> userOptional = userService.findByEmail(email);
   ```

### Step 6: Update `AuthController.java` -- Refresh Token

1. Change `generateTokenFromUsername()` to `generateTokenFromEmail()`:
   ```java
   String newAccessToken = jwtService.generateTokenFromEmail(user.getEmail());
   ```

### Step 7: Add `LoginRequestDto` import to AuthController

```java
import com.namnd.springjwt.dto.LoginRequestDto;
```
The wildcard import `import com.namnd.springjwt.dto.*;` already covers this, so no change needed.

## Todo List

- [x] Create `LoginRequestDto.java` with `email` + `password` fields
- [x] Add `email` field to `JwtResponseDto`, update constructors
- [x] Update login endpoint: accept `LoginRequestDto`, authenticate with email
- [x] Update login endpoint: construct `JwtResponseDto` with email
- [x] Remove username uniqueness check from register endpoint
- [x] Update logout: use `getEmailFromJwtToken()` + `findByEmail()`
- [x] Update refresh token: use `generateTokenFromEmail()` + `user.getEmail()`
- [x] Run `mvn clean compile` to verify
- [ ] **[Review finding — must fix before merge]** Add `@NotBlank @Email` to `LoginRequestDto` fields + `@Valid` on controller param
- [ ] **[Review finding — must fix before merge]** Replace unsafe `findByEmail(...).get()` with `.orElseThrow()` at `AuthController:71`
- [ ] **[Review finding]** Add Lombok `@Data` to `JwtResponseDto` to remove boilerplate

## Success Criteria

- `POST /api/auth/login` with `{"email":"user@example.com","password":"pass"}` returns JWT
- Login response contains `email` field
- Registration allows duplicate usernames
- Registration still rejects duplicate emails
- Logout works with email-based JWT
- Token refresh generates token with email as subject
- Project compiles with zero errors

## Risk Assessment

| Risk | Impact | Mitigation |
|------|--------|-----------|
| Clients expect `username` in login request | High (breaking API change) | Document clearly in README, version API if needed |
| JwtResponseDto constructor signature change | Low | Only used in AuthController |
| Missing import for LoginRequestDto | Low | Wildcard import already covers dto package |

## Security Considerations

- Login now requires email instead of username -- email is harder to guess than username for brute-force attacks (positive security impact)
- Email is returned in login response -- acceptable since the user just logged in with it

## Next Steps

- Phase 3: Update README and API documentation
