# Research: Spring Boot 2.6.4 → 3.4.x Migration (Java 21)

**Date:** 2026-03-02
**Scope:** JWT Spring Security project migration

---

## 1. Target Version

- **Spring Boot 3.4.13** — latest stable in 3.4.x line (released 2025-12-18, end of OSS support for 3.4.x)
- Spring Boot 3.4.x requires **Java 17 minimum**; Java 21 is fully supported (LTS)
- Bundled Spring Security **6.4.x**, Spring Data JPA **3.4.x**

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.4.3</version>  <!-- latest patch at time of plan writing -->
</parent>
<properties>
    <java.version>21</java.version>
</properties>
```

---

## 2. javax → jakarta Namespace Migration

Full rename required — ALL `javax.*` imports from Java EE must become `jakarta.*`.

| Old (`javax.*`)              | New (`jakarta.*`)              | Files affected in project          |
|------------------------------|--------------------------------|------------------------------------|
| `javax.persistence.*`        | `jakarta.persistence.*`        | All `@Entity` models               |
| `javax.validation.*`         | `jakarta.validation.*`         | DTOs with `@Valid`, `@NotBlank`    |
| `javax.servlet.*`            | `jakarta.servlet.*`            | `JwtAuthenticationFilter`, `CookieUtils` |
| `javax.annotation.*`         | `jakarta.annotation.*`         | Any `@PostConstruct` usage         |
| `javax.transaction.*`        | `jakarta.transaction.*`        | If used directly                   |

**NOT renamed** (stay as `javax.*`): `javax.sql.*`, `javax.crypto.*` — these are JDK, not Jakarta EE.

Affected files in this project:
- `JwtAuthenticationFilter.java` — `javax.servlet.{FilterChain,ServletException,http.*}`
- `CookieUtils.java` — `javax.servlet.http.{Cookie,HttpServletRequest,HttpServletResponse}`
- `User.java`, `Role.java`, `PasswordResetToken.java`, `RefreshToken.java`, `ActivationToken.java` — `javax.persistence.*`
- All DTOs using `@Valid` — `javax.validation.*`

---

## 3. Spring Security 5 → 6 Breaking Changes

### 3.1 WebSecurityConfigurerAdapter — REMOVED

`WebSecurityConfigurerAdapter` is completely removed in Spring Security 6. Must migrate to component-based config.

**Current code** (`SecurityConfig.java`):
```java
// BROKEN in Spring Security 6
@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class SecurityConfig extends WebSecurityConfigurerAdapter { ... }
```

**New pattern** (Spring Security 6):
```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity  // replaces @EnableGlobalMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth  // replaces authorizeRequests()
                .requestMatchers("/api/auth/**").permitAll()  // replaces antMatchers()
                .anyRequest().authenticated()
            )
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(ex -> ex.accessDeniedHandler(customAccessDeniedHandler()))
            .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class)
            .cors(Customizer.withDefaults());
        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
```

### 3.2 Key API Renames

| Removed (SS5)                   | Replacement (SS6)              |
|---------------------------------|-------------------------------|
| `authorizeRequests()`           | `authorizeHttpRequests()`     |
| `antMatchers()`                 | `requestMatchers()`           |
| `@EnableGlobalMethodSecurity`   | `@EnableMethodSecurity`       |
| `WebSecurityConfigurerAdapter`  | `SecurityFilterChain` bean    |
| `configure(AuthenticationManagerBuilder)` | `AuthenticationManager` bean via `AuthenticationConfiguration` |

### 3.3 Default Security Changes

- CSRF protection still enabled by default — explicit `.csrf(csrf -> csrf.disable())` required for stateless APIs
- `HttpSecurity` methods now use lambda DSL (non-lambda form deprecated)

---

## 4. Spring Boot 3.x Property Changes

Key renames for this project's likely `application.yml`:

| Old property (Boot 2.x)         | New property (Boot 3.x)         |
|---------------------------------|--------------------------------|
| `spring.redis.*`                | `spring.data.redis.*`          |
| `spring.jpa.hibernate.ddl-auto` | unchanged                      |
| `spring.profiles`               | `spring.config.activate.on-profile` |

**Add `spring-boot-properties-migrator` temporarily** to auto-detect renamed props:
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-properties-migrator</artifactId>
    <scope>runtime</scope>
</dependency>
```
Remove after migration is verified.

Redis property rename is critical — current code uses `spring.data.redis` in Boot 3.x.

---

## 5. Maven Build Changes

### 5.1 pom.xml Updates

```xml
<!-- Java version -->
<properties>
    <java.version>21</java.version>
</properties>
```

No structural changes to `spring-boot-maven-plugin` config needed — existing Lombok exclusion pattern is still valid. WAR packaging config unchanged.

### 5.2 WAR + SpringBootServletInitializer

`ServletInitializer.java` extends `SpringBootServletInitializer` — still valid in Boot 3.x, no change needed structurally, but it imports `javax.servlet` internally via Spring, which is handled by the Jakarta migration.

---

## 6. JJWT Migration (0.9.0 → 0.12.x)

**Critical:** JJWT 0.9.0 single-jar artifact is legacy. Must split into 3 artifacts AND update API.

### 6.1 Dependency Change

```xml
<!-- Remove old -->
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt</artifactId>
    <version>0.9.0</version>
</dependency>

<!-- Add new -->
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.6</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
```

### 6.2 JwtService.java API Changes

```java
// OLD (0.9.0) — BROKEN in 0.12.x
Jwts.builder()
    .setSubject(email)
    .setId(UUID.randomUUID().toString())
    .setIssuedAt(new Date())
    .setExpiration(new Date(...))
    .signWith(SignatureAlgorithm.HS512, SECRET_KEY)
    .compact();

Jwts.parser().setSigningKey(SECRET_KEY).parseClaimsJws(token).getBody().getSubject();

// NEW (0.12.x)
SecretKey key = Keys.hmacShaKeyFor(SECRET_KEY.getBytes(StandardCharsets.UTF_8));

Jwts.builder()
    .subject(email)
    .id(UUID.randomUUID().toString())
    .issuedAt(new Date())
    .expiration(new Date(...))
    .signWith(key, Jwts.SIG.HS512)
    .compact();

Jwts.parser()
    .verifyWith(key)
    .build()
    .parseSignedClaims(token)
    .getPayload()
    .getSubject();
```

Key change: `SECRET_KEY` must be Base64-encoded or long enough for HS512 (≥64 bytes raw).

---

## 7. Spring Data JPA Changes

- No repository interface changes (`JpaRepository`, `CrudRepository` API unchanged)
- Entity annotations move to `jakarta.persistence.*` (covered in §2)
- Hibernate 6 (bundled with Boot 3.x) has stricter naming strategy — verify `spring.jpa.hibernate.naming.physical-strategy` if customized
- `@Table`, `@Column` behavior unchanged

---

## Summary of Files to Change

| File | Change |
|------|--------|
| `pom.xml` | Boot 3.4.x, Java 21, JJWT split deps |
| `SecurityConfig.java` | Full rewrite to `SecurityFilterChain` pattern |
| `JwtService.java` | JJWT 0.12.x API migration |
| `JwtAuthenticationFilter.java` | `javax.servlet` → `jakarta.servlet` |
| `CookieUtils.java` | `javax.servlet` → `jakarta.servlet` |
| All `@Entity` models | `javax.persistence` → `jakarta.persistence` |
| All DTOs | `javax.validation` → `jakarta.validation` |
| `application.yml` | `spring.redis.*` → `spring.data.redis.*` |

---

## Unresolved Questions

1. Current `application.yml` not reviewed — need to verify actual Redis property keys and any other deprecated props in use.
2. JJWT `SECRET_KEY` current value/length unknown — HS512 requires ≥64-byte key; if current secret is short, it will fail at runtime with 0.12.x (will throw `WeakKeyException`).
3. Any custom `@PreAuthorize`/`@PostAuthorize` annotations — verify `@EnableMethodSecurity` covers the same semantics as `@EnableGlobalMethodSecurity(prePostEnabled = true)` (it does by default, but confirm no `securedEnabled` or `jsr250Enabled` usage).
4. WAR deployment target (Tomcat version) — Boot 3.4.x requires Tomcat 10.x (Jakarta Servlet 6.0). If deploying to external Tomcat, it must be Tomcat 10+.
