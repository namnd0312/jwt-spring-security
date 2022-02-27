package com.namnd.springjwt.controller;

import com.namnd.springjwt.dto.JwtResponseDto;
import com.namnd.springjwt.dto.RegisterDto;
import com.namnd.springjwt.dto.mapper.RegisterDtoMapper;
import com.namnd.springjwt.model.Role;
import com.namnd.springjwt.model.User;
import com.namnd.springjwt.service.JwtService;
import com.namnd.springjwt.service.RoleService;
import com.namnd.springjwt.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
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

    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@RequestBody User user) {

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        user.getUsername(),
                        user.getPassword()
                )
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);

        String jwt = jwtService.generateTokenLogin(authentication);
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        User currentUser = userService.findByUserName(user.getUsername()).get();
        return ResponseEntity.ok(new JwtResponseDto(currentUser.getId(), jwt, userDetails.getUsername(), currentUser.getFullName(), userDetails.getAuthorities()));
    }

    @PostMapping("/register")
    public ResponseEntity<String> registerUser(@RequestBody RegisterDto registerDto) {
        if(userService.existsByUsername(registerDto.getUsername())) {
            return new ResponseEntity<String>("Fail -> Username is already taken!",
                    HttpStatus.BAD_REQUEST);
        }

        Set<Role> roles = registerDto.getRoles();

        for (Role role: roles) {
            if(roleService.findByName(role.getName()) == null){
                roleService.save(role);
                roleService.flush();
            }else {
                role.setId(roleService.findByName(role.getName()).getId());
            }
        }

        Optional<User> user = this.userService.findByUserName(registerDto.getUsername());

        if(user.isPresent()){
            return new ResponseEntity<String>("Fail -> Username is already taken!",
                    HttpStatus.BAD_REQUEST);
        }

        User user1 = registerDtoMapper.toEntity(registerDto);
        userService.save(user1);

        return ResponseEntity.ok().body("User registered successfully!");
    }
}