package com.tech.orbi.controller;

import com.tech.orbi.Repository.RoleRepository;
import com.tech.orbi.Repository.UserRepository;
import com.tech.orbi.dto.RegisterUserDto;
import com.tech.orbi.entity.Role;
import com.tech.orbi.entity.User;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
public class UserController {

    private final BCryptPasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    public UserController(BCryptPasswordEncoder passwordEncoder, UserRepository userRepository, RoleRepository roleRepository) {
        this.passwordEncoder = passwordEncoder;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
    }

    @Transactional
    @PostMapping("/api/auth/register")
    public ResponseEntity<Void> register(@RequestBody RegisterUserDto dto) {

        var basicRole = roleRepository.findByName(Role.Values.BASIC.name());
        var userFromDb = userRepository.findByEmail(dto.email());

        if (userFromDb.isPresent()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY);
        }

        var user = new User();

        user.setName(dto.name());
        user.setEmail(dto.email());
        user.setPassword(dto.password());
        user.setRoles(Set.of(basicRole));

        userRepository.save(user);

        return ResponseEntity.ok().build();

    }

    @GetMapping("/api/users")
    public ResponseEntity<List<User>> listUsers(JwtAuthenticationToken token) {

        var user = userRepository.findById(UUID.fromString(token.getName()));

        var isAdmin = user.get().getRoles().stream().anyMatch(role -> role.getRoleName().equalsIgnoreCase(Role.Values.ADMIN.name()));

        if (isAdmin) {
            var users = userRepository.findAll();
            return ResponseEntity.ok(users);
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

    }

}
