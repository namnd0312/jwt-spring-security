# JWT Spring Security

REST API authentication & authorization using JWT (JSON Web Tokens) with Spring Boot 2.6.4 and Spring Security.

**Current Version:** 0.0.1-SNAPSHOT
**Java:** 1.8 (source) | 11 (Docker)
**Database:** PostgreSQL 13.1
**License:** Proprietary

## Quick Start

### Prerequisites
- Java 8+ or Docker
- Maven 3.6+
- PostgreSQL 13+ (or use docker-compose)

### Build & Run

**Local Setup:**
```bash
# Set database credentials in src/main/resources/application.yml
# Default: postgres/123456 at localhost:5432/testdb

mvn clean install
mvn spring-boot:run
```

**Docker Compose:**
```bash
# Starts PostgreSQL (5432) + API (8080) with network bridge
docker-compose up --build
```

Server runs on `http://localhost:8080`

## API Reference

### Authentication Endpoints

**POST /api/auth/login** - Authenticate user
```json
Request:
{
  "username": "john",
  "password": "password123"
}

Response (200 OK):
{
  "id": 1,
  "token": "eyJhbGc...",
  "type": "Bearer",
  "username": "john",
  "name": "John Doe",
  "roles": ["ROLE_USER"]
}
```

**POST /api/auth/register** - Create new user
```json
Request:
{
  "username": "jane",
  "password": "secure123",
  "fullName": "Jane Doe",
  "roles": [
    {"name": "ROLE_USER"},
    {"name": "ROLE_PM"}
  ]
}

Response (200 OK):
"User registered successfully!"
```

### Protected Endpoints

Add `Authorization: Bearer <token>` header to access protected resources.

```bash
curl -H "Authorization: Bearer eyJhbGc..." http://localhost:8080/api/protected
```

## Architecture

**Stack:**
- Spring Boot 2.6.4
- Spring Security + JWT (JJWT 0.9.0)
- Spring Data JPA + Hibernate
- PostgreSQL + Flyway (manual DDL)
- BCrypt password encoding
- Maven WAR packaging

**Security:**
- Stateless JWT authentication (no sessions)
- HS512 signature algorithm
- 24-hour token expiration (configurable)
- Role-based access control (@PreAuthorize)
- CORS enabled
- CSRF disabled (appropriate for JWT API)

**Authentication Flow:**
1. User submits credentials → AuthenticationManager validates
2. JwtService generates HS512 token
3. Client stores token, sends in Authorization header
4. JwtAuthenticationFilter validates token on each request
5. SecurityContext populated with UserDetails/roles

## Configuration

**src/main/resources/application.yml:**
```yaml
server.port: 8080                    # Server port
spring.datasource.url: jdbc:postgresql://localhost:5432/testdb
namnd.app.jwtSecret: bezKoderSecretKey  # Token signing key
namnd.app.jwtExpiration: 86400000       # 24 hours in milliseconds
```

**Override via environment:**
```bash
-Dnamnd.app.jwtSecret=your-secret-key
-Dnamnd.app.jwtExpiration=3600000
```

## Project Structure

```
src/main/java/com/namnd/springjwt/
├── SpringJwtApplication.java          Main entry point
├── config/
│   ├── security/SecurityConfig.java   Filter chain, encoder, session policy
│   ├── filter/JwtAuthenticationFilter.java  Token extraction & validation
│   └── custom/CustomAccesDeniedHandler.java 403 error responses
├── controller/
│   └── AuthController.java            Login/register endpoints
├── model/
│   ├── User.java                      User entity
│   ├── Role.java                      Role entity
│   └── UserPrinciple.java             UserDetails adapter
├── dto/
│   ├── JwtResponseDto.java            Login response
│   ├── RegisterDto.java               Registration request
│   └── mapper/RegisterDtoMapper.java  DTO conversion
├── service/
│   ├── JwtService.java                Token generation/validation
│   ├── UserService.java               Interface
│   ├── RoleService.java               Interface
│   └── impl/                          Service implementations
└── repository/
    ├── UserRepository.java            User data access
    └── RoleRepository.java            Role data access
```

## Database

**Schema:** PostgreSQL 13.1
- **users** table: id, username, password, full_name
- **roles** table: id, name
- **user_roles** junction: user_id (FK), role_id (FK)

**Default Roles:** ROLE_USER, ROLE_PM, ROLE_ADMIN (from roles.sql)

**Note:** Hibernate DDL auto is `none` - manual schema management required.

## Testing

```bash
mvn test
```

## Documentation

- [Project Overview & PDR](./docs/project-overview-pdr.md)
- [Codebase Summary](./docs/codebase-summary.md)
- [Code Standards](./docs/code-standards.md)
- [System Architecture](./docs/system-architecture.md)
- [Deployment Guide](./docs/deployment-guide.md)

## Troubleshooting

**Connection refused (database)**
- Ensure PostgreSQL runs on localhost:5432
- Check credentials in application.yml
- Use docker-compose for automatic setup

**401 Invalid token**
- Token expired? Default: 24 hours
- Verify jwtSecret matches between server & client
- Check Authorization header format: `Bearer <token>`

**403 Access denied**
- User lacks required role? Check @PreAuthorize annotation
- Default test roles: ROLE_USER, ROLE_PM, ROLE_ADMIN

## Development

[See development rules in .claude/rules/development-rules.md]

- Follow kebab-case file naming
- Keep code files under 200 lines (modularize if needed)
- Run `mvn compile` before committing
- No secrets in git (use environment variables)
