package com.tech.orbi.controller;

import com.tech.orbi.Repository.RoleRepository;
import com.tech.orbi.Repository.UserRepository;
import com.tech.orbi.dto.UserDto;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserRepository userRepository;

    public UserController(BCryptPasswordEncoder passwordEncoder, UserRepository userRepository, RoleRepository roleRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/list")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UserDto>> listUsers() {
        List<UserDto> users = userRepository.findAll()
                .stream()
                .map(user -> new UserDto(user.getId(), user.getName(), user.getEmail()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(users);
    }

}
