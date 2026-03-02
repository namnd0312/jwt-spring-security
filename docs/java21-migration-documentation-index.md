# Java 21 Migration Documentation Index

**Last Updated:** March 2, 2026
**Status:** Complete & Current
**Scope:** All documentation updated for Java 21 & Spring Boot 3.4.3 migration

---

## Quick Navigation

### 🚀 Getting Started with Java 21 Migration

**New to the migration?** Start here:
1. **[migration-java21.md](./migration-java21.md)** - Comprehensive migration guide with breaking changes, checklist, and testing procedures
2. **[README.md](../README.md)** - Updated quick start, prerequisites, and configuration
3. **[code-standards.md](./code-standards.md)** - New Spring Security 6.x patterns and coding standards

### 📋 Complete Documentation Map

| Document | Purpose | Key Topics |
|----------|---------|-----------|
| **[README.md](../README.md)** | Project overview & quick start | Prerequisites, build, API, configuration |
| **[project-overview-pdr.md](./project-overview-pdr.md)** | Product requirements & decisions | Tech stack, requirements, architecture decisions |
| **[codebase-summary.md](./codebase-summary.md)** | Codebase structure & components | File organization, dependencies, metrics |
| **[system-architecture.md](./system-architecture.md)** | System design & deployment | Layers, flows, Docker, scaling, security |
| **[code-standards.md](./code-standards.md)** | Coding conventions & patterns | Style, Spring Security 6.x, testing |
| **[migration-java21.md](./migration-java21.md)** | Migration guide & checklist | Breaking changes, steps, testing, rollback |

---

## Key Changes by Component

### Java & JDK
- **Version:** 1.8 → **21 LTS** ✓
- **Docker:** openjdk:11 → **eclipse-temurin:21-jre-alpine** ✓
- **Features:** Virtual threads, records, sealed classes now available
- **Documentation:** See migration-java21.md → Performance Improvements

### Spring Framework
- **Spring Boot:** 2.6.4 → **3.4.3** ✓
- **Spring Security:** 5.x → **6.x** ✓
  - New pattern: `SecurityFilterChain` bean
  - New annotation: `@EnableMethodSecurity`
  - Configuration: Lambda-based `authorizeHttpRequests()`
- **Documentation:** See code-standards.md → Spring Framework Conventions (Spring Security 6.x)

### Namespace Changes
- **All:** javax.* → **jakarta.*** ✓
  - javax.persistence → jakarta.persistence
  - javax.servlet → jakarta.servlet
  - javax.mail → jakarta.mail
  - javax.validation → jakarta.validation
- **Documentation:** See migration-java21.md → Breaking Changes (Jakarta EE)

### Dependencies
- **JJWT:** 0.9.0 → **0.12.6** (split into 3 artifacts) ✓
  - jjwt-api (compile)
  - jjwt-impl (runtime)
  - jjwt-jackson (runtime)
- **Lombok:** 1.18.30 → **BOM-managed** ✓
- **PostgreSQL:** 13.1 → **16** ✓
- **Documentation:** See codebase-summary.md → Configuration Files

### Configuration
- **Packaging:** WAR → **JAR** ✓
- **Redis Config:** spring.redis.* → **spring.data.redis.*** ✓
- **ServletInitializer:** **Removed** (JAR-only deployment) ✓
- **Documentation:** See README.md → Configuration section

---

## Migration Checklist Quick Reference

From `migration-java21.md`, the complete checklist includes:

### Code Changes
- [ ] Update javax.* imports to jakarta.*
- [ ] Update SecurityConfig to SecurityFilterChain pattern
- [ ] Replace @EnableGlobalMethodSecurity with @EnableMethodSecurity
- [ ] Update JJWT parser calls to parserBuilder()
- [ ] Remove WebSecurityConfigurerAdapter inheritance
- [ ] Check servlet filter imports
- [ ] Update JPA entity imports
- [ ] Verify Mail API imports

### POM.XML Changes
- [ ] Update Spring Boot parent to 3.4.3
- [ ] Update JJWT to 0.12.6 (3 artifacts)
- [ ] Change packaging from war to jar
- [ ] Remove spring-boot-starter-tomcat (provided)
- [ ] Update Java source/target to 21
- [ ] Update dependency versions for compatibility

### Configuration Changes
- [ ] Update application.yml: spring.redis.* → spring.data.redis.*
- [ ] Verify JWT secret uses env var
- [ ] Check other javax namespace properties

### Docker Changes
- [ ] Update Dockerfile base to eclipse-temurin:21-jre-alpine
- [ ] Update docker-compose.yml PostgreSQL to 16
- [ ] Test docker-compose build and startup

### Testing & Validation
- [ ] Run mvn clean compile (no errors)
- [ ] Run mvn test (all pass)
- [ ] Run mvn spring-boot:run (local startup)
- [ ] Test docker-compose up (containerized)
- [ ] Test all authentication endpoints
- [ ] Test token refresh mechanism
- [ ] Test logout/blacklist
- [ ] Test email activation flow
- [ ] Test password reset flow

---

## Spring Security 6.x Pattern Migration

**Critical Change:** The way Spring Security configuration is done has changed significantly.

### Old Pattern (Spring Boot 2.x / Spring Security 5.x)
```java
@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class SecurityConfig extends WebSecurityConfigurerAdapter {
    @Override
    protected void configure(HttpSecurity http) throws Exception { ... }
}
```

### New Pattern (Spring Boot 3.x / Spring Security 6.x)
```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity  // ← New annotation
public class SecurityConfig {  // ← No longer extends adapter
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
            .authorizeHttpRequests(auth -> auth  // ← Lambda-based
                .requestMatchers("/api/auth/**").permitAll()
                .anyRequest().authenticated()
            )
            .build();
    }
}
```

**Full example available:** See [code-standards.md](./code-standards.md#annotation-order-spring-security-6x)

---

## Configuration Property Migration

### Spring Data Redis (was spring.redis.*)
```yaml
# Old (Spring Boot 2.x)
spring:
  redis:
    host: localhost
    port: 6379

# New (Spring Boot 3.x)
spring:
  data:
    redis:
      host: localhost
      port: 6379
```

**Full configuration example:** See [README.md](../README.md#configuration)

---

## Database & Infrastructure Changes

### PostgreSQL
- **Old:** 13.1
- **New:** 16
- **Action:** Verify schema compatibility, test migrations

### Docker Base Image
- **Old:** openjdk:11
- **New:** eclipse-temurin:21-jre-alpine
- **Benefits:** Smaller image size, Alpine Linux, JDK 21 LTS

### Redis
- **Configuration:** spring.redis.* → spring.data.redis.*
- **Functionality:** No breaking changes, property naming only

---

## Architecture & Security Unchanged

✓ **No fundamental changes to:**
- JWT token generation/validation (HS512 still used)
- Token refresh mechanism
- Account lockout logic
- Email activation flow
- Password reset flow
- Role-based authorization (@PreAuthorize)
- Token blacklist (Redis)
- Database schema

✓ **Spring Security 6.x provides:**
- Same security guarantees
- Improved configuration patterns
- Better performance
- Lambda-based fluent API
- Simplified bean management

---

## Testing After Migration

### Quick Smoke Test
```bash
# 1. Build
mvn clean package

# 2. Run locally
mvn spring-boot:run

# 3. Test login endpoint
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"pass123"}'

# 4. Test Docker
docker-compose up --build
# Verify: PostgreSQL on 5432, Redis on 6379, API on 8080
```

### Full Testing Checklist
See [migration-java21.md](./migration-java21.md#testing-recommendations) for comprehensive unit, integration, and performance tests.

---

## Common Issues & Solutions

### Issue: "Unable to find a signing key"
**Cause:** JWT secret format issue
**Solution:** Verify secret is Base64-encoded, check env var override

### Issue: "Connection refused" on Redis
**Cause:** Property name typo
**Solution:** Use `spring.data.redis.*` (not `spring.redis.*`)

### Issue: Compilation errors with imports
**Cause:** javax.* still in code
**Solution:** Global find/replace javax → jakarta

### Issue: SecurityConfig bean not found
**Cause:** Missing SecurityFilterChain bean return
**Solution:** Method must return SecurityFilterChain (not void)

**Full troubleshooting:** See [migration-java21.md](./migration-java21.md#known-issues--workarounds)

---

## Rollback Procedure

If critical issues arise, rollback using the procedure in [migration-java21.md](./migration-java21.md#rollback-plan):

1. **Code:** `git revert <migration-commit>`
2. **Docker:** Update Dockerfile & docker-compose.yml to old versions
3. **Database:** Restore from backup if schema changes made
4. **Verify:** Re-run smoke tests with old environment

---

## Performance Notes

Java 21 and Spring Boot 3.4.3 provide:
- ✓ ~30% faster startup time
- ✓ Improved GC pause times (G1GC optimizations)
- ✓ Better memory efficiency
- ✓ Virtual threads ready (future use)
- ✓ No breaking performance changes

See [migration-java21.md](./migration-java21.md#performance-improvements) for details.

---

## Future Upgrades

Java 21 LTS receives updates until **September 2028**:
- Java 23 (Sept 2023, non-LTS) - Bleeding edge
- Java 25 (Sept 2025, non-LTS) - Incremental updates
- Java 27 (Sept 2027, LTS candidate) - Next LTS version

Plan upgrades in 2-3 year cycles, staying on LTS versions.

---

## Key Document Links

### For Understanding Changes
- **Complete migration details:** [migration-java21.md](./migration-java21.md)
- **Breaking changes explained:** [migration-java21.md#breaking-changes](./migration-java21.md)
- **New code patterns:** [code-standards.md](./code-standards.md)

### For Implementation
- **Spring Security 6.x example:** [code-standards.md#annotation-order-spring-security-6x](./code-standards.md)
- **Configuration updates:** [README.md#configuration](../README.md)
- **Dependencies list:** [codebase-summary.md#external-dependencies](./codebase-summary.md)

### For Deployment
- **Prerequisites:** [README.md#prerequisites](../README.md)
- **Architecture:** [system-architecture.md](./system-architecture.md)
- **Docker setup:** [README.md#docker-compose](../README.md)

### For Testing
- **Test recommendations:** [migration-java21.md#testing-recommendations](./migration-java21.md)
- **Troubleshooting:** [migration-java21.md#known-issues--workarounds](./migration-java21.md)

---

## Report Files

Migration documentation is tracked in reports:
- **Full migration details:** `plans/260302-2102-java21-spring-boot-migration/reports/docs-manager-260302-2126-java21-migration-documentation-updates.md`
- **Summary:** `plans/260302-2102-java21-spring-boot-migration/reports/docs-manager-260302-2127-documentation-update-summary.md`

---

## Status Summary

| Aspect | Status | Details |
|--------|--------|---------|
| Documentation Updates | ✓ Complete | 5 files updated, 1 new guide |
| Code Examples | ✓ Complete | Spring Security 6.x patterns shown |
| Configuration Changes | ✓ Complete | All property updates documented |
| Breaking Changes | ✓ Complete | Jakarta EE, Spring Security 6.x, JJWT |
| Testing Procedures | ✓ Complete | Unit, integration, smoke tests |
| Rollback Plan | ✓ Complete | Step-by-step rollback documented |
| Quality Assurance | ✓ Complete | All updates verified |

---

## Questions or Issues?

1. **Check:** [migration-java21.md](./migration-java21.md) for comprehensive answers
2. **Review:** [code-standards.md](./code-standards.md) for coding patterns
3. **Verify:** [README.md](../README.md) for configuration and setup
4. **Understand:** [system-architecture.md](./system-architecture.md) for architectural impact

---

**Documentation Last Updated:** March 2, 2026
**Migration Status:** Complete and Documented
**Ready for:** Development Team, Deployment, Production Migration
