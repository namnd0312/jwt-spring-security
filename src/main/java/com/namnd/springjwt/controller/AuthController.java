package com.namnd.springjwt.controller;

import com.namnd.springjwt.dto.*;
import com.namnd.springjwt.dto.mapper.RegisterDtoMapper;
import com.namnd.springjwt.model.RefreshToken;
import com.namnd.springjwt.model.Role;
import com.namnd.springjwt.model.User;
import com.namnd.springjwt.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

import javax.servlet.http.HttpServletRequest;
import java.util.Date;
import java.util.Optional;
import java.util.Set;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private UserService userService;

    @Autowired
    private RoleService roleService;

    @Autowired
    private PasswordEncoder encoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private RegisterDtoMapper registerDtoMapper;

    @Autowired
    private PasswordResetService passwordResetService;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private BlacklistedTokenService blacklistedTokenService;

    @Autowired
    private ActivationService activationService;

    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequestDto loginRequest) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginRequest.getEmail(),
                            loginRequest.getPassword()
                    )
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);

            String jwt = jwtService.generateTokenLogin(authentication);
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            User currentUser = userService.findByEmail(loginRequest.getEmail())
                    .orElseThrow(() -> new org.springframework.security.core.userdetails.UsernameNotFoundException("User not found"));

            RefreshToken refreshToken = refreshTokenService.createRefreshToken(currentUser.getId());

            return ResponseEntity.ok(new JwtResponseDto(
                    currentUser.getId(),
                    jwt,
                    refreshToken.getToken(),
                    currentUser.getEmail(),
                    currentUser.getUsername(),
                    currentUser.getFullName(),
                    userDetails.getAuthorities()));
        } catch (DisabledException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Account not activated. Please check your email for the activation link.");
        }
    }

    @PostMapping("/register")
    public ResponseEntity<String> registerUser(@RequestBody RegisterDto registerDto) {
        // Validate email: required and unique
        if (registerDto.getEmail() == null || registerDto.getEmail().trim().isEmpty()) {
            return new ResponseEntity<>("Fail -> Email is required!",
                    HttpStatus.BAD_REQUEST);
        }
        if (userService.existsByEmail(registerDto.getEmail())) {
            return new ResponseEntity<>("Fail -> Email is already in use!",
                    HttpStatus.BAD_REQUEST);
        }

        Set<Role> roles = registerDto.getRoles();

        for (Role role : roles) {
            if (roleService.findByName(role.getName()) == null) {
                roleService.save(role);
                roleService.flush();
            } else {
                role.setId(roleService.findByName(role.getName()).getId());
            }
        }

        User user1 = registerDtoMapper.toEntity(registerDto);
        userService.save(user1);
        activationService.createActivationToken(user1);

        return ResponseEntity.ok().body("User registered successfully! Please check your email to activate your account.");
    }

    @GetMapping("/activate")
    public ResponseEntity<?> activateAccount(@RequestParam("token") String token) {
        try {
            activationService.activateAccount(token);
            return ResponseEntity.ok("Account activated successfully! You can now login.");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/resend-activation")
    public ResponseEntity<?> resendActivation(@RequestBody ForgotPasswordDto request) {
        // Always returns 200 to prevent email enumeration
        activationService.resendActivationToken(request.getEmail());
        return ResponseEntity.ok("If the email is registered and not yet activated, an activation email has been sent.");
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody ForgotPasswordDto forgotPasswordDto) {
        passwordResetService.createPasswordResetToken(forgotPasswordDto.getEmail());
        return ResponseEntity.ok("If the email exists, a password reset link has been sent.");
    }

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

    @PostMapping("/refresh-token")
    public ResponseEntity<?> refreshToken(
            @RequestBody RefreshTokenRequestDto refreshTokenRequestDto) {
        String requestRefreshToken = refreshTokenRequestDto.getRefreshToken();

        Optional<RefreshToken> tokenOptional = refreshTokenService
                .findByToken(requestRefreshToken);

        if (!tokenOptional.isPresent()) {
            return ResponseEntity.badRequest().body("Invalid refresh token.");
        }

        try {
            // Atomic: verify expiry + delete old + create new (single transaction)
            RefreshToken newRefreshToken = refreshTokenService
                    .rotateRefreshToken(tokenOptional.get());
            User user = newRefreshToken.getUser();

            String newAccessToken = jwtService.generateTokenFromEmail(user.getEmail());

            return ResponseEntity.ok(new TokenRefreshResponseDto(
                    newAccessToken, newRefreshToken.getToken()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request) {
        String jwt = getJwtFromRequest(request);

        if (jwt == null || !jwtService.validateJwtToken(jwt)) {
            return ResponseEntity.badRequest().body("No valid token provided.");
        }

        // Reject already-blacklisted tokens
        String jti = jwtService.getJtiFromToken(jwt);
        if (blacklistedTokenService.isTokenBlacklisted(jti)) {
            return ResponseEntity.badRequest().body("Token already invalidated.");
        }

        // Blacklist the current access token by JTI
        Date tokenExpiry = jwtService.getExpirationFromToken(jwt);
        blacklistedTokenService.blacklistToken(jti, tokenExpiry);

        // Delete the user's refresh token
        String email = jwtService.getEmailFromJwtToken(jwt);
        Optional<User> userOptional = userService.findByEmail(email);
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
}
