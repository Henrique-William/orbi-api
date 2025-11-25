package com.tech.orbi.controller;

import com.tech.orbi.Repository.RoleRepository;
import com.tech.orbi.Repository.UserRepository;
import com.tech.orbi.dto.UserDto;

import com.tech.orbi.dto.UserUpdateDto;
import com.tech.orbi.dto.UsersListDto;
import com.tech.orbi.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserRepository userRepository;

    public UserController(BCryptPasswordEncoder passwordEncoder, UserRepository userRepository, RoleRepository roleRepository) {
        this.userRepository = userRepository;
    }

    // Get all users
    @PreAuthorize("hasAuthority('SCOPE_ADMIN')")
    @GetMapping
    public ResponseEntity<UsersListDto> getAllUsers(@RequestParam(value = "page", defaultValue = "0") int page,
                                                    @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {

        Page<User> usersPage = userRepository.findAll(
                PageRequest.of(page, pageSize, Sort.Direction.DESC, "createdAt")
        );

        List<UserDto> userDto = usersPage.getContent().stream()
                .map(user -> new UserDto(
                        user.getId(),
                        user.getName(),
                        user.getEmail(),
                        user.getCreatedAt(),
                        user.getDriverProfile(),
                        user.getVehicles(),
                        user.getRoles()))
                .collect(Collectors.toList());

        UsersListDto usersListDto = new UsersListDto(
                userDto,
                usersPage.getNumber(),
                usersPage.getSize(),
                usersPage.getTotalPages(),
                usersPage.getTotalElements());

        return ResponseEntity.ok(usersListDto);

    }

    // Get user by ID
    @PreAuthorize("hasAuthority('SCOPE_ADMIN') or authentication.principal.claims['sub'] == #userId.toString()")
    @GetMapping("/{userId}")
    public ResponseEntity<UserDto> getUserById(@PathVariable UUID userId) {

        Optional<User> userOptional = userRepository.findById(userId);

        if (userOptional.isPresent()) {
            User user = userOptional.get();
            UserDto userDto = new UserDto(
                    user.getId(),
                    user.getName(),
                    user.getEmail(),
                    user.getCreatedAt(),
                    user.getDriverProfile(),
                    user.getVehicles(),
                    user.getRoles()
            );

            return ResponseEntity.ok(userDto);

        } else {

            return ResponseEntity.notFound().build();

        }
    }

    // Update user By ID
    @Transactional
    @PreAuthorize("hasAuthority('SCOPE_ADMIN') or authentication.principal.claims['sub'] == #userId.toString()")
    @PutMapping("/{userId}")
    public ResponseEntity<UserDto> updateUserById(@PathVariable UUID userId,
                                                  @RequestBody UserUpdateDto userUpdateDto) {

        Optional<User> userOptional = userRepository.findById(userId);

        if (userOptional.isPresent()) {
            User user = userOptional.get();

            user.setName(userUpdateDto.name() != null ? userUpdateDto.name() : user.getName());
            user.setEmail(userUpdateDto.email() != null ? userUpdateDto.email() : user.getEmail());
            user.setDriverProfile(userUpdateDto.driverProfile() != null ? userUpdateDto.driverProfile() : user.getDriverProfile());
            user.setVehicles(userUpdateDto.vehicles() != null ? userUpdateDto.vehicles() : user.getVehicles());
            user.setRoles(userUpdateDto.role() != null ? userUpdateDto.role() : user.getRoles());

            User updatedUser = userRepository.save(user);

            UserDto userDto = new UserDto(
                    updatedUser.getId(),
                    updatedUser.getName(),
                    updatedUser.getEmail(),
                    updatedUser.getCreatedAt(),
                    updatedUser.getDriverProfile(),
                    updatedUser.getVehicles(),
                    updatedUser.getRoles()
            );

            return ResponseEntity.ok(userDto);

        } else {

            return ResponseEntity.notFound().build();

        }
    }

    @Transactional // Importante para garantir a atomicidade da operação no banco
    @PreAuthorize("hasAuthority('SCOPE_ADMIN') or authentication.name == #userId.toString()")
    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> deleteUserById(@PathVariable UUID userId) {

        var userOptional = userRepository.findById(userId);

        if (userOptional.isPresent()) {
            User user = userOptional.get();

            // Remove the link with table (tb_users_role)
            user.getRoles().clear();

            // 2. Delete the user
            userRepository.delete(user);

            return ResponseEntity.noContent().build();
        }

        return ResponseEntity.notFound().build();
    }
}
