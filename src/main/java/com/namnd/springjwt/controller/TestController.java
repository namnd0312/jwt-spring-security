package com.namnd.springjwt.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/test")
public class TestController {

    @GetMapping("/user")
    @PreAuthorize("hasRole('USER') or hasRole('PM') or hasRole('ADMIN')")
    public ResponseEntity<?> userAccess(Authentication authentication) {
        return ResponseEntity.ok(buildResponse(authentication, "User Content"));
    }

    @GetMapping("/pm")
    @PreAuthorize("hasRole('PM') or hasRole('ADMIN')")
    public ResponseEntity<?> pmAccess(Authentication authentication) {
        return ResponseEntity.ok(buildResponse(authentication, "Project Manager Board"));
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> adminAccess(Authentication authentication) {
        return ResponseEntity.ok(buildResponse(authentication, "Admin Board"));
    }

    private Map<String, Object> buildResponse(Authentication auth, String content) {
        List<String> roles = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("message", content);
        response.put("username", auth.getName());
        response.put("roles", roles);
        return response;
    }
}
