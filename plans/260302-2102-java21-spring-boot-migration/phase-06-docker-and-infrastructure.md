# Phase 6: Docker & Infrastructure

## Context Links
- [Research: JJWT & Docker](./research/researcher-02-jjwt-docker-migration.md)
- [Plan Overview](./plan.md)
- Current files: `Dockerfile`, `docker-compose.yml`

## Overview
- **Priority:** P2 (not blocking compilation, but needed for deployment)
- **Status:** pending
- **Effort:** 30min
- **Description:** Update Docker base image to Java 21, upgrade PostgreSQL image, fix volumes, remove deprecated compose version key

## Key Insights
- `openjdk:11` is deprecated on Docker Hub — `eclipse-temurin` is official successor (Adoptium)
- `eclipse-temurin:21-jre-alpine` is smallest production image (~80MB)
- `postgres:13.1-alpine` -> `postgres:16-alpine` for latest LTS; no JDBC breaking changes
- Current volume uses absolute host path (`/Users/admin/...`) — non-portable; should use named volume
- `version: "3.7"` in docker-compose is deprecated in Compose V2 — remove it
- Redis image `redis:7-alpine` is already good — no change needed

## Requirements
- Dockerfile uses `eclipse-temurin:21-jre-alpine`
- docker-compose uses `postgres:16-alpine`
- Volume uses named Docker volume (portable)
- Remove `version:` key from docker-compose.yml
- Application environment variables passed for Redis host override

## Architecture
Same container architecture: 3 services (postgres, redis, app) on bridged network. Only image versions and volume config change.

## Related Code Files
| File | Action |
|------|--------|
| `Dockerfile` | Modify (base image) |
| `docker-compose.yml` | Modify (postgres version, volume, remove version key, add env vars) |

## Implementation Steps

### 1. Replace Dockerfile

**Current (OLD):**
```dockerfile
FROM openjdk:11
ARG JAR_FILE=target/spring-jwt.jar
WORKDIR /opt/app
COPY ${JAR_FILE} /opt/app/spring-jwt.jar
ENTRYPOINT ["java","-jar","spring-jwt.jar"]
```

**New:**
```dockerfile
FROM eclipse-temurin:21-jre-alpine

ARG JAR_FILE=target/spring-jwt.jar

WORKDIR /opt/app

COPY ${JAR_FILE} /opt/app/spring-jwt.jar

ENTRYPOINT ["java", "-jar", "spring-jwt.jar"]
```

Changes:
- `FROM openjdk:11` -> `FROM eclipse-temurin:21-jre-alpine`
- Removed old comments about Java 8
- Kept same WORKDIR, JAR path, ENTRYPOINT

### 2. Replace docker-compose.yml

**Current (OLD):**
```yaml
version: "3.7"
services:
  postgres-service:
    image: 'postgres:13.1-alpine'
    ...
    volumes:
      - /Users/admin/Desktop/DEV/DOCKER/docker-volumes:/var/lib/docker/volumes/postgres/_data
    ...
```

**New:**
```yaml
services:
  postgres-service:
    image: 'postgres:16-alpine'
    ports:
      - "5432:5432"
    volumes:
      - postgres-data:/var/lib/postgresql/data
    networks:
      - my-net
    restart: unless-stopped
    environment:
      - POSTGRES_USER=postgres
      - POSTGRES_PASSWORD=postgres
  redis-service:
    image: 'redis:7-alpine'
    ports:
      - "6379:6379"
    networks:
      - my-net
    restart: unless-stopped
  ms-authentication-service:
    build:
      context: ./
      dockerfile: Dockerfile
    ports:
      - "8080:8080"
    networks:
      - my-net
    depends_on:
      - postgres-service
      - redis-service
    environment:
      - SPRING_DATASOURCE_URL=jdbc:postgresql://postgres-service:5432/testdb
      - SPRING_DATASOURCE_USERNAME=postgres
      - SPRING_DATASOURCE_PASSWORD=postgres
      - REDIS_HOST=redis-service
      - REDIS_PORT=6379

networks:
  my-net:
    driver: bridge

volumes:
  postgres-data:
```

Changes:
- Removed `version: "3.7"` (deprecated in Compose V2)
- `postgres:13.1-alpine` -> `postgres:16-alpine`
- Host-path volume -> named volume `postgres-data`
- Added `volumes:` top-level declaration for `postgres-data`
- Volume path corrected to `/var/lib/postgresql/data` (standard Postgres path)
- Added environment variables to `ms-authentication-service` for datasource + Redis host
- Removed commented-out angular service block
- Redis image unchanged (`redis:7-alpine`)

### 3. Data migration consideration

Named volume `postgres-data` is new. Existing data in the old host-path volume will NOT be automatically migrated. Options:
- **Dev environment**: Accept data loss (start fresh with `ddl-auto: update`)
- **If data needed**: Manually copy old volume contents to new named volume before first start

## Todo List
- [ ] Update Dockerfile base image to `eclipse-temurin:21-jre-alpine`
- [ ] Remove `version: "3.7"` from docker-compose.yml
- [ ] Update postgres image to `postgres:16-alpine`
- [ ] Replace host-path volume with named volume `postgres-data`
- [ ] Add environment variables for app service (datasource URL, Redis host)
- [ ] Remove commented-out angular service block
- [ ] Test `docker-compose build` succeeds
- [ ] Test `docker-compose up` starts all 3 services

## Success Criteria
- `docker-compose build` completes without errors
- `docker-compose up` starts postgres, redis, and app containers
- App container runs on Java 21 (`docker exec <container> java -version`)
- Postgres accepts connections on port 5432
- Named volume `postgres-data` created and used

## Risk Assessment
- **Low risk**: image tag changes are straightforward
- **Data loss**: switching from host-path to named volume means existing dev data not migrated — acceptable
- Alpine compatibility: `eclipse-temurin:21-jre-alpine` uses musl libc; no known issues for Spring Boot apps

## Security Considerations
- `eclipse-temurin:21-jre-alpine` receives regular security patches from Adoptium
- PostgreSQL 16 includes latest security fixes
- Database credentials still in docker-compose — pre-existing, out of scope for migration

## Next Steps
Proceed to Phase 7 (Compile, Test & Verify).
