# Phase 1: Core Auth Refactor

## Context Links
- [Plan Overview](./plan.md)
- [System Architecture](../../docs/system-architecture.md)
- [Code Standards](../../docs/code-standards.md)

## Overview
- **Priority:** P1 (must complete first)
- **Status:** complete
- **Description:** Refactor the core Spring Security auth chain to use email instead of username as the principal identifier

## Key Insights
- `UserDetailsService.loadUserByUsername(String)` is a Spring interface method -- its parameter name says "username" but we pass email. This is standard Spring Security practice (many apps pass email via this method)
- JWT `sub` claim currently holds username; must change to email
- `UserPrinciple.getUsername()` returns what Spring Security treats as the principal; must return email
- `UserRepository.findByEmail()` already exists -- no new repo method needed
- `User.username` field has no `@Column(unique = true)` annotation, so DB already allows duplicates
- The filter chain (`JwtAuthenticationFilter`) calls `getUserNameFromJwtToken()` then `loadUserByUsername()` -- both will work transparently once internals change

## Requirements

### Functional
- `loadUserByUsername()` looks up user by email in DB
- `UserPrinciple.getUsername()` returns email (Spring Security contract)
- JWT subject claim (`sub`) contains email
- Token generation methods use email as subject

### Non-Functional
- Zero downtime deployment not required (auth refactor invalidates existing tokens)
- Backward-compatible method signatures where possible

## Architecture

```
Login Request (email+pass)
    |
    v
AuthenticationManager.authenticate(email, pass)
    |
    v
UserServiceImpl.loadUserByUsername(email)  // Spring interface, param name is misleading
    |
    v
UserRepository.findByEmail(email)
    |
    v
UserPrinciple.build(user)  // getUsername() returns user.getEmail()
    |
    v
JwtService.generateTokenLogin(auth)
    // sub = userPrinciple.getUsername() = email
```

## Related Code Files

### Files to Modify

1. **`src/main/java/com/namnd/springjwt/model/UserPrinciple.java`**
2. **`src/main/java/com/namnd/springjwt/service/impl/UserServiceImpl.java`**
3. **`src/main/java/com/namnd/springjwt/service/UserService.java`**
4. **`src/main/java/com/namnd/springjwt/service/JwtService.java`**

### Files Unchanged (but verify)
- `JwtAuthenticationFilter.java` -- calls `getUserNameFromJwtToken()` then `loadUserByUsername()`. Both methods internally switch to email but signatures stay the same. **No code change needed.**

## Implementation Steps

### Step 1: Update `UserPrinciple.java`

**File:** `src/main/java/com/namnd/springjwt/model/UserPrinciple.java`

1. Add `email` field to the class:
   ```java
   private String email;
   ```

2. Update constructor to accept email:
   ```java
   public UserPrinciple(Long id, String username, String email, String password,
                         Collection<? extends GrantedAuthority> roles) {
       this.id = id;
       this.username = username;
       this.email = email;
       this.password = password;
       this.roles = roles;
   }
   ```

3. Update `build()` method to pass email:
   ```java
   public static UserPrinciple build(User user) {
       List<GrantedAuthority> authorities = user.getRoles().stream()
               .map(role -> new SimpleGrantedAuthority(role.getName()))
               .collect(Collectors.toList());

       return new UserPrinciple(user.getId(),
               user.getUsername(),
               user.getEmail(),
               user.getPassword(),
               authorities);
   }
   ```

4. **Change `getUsername()` to return `email`** -- this is the key change. Spring Security uses `getUsername()` as the principal identity:
   ```java
   @Override
   public String getUsername() {
       return email;  // Spring Security principal = email
   }
   ```

5. Add getter for the actual username (display name):
   ```java
   public String getDisplayName() {
       return username;
   }
   ```

6. Add getter for email:
   ```java
   public String getEmail() {
       return email;
   }
   ```

### Step 2: Update `UserServiceImpl.java`

**File:** `src/main/java/com/namnd/springjwt/service/impl/UserServiceImpl.java`

1. Change `loadUserByUsername()` to query by email instead of username:
   ```java
   @Override
   public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
       Optional<User> user = userRepository.findByEmail(email);

       if (!user.isPresent()) {
           throw new UsernameNotFoundException("User not found with email: " + email);
       }

       return UserPrinciple.build(user.get());
   }
   ```

### Step 3: Update `UserService.java` interface

**File:** `src/main/java/com/namnd/springjwt/service/UserService.java`

1. Remove `existsByUsername()` method (no longer needed for login uniqueness check). **But keep it if registration still wants to check.** Actually, since the requirement says "allow duplicate usernames", we should remove the method entirely:
   - Remove: `Boolean existsByUsername(String userName);`
   - Remove: `Optional<User> findByUserName(String userName);`
   - Keep: `findByEmail()`, `existsByEmail()`

2. **Wait -- `findByUserName()` is used in logout flow** (line 183 of AuthController). The logout flow extracts username from JWT to find the user. After refactor, JWT `sub` = email, so `getUserNameFromJwtToken()` returns email. Logout should then use `findByEmail()`. So we can safely remove `findByUserName()`.

3. Updated interface:
   ```java
   public interface UserService extends UserDetailsService {
       void save(User user);
       Optional<User> findByEmail(String email);
       Boolean existsByEmail(String email);
   }
   ```

### Step 4: Update `UserServiceImpl.java` (remove old methods)

1. Remove `findByUserName()` method
2. Remove `existsByUsername()` method
3. Keep `findByEmail()`, `existsByEmail()`, `save()`, `loadUserByUsername()`

### Step 5: Update `JwtService.java`

**File:** `src/main/java/com/namnd/springjwt/service/JwtService.java`

1. Rename `generateTokenFromUsername()` to `generateTokenFromEmail()` for clarity:
   ```java
   public String generateTokenFromEmail(String email) {
       return Jwts.builder()
               .setSubject(email)
               .setId(UUID.randomUUID().toString())
               .setIssuedAt(new Date())
               .setExpiration(new Date(System.currentTimeMillis() + EXPIRE_TIME))
               .signWith(SignatureAlgorithm.HS512, SECRET_KEY)
               .compact();
   }
   ```

2. Rename `getUserNameFromJwtToken()` to `getEmailFromJwtToken()` for clarity:
   ```java
   public String getEmailFromJwtToken(String token) {
       return Jwts.parser()
               .setSigningKey(SECRET_KEY)
               .parseClaimsJws(token)
               .getBody().getSubject();
   }
   ```

3. `generateTokenLogin()` -- **no code change needed**. It already calls `userPrinciple.getUsername()` which now returns email.

### Step 6: Update `JwtAuthenticationFilter.java`

**File:** `src/main/java/com/namnd/springjwt/config/filter/JwtAuthenticationFilter.java`

1. Update method call from `getUserNameFromJwtToken()` to `getEmailFromJwtToken()`:
   ```java
   String email = jwtService.getEmailFromJwtToken(jwt);
   UserDetails userDetails = userService.loadUserByUsername(email);
   ```

2. Update variable name from `username` to `email` for clarity.

## Todo List

- [x] Add `email` field to `UserPrinciple`, update constructor and `build()`
- [x] Change `UserPrinciple.getUsername()` to return email
- [x] Add `getDisplayName()` and `getEmail()` getters to `UserPrinciple`
- [x] Change `loadUserByUsername()` in `UserServiceImpl` to query by email
- [x] Remove `findByUserName()` and `existsByUsername()` from `UserService` interface
- [x] Remove `findByUserName()` and `existsByUsername()` from `UserServiceImpl`
- [x] Rename `generateTokenFromUsername()` to `generateTokenFromEmail()` in `JwtService`
- [x] Rename `getUserNameFromJwtToken()` to `getEmailFromJwtToken()` in `JwtService`
- [x] Update `JwtAuthenticationFilter` to use `getEmailFromJwtToken()`
- [x] Run `mvn clean compile` to verify no compilation errors
- [ ] **[Review finding]** Add `hashCode()` to `UserPrinciple` (violates equals/hashCode contract)
- [ ] **[Review finding]** Remove dead `findByUsername`/`existsByUsername` from `UserRepository`
- [ ] **[Review finding]** Extract duplicate `getJwtFromRequest()` to shared utility in `JwtService`

## Success Criteria

- `loadUserByUsername("user@example.com")` returns correct UserPrinciple
- JWT `sub` claim contains email address
- `UserPrinciple.getUsername()` returns email
- `JwtAuthenticationFilter` resolves user from email in JWT
- Project compiles with zero errors

## Risk Assessment

| Risk | Impact | Mitigation |
|------|--------|-----------|
| Existing JWT tokens become invalid | Medium | Expected; users must re-login after deploy |
| `loadUserByUsername` param name confusion | Low | Add comment explaining Spring Security convention |
| Removing `findByUserName` breaks other callers | Medium | Grep for all usages before removing (done: only AuthController uses it) |

## Security Considerations

- Email is PII -- JWT `sub` now contains email. Since JWTs are Base64 (not encrypted), email is visible to anyone with the token. This is acceptable for Bearer-token APIs where the client already knows the user's email.
- Email uniqueness constraint remains enforced at DB level (`@Column(unique = true)`)

## Next Steps

- Phase 2: Update AuthController to use `LoginRequestDto` and call updated service methods
