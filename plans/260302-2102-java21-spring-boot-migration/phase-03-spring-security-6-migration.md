# Phase 3: Spring Security 6.x Migration

## Context Links
- [Research: Spring Boot Migration](./research/researcher-01-spring-boot-2.6-to-3.4-java21-jakarta-security6-jjwt-migration.md)
- [Plan Overview](./plan.md)
- Current file: `src/main/java/com/namnd/springjwt/config/security/SecurityConfig.java` (82 lines)

## Overview
- **Priority:** P1 (SecurityConfig will not compile with Boot 3.x)
- **Status:** pending
- **Effort:** 1h
- **Description:** Full rewrite of SecurityConfig.java from WebSecurityConfigurerAdapter pattern to SecurityFilterChain bean pattern required by Spring Security 6.x

## Key Insights
- `WebSecurityConfigurerAdapter` completely removed in Spring Security 6 — no deprecation bridge
- `@EnableGlobalMethodSecurity` replaced by `@EnableMethodSecurity` (prePostEnabled=true is default)
- `authorizeRequests()` -> `authorizeHttpRequests()`; `antMatchers()` -> `requestMatchers()`
- Lambda DSL mandatory; non-lambda form deprecated
- `AuthenticationManager` obtained via `AuthenticationConfiguration.getAuthenticationManager()` instead of `super.authenticationManagerBean()`
- `UserService` is wired into `AuthenticationManager` via `DaoAuthenticationProvider` bean or `AuthenticationConfiguration` auto-detection
- Current public endpoints: `/api/auth/**` — must preserve

## Requirements
- SecurityFilterChain bean replaces `configure(HttpSecurity)`
- AuthenticationManager bean replaces `authenticationManagerBean()` and `configure(AuthenticationManagerBuilder)`
- PasswordEncoder bean preserved (BCrypt)
- JwtAuthenticationFilter bean preserved
- CustomAccesDeniedHandler bean preserved
- Session policy: STATELESS
- CSRF: disabled
- CORS: enabled with defaults
- Public: `/api/auth/**`; all other requests: authenticated

## Architecture
Same security architecture — stateless JWT, filter-before chain. Only the configuration API changes.

## Related Code Files
| File | Action |
|------|--------|
| `src/main/java/com/namnd/springjwt/config/security/SecurityConfig.java` | **Full rewrite** |

## Implementation Steps

### 1. Replace SecurityConfig.java entirely

**Current code (82 lines, OLD — delete entirely):**
```java
@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class SecurityConfig extends WebSecurityConfigurerAdapter {
    @Autowired
    private UserService userService;

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter();
    }

    @Override
    public void configure(AuthenticationManagerBuilder authenticationManagerBuilder) throws Exception {
        authenticationManagerBuilder
                .userDetailsService(userService)
                .passwordEncoder(passwordEncoder());
    }

    @Bean
    @Override
    public AuthenticationManager authenticationManagerBean() throws Exception {
        return super.authenticationManagerBean();
    }

    @Bean
    public CustomAccesDeniedHandler customAccesDeniedHandler() {
        return new CustomAccesDeniedHandler();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.authorizeRequests()
                .antMatchers("/api/auth/**").permitAll()
                .anyRequest().authenticated()
                .and().csrf().disable();
        http.addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling().accessDeniedHandler(customAccesDeniedHandler());
        http.sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS);
        http.cors();
    }
    // ... commented-out code block ...
}
```

**New code (replace entire file):**
```java
package com.namnd.springjwt.config.security;

import com.namnd.springjwt.config.custom.CustomAccesDeniedHandler;
import com.namnd.springjwt.config.filter.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter();
    }

    @Bean
    public CustomAccesDeniedHandler customAccesDeniedHandler() {
        return new CustomAccesDeniedHandler();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(Customizer.withDefaults())
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()
                .anyRequest().authenticated())
            .exceptionHandling(ex ->
                ex.accessDeniedHandler(customAccesDeniedHandler()))
            .addFilterBefore(jwtAuthenticationFilter(),
                UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
```

### Key changes explained

| Old (SS5) | New (SS6) | Why |
|-----------|-----------|-----|
| `extends WebSecurityConfigurerAdapter` | removed, plain `@Configuration` class | Adapter class deleted in SS6 |
| `@EnableGlobalMethodSecurity(prePostEnabled = true)` | `@EnableMethodSecurity` | prePostEnabled=true is default in `@EnableMethodSecurity` |
| `configure(AuthenticationManagerBuilder)` with `userDetailsService()` | `AuthenticationManager` bean via `AuthenticationConfiguration` | SS6 auto-detects `UserDetailsService` + `PasswordEncoder` beans |
| `authenticationManagerBean()` override | `authenticationConfiguration.getAuthenticationManager()` | No super class to override |
| `configure(HttpSecurity)` | `@Bean SecurityFilterChain filterChain(HttpSecurity)` | Component-based config |
| `authorizeRequests()` | `authorizeHttpRequests()` | Renamed in SS6 |
| `.antMatchers()` | `.requestMatchers()` | Renamed in SS6 |
| `http.cors()` (no arg) | `http.cors(Customizer.withDefaults())` | Lambda DSL required |
| `http.csrf().disable()` | `http.csrf(csrf -> csrf.disable())` | Lambda DSL required |
| Chained `.and()` calls | Separate lambda blocks | Cleaner API, `.and()` deprecated |

### 2. Verify UserService auto-detection

`AuthenticationConfiguration` auto-detects `UserDetailsService` beans. `UserService` implements `UserDetailsService` (via `UserServiceImpl.loadUserByUsername()`). Combined with `PasswordEncoder` bean, Spring auto-configures `DaoAuthenticationProvider`. No explicit wiring needed.

**No changes needed** in `UserServiceImpl.java` — it already implements `UserDetailsService`.

## Todo List
- [ ] Replace entire SecurityConfig.java with new code
- [ ] Remove all commented-out code blocks
- [ ] Verify imports resolve (no WebSecurityConfigurerAdapter, no EnableGlobalMethodSecurity)
- [ ] Confirm `/api/auth/**` endpoints remain public
- [ ] Confirm all other endpoints require authentication

## Success Criteria
- SecurityConfig.java compiles without errors
- No reference to `WebSecurityConfigurerAdapter` anywhere in codebase
- No reference to `@EnableGlobalMethodSecurity` anywhere
- `AuthenticationManager` is injectable in `AuthController`

## Risk Assessment
- **Medium risk**: most impactful single-file change; any mistake breaks all auth
- `@EnableMethodSecurity` defaults: `prePostEnabled=true`, `securedEnabled=false`, `jsr250Enabled=false` — matches current behavior since only `@PreAuthorize` used in codebase
- `requestMatchers("/api/auth/**")` pattern matching is identical to `antMatchers("/api/auth/**")` for simple paths

## Security Considerations
- CSRF disabled: correct for stateless JWT API (no session cookies)
- CORS with defaults: uses `CorsConfigurationSource` bean if defined, else allows all — same as before
- Session STATELESS: prevents session fixation attacks

## Next Steps
Proceed to Phase 4 (JJWT 0.12.6 migration).
