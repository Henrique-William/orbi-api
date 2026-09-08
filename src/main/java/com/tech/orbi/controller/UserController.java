package com.tech.orbi.controller;

import com.tech.orbi.Repository.DriverRepository;
import com.tech.orbi.Repository.RoleRepository;
import com.tech.orbi.Repository.UserRepository;
import com.tech.orbi.dto.*;
import com.tech.orbi.entity.Driver;
import com.tech.orbi.entity.Role;
import com.tech.orbi.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserRepository userRepository;
    private final DriverRepository driverRepository;
    private final RoleRepository roleRepository;

    public UserController(UserRepository userRepository, DriverRepository driverRepository,  RoleRepository roleRepository) {
        this.userRepository = userRepository;
        this.driverRepository = driverRepository;
        this.roleRepository = roleRepository;
    }

    @PreAuthorize("hasAuthority('SCOPE_ADMIN')")
    @GetMapping
    public ResponseEntity<UsersListDto> getAllUsers(@RequestParam(value = "page", defaultValue = "0") int page,
                                                    @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        Page<User> usersPage = userRepository.findAll(PageRequest.of(page, pageSize, Sort.Direction.DESC, "createdAt"));
        List<UserDto> userDtos = usersPage.getContent().stream()
                .map(user -> new UserDto(user.getId(), user.getName(), user.getEmail(), user.getCreatedAt(), user.getRoles()))
                .collect(Collectors.toList());
        UsersListDto usersListDto = new UsersListDto(userDtos, usersPage.getNumber(), usersPage.getSize(), usersPage.getTotalPages(), usersPage.getTotalElements());
        return ResponseEntity.ok(usersListDto);
    }

    @PreAuthorize("hasAuthority('SCOPE_ADMIN') or authentication.principal.claims['sub'] == #userId.toString()")
    @GetMapping("/{userId}")
    public ResponseEntity<UserResponseDto> getUserById(@PathVariable UUID userId) {
        return userRepository.findById(userId)
                .map(user -> ResponseEntity.ok(user))
                .orElse(ResponseEntity.notFound().build());
    }

    @Transactional
    @PreAuthorize("hasAuthority('SCOPE_ADMIN') or authentication.principal.claims['sub'] == #userId.toString()")
    @PutMapping("/{userId}")
    public ResponseEntity<UserResponseDto> updateUserById(@PathVariable UUID userId, @RequestBody UserUpdateDto userUpdateDto) {
        return userRepository.findById(userId).map(user -> {
            user.setName(userUpdateDto.name() != null ? userUpdateDto.name() : user.getName());
            user.setEmail(userUpdateDto.email() != null ? userUpdateDto.email() : user.getEmail());
            user.setRoles(userUpdateDto.role() != null ? userUpdateDto.role() : user.getRoles());
            User updatedUser = userRepository.save(user);
            return ResponseEntity.ok((updatedUser));
        }).orElse(ResponseEntity.notFound().build());
    }

    @Transactional
    @PreAuthorize("hasAuthority('SCOPE_ADMIN') or authentication.name == #userId.toString()")
    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> deleteUserById(@PathVariable UUID userId) {
        return userRepository.findById(userId).map(user -> {
            user.getRoles().clear();
            userRepository.delete(user);
            return ResponseEntity.noContent().<Void>build();
        }).orElse(ResponseEntity.notFound().build());
    }

    @Transactional
    @PostMapping("/{userId}/driver-profile")
    public ResponseEntity<DriverProfileDto> createDriverProfile(@RequestBody CreateDriverDto driverDto) {
        Driver driver = new Driver();
        var driverRole = roleRepository.findByName(Role.Values.DRIVER.name());

        driver.setCnh(driverDto.licenseNumber());
        driver.setRoles(Set.of(driverRole));

        driverRepository.save(driver);
        return ResponseEntity.ok().build();

    }

    @GetMapping("/{userId}/driver-profile")
    public ResponseEntity<DriverProfileDto> getDriverProfile(@PathVariable UUID userId) {
        return driverRepository.findById(userId)
                .map(driver -> ResponseEntity.ok())
                .orElse(ResponseEntity.notFound().build());
    }

    @Transactional
    @DeleteMapping("/{userId}/driver-profile")
    @PreAuthorize("hasAuthority('SCOPE_ADMIN') or authentication.name == #userId.toString()")
    public ResponseEntity<Void> deleteDriverProfile(@PathVariable UUID driverId) {

        if (!driverRepository.existsById(driverId)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.noContent().build();

    }
}