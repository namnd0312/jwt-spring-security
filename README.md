# JWT Spring Security

REST API authentication & authorization using JWT (JSON Web Tokens) with Spring Boot 2.6.4 and Spring Security.

**Current Version:** 0.0.1-SNAPSHOT
**Java:** 1.8 (source) | 11 (Docker) | 21 (JDK compatibility via Lombok 1.18.30)
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
  "email": "john@example.com",
  "password": "password123"
}

Response (200 OK):
{
  "id": 1,
  "token": "eyJhbGc...",
  "refreshToken": "eyJhbGc...",
  "email": "john@example.com",
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
  "email": "jane@example.com",
  "fullName": "Jane Doe",
  "roles": [
    {"name": "ROLE_USER"},
    {"name": "ROLE_PM"}
  ]
}

Response (200 OK):
"User registered successfully! Please check your email to activate your account."
```

**GET /api/auth/activate** - Activate account via email link
```
Request:
GET /api/auth/activate?token=<activation-token-from-email>

Response (200 OK):
"Account activated successfully."

Response (400 Bad Request):
"Invalid or expired activation token."
```

**POST /api/auth/resend-activation** - Resend activation email
```json
Request:
{
  "email": "jane@example.com"
}

Response (200 OK):
"If the email exists and is not yet activated, a new activation link has been sent."
```

**POST /api/auth/forgot-password** - Request password reset
```json
Request:
{
  "email": "jane@example.com"
}

Response (200 OK):
"If the email exists, a password reset link has been sent."
```

**POST /api/auth/reset-password** - Reset password with token
```json
Request:
{
  "token": "reset-token-from-email",
  "newPassword": "newSecure123"
}

Response (200 OK):
"Password reset successful."
```

**POST /api/auth/refresh-token** - Get new access token
```json
Request:
{
  "refreshToken": "eyJhbGc..."
}

Response (200 OK):
{
  "accessToken": "eyJhbGc...",
  "refreshToken": "eyJhbGc..."
}
```

**POST /api/auth/logout** - Logout and blacklist token
```
Request Headers:
Authorization: Bearer <token>

Response (200 OK):
"Logged out successfully."
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
- Spring Mail (for password reset emails)
- BCrypt password encoding
- Lombok 1.18.30 (JDK 21 compatible)
- Maven WAR packaging

**Security:**
- Stateless JWT authentication with refresh tokens
- HS512 signature algorithm
- 15-minute access token expiration (900000 ms, configurable)
- 7-day refresh token rotation on each use
- JTI-based token blacklisting for logout
- Password reset flow via email
- Role-based access control (@PreAuthorize)
- CORS enabled
- CSRF disabled (appropriate for JWT API)

**Authentication Flow:**
1. User registers → account created with `active=false`, activation email sent
2. User clicks activation link → GET /api/auth/activate?token=xxx → account activated
3. User submits email + password → AuthenticationManager validates + checks `active` flag
4. Inactive accounts receive 401 "Account not activated"
5. JwtService generates HS512 access token + refresh token
6. Client stores both tokens, sends access token in Authorization header
7. JwtAuthenticationFilter validates token on each request
8. SecurityContext populated with UserDetails/roles
9. On token expiration, client uses refresh token to obtain new pair
10. Logout blacklists current token by JTI and deletes refresh token

## Configuration

**src/main/resources/application.yml:**
```yaml
server.port: 8080                          # Server port
spring.datasource.url: jdbc:postgresql://localhost:5432/testdb
spring.mail.host: smtp.gmail.com           # SMTP server for password reset emails
spring.mail.port: 587
namnd.app.jwtSecret: bezKoderSecretKey     # Token signing key
namnd.app.jwtExpiration: 900000            # 15 minutes in milliseconds
namnd.app.jwtRefreshExpiration: 604800000  # 7 days in milliseconds
namnd.app.passwordResetBaseUrl: http://localhost:3000/reset-password
namnd.app.activationBaseUrl: http://localhost:8080/api/auth/activate
```

**Override via environment:**
```bash
-Dnamnd.app.jwtSecret=your-secret-key
-Dnamnd.app.jwtExpiration=900000
-Dnamnd.app.jwtRefreshExpiration=604800000
-DMAIL_USERNAME=your-email@gmail.com
-DMAIL_PASSWORD=your-app-password
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
- **users** table: id, username, email (unique), password, full_name, active (boolean, default false)
- **roles** table: id, name
- **user_roles** junction: user_id (FK), role_id (FK)
- **refresh_tokens** table: id, token, expiry_date, user_id (FK)
- **password_reset_tokens** table: id, token, expiry_date, user_id (FK)
- **activation_tokens** table: id, token (unique), expiry_date, user_id (FK), used (boolean)
- **blacklisted_tokens** table: id, jti (JWT ID), expiry_date

**Default Roles:** ROLE_USER, ROLE_PM, ROLE_ADMIN (from roles.sql)

**Note:** Hibernate DDL auto is `create-drop` (for development) - enable `none` for production.

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
- Access token expired? Default: 15 minutes - use refresh endpoint
- Verify jwtSecret matches between server & client
- Check Authorization header format: `Bearer <token>`
- Token blacklisted (logged out)? Must re-login

**403 Access denied**
- User lacks required role? Check @PreAuthorize annotation
- Default test roles: ROLE_USER, ROLE_PM, ROLE_ADMIN

**Refresh token invalid**
- Refresh token expired? Default: 7 days - must re-login
- Refresh token already rotated? Obtain new pair from /api/auth/refresh-token
- User logged out? Refresh token deleted - must re-login

**401 Account not activated**
- After registration, check inbox for activation email
- Click the activation link (valid for 24 hours)
- Use POST /api/auth/resend-activation with your email if link expired

**Activation link not working**
- Link valid for 24 hours - request a new one via POST /api/auth/resend-activation
- Check ACTIVATION_BASE_URL environment variable
- Verify MAIL_USERNAME and MAIL_PASSWORD for Gmail SMTP

**Password reset link not working**
- Check PASSWORD_RESET_BASE_URL environment variable
- Verify MAIL_USERNAME and MAIL_PASSWORD for Gmail SMTP
- Ensure email address exists in database

## Development

[See development rules in .claude/rules/development-rules.md]

- Follow kebab-case file naming
- Keep code files under 200 lines (modularize if needed)
- Run `mvn compile` before committing
- No secrets in git (use environment variables)
