---
title: "Java 21 & Spring Boot 3.4.x Migration"
description: "Migrate jwt-spring-security from Java 8 / Spring Boot 2.6.4 to Java 21 / Spring Boot 3.4.3"
status: completed
priority: P1
effort: 5.5h
branch: master
tags: [migration, java21, spring-boot-3, spring-security-6, jjwt, backend, infra]
created: 2026-03-02
---

# Java 21 & Spring Boot 3.4.x Migration Plan

## Summary

Migrate project from Java 8 + Spring Boot 2.6.4 + JJWT 0.9.0 to Java 21 + Spring Boot 3.4.3 + JJWT 0.12.6. Covers namespace migration (javax->jakarta), Spring Security 6 rewrite, JJWT API migration, config property updates, and Docker image upgrades.

## Phases

| # | Phase | Effort | Status | File |
|---|-------|--------|--------|------|
| 1 | [Build Config & Dependencies](./phase-01-build-config-and-dependencies.md) | 1h | pending | pom.xml |
| 2 | [javax to jakarta Namespace](./phase-02-javax-to-jakarta-namespace.md) | 30min | pending | 12 files |
| 3 | [Spring Security 6.x Migration](./phase-03-spring-security-6-migration.md) | 1h | pending | SecurityConfig.java |
| 4 | [JJWT 0.12.6 Migration](./phase-04-jjwt-migration.md) | 1h | pending | JwtService.java, application.yml |
| 5 | [Application Config & Properties](./phase-05-application-config-and-properties.md) | 30min | pending | application.yml, RedisConfig.java |
| 6 | [Docker & Infrastructure](./phase-06-docker-and-infrastructure.md) | 30min | pending | Dockerfile, docker-compose.yml |
| 7 | [Compile, Test & Verify](./phase-07-compile-test-and-verify.md) | 1h | pending | all |

## Execution Order

Phases MUST be executed in order 1-7. Phase 1 enables IDE resolution for all subsequent phases. Phase 7 validates everything.

## Key Dependencies

- Java 21 JDK installed locally
- Maven 3.9+ (for Boot 3.x)
- Docker Desktop updated (for new images)

## Validated Decisions

- **Packaging**: WAR → JAR (remove ServletInitializer, embedded Tomcat only)
- **JWT Secret**: Auto-generate new Base64-encoded 64-byte key (invalidates existing tokens — acceptable)
- **Hibernate Dialect**: Remove `spring.datasource.platform`, auto-detect from JDBC URL
- **JDK**: Java 21 confirmed installed locally

## Risk Summary

- **Token invalidation**: New HS512 key invalidates all existing JWTs (acceptable for dev)
- **Hibernate 6**: Stricter column naming; `ddl-auto: update` may generate different DDL

## Research Reports

- [Spring Boot 2.6->3.4 Migration](./research/researcher-01-spring-boot-2.6-to-3.4-java21-jakarta-security6-jjwt-migration.md)
- [JJWT & Docker Migration](./research/researcher-02-jjwt-docker-migration.md)
