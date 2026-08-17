package com.tech.orbi.controller;

import com.tech.orbi.Repository.DriverProfileRepository;
import com.tech.orbi.Repository.RoleRepository;
import com.tech.orbi.Repository.UserRepository;
import com.tech.orbi.dto.*;
import com.tech.orbi.entity.ApprovalStatus;
import com.tech.orbi.entity.DriverProfile;
import com.tech.orbi.entity.User;
import com.tech.orbi.entity.Vehicle;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserRepository userRepository;
    private final DriverProfileRepository driverProfileRepository;

    public UserController(UserRepository userRepository, DriverProfileRepository driverProfileRepository) {
        this.userRepository = userRepository;
        this.driverProfileRepository = driverProfileRepository;
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
                .map(this::convertToUserResponseDto)
                .map(ResponseEntity::ok)
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
            return ResponseEntity.ok(convertToUserResponseDto(updatedUser));
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
    public ResponseEntity<DriverProfileDto> createDriverProfile(@PathVariable UUID userId, @RequestBody CreateDriverProfileDto dto) {
        return userRepository.findById(userId).map(user -> {
            if (user.getDriverProfile() != null) {
                return ResponseEntity.badRequest().<DriverProfileDto>build();
            }
            DriverProfile driverProfile = new DriverProfile();
            driverProfile.setUser(user);
            driverProfile.setLicenseNumber(dto.licenseNumber());
            driverProfile.setApprovalStatus(ApprovalStatus.PENDING);
            user.setDriverProfile(driverProfile);
            User savedUser = userRepository.save(user);
            return ResponseEntity.ok(convertToDriverProfileDto(savedUser.getDriverProfile()));
        }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{userId}/driver-profile")
    public ResponseEntity<DriverProfileDto> getDriverProfile(@PathVariable UUID userId) {
        return userRepository.findById(userId)
                .map(User::getDriverProfile)
                .map(this::convertToDriverProfileDto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Transactional
    @DeleteMapping("/{userId}/driver-profile")
    @PreAuthorize("hasAuthority('SCOPE_ADMIN') or authentication.name == #userId.toString()")
    public ResponseEntity<Void> deleteDriverProfile(@PathVariable UUID userId) {
        return userRepository.findById(userId).map(user -> {
            DriverProfile driverProfile = user.getDriverProfile();
            if (driverProfile != null) {
                user.setDriverProfile(null);
                driverProfileRepository.delete(driverProfile);
                return ResponseEntity.noContent().<Void>build();
            }
            return ResponseEntity.notFound().<Void>build();
        }).orElse(ResponseEntity.notFound().build());
    }

    private UserResponseDto convertToUserResponseDto(User user) {
        DriverProfileDto driverProfileDto = user.getDriverProfile() != null ? convertToDriverProfileDto(user.getDriverProfile()) : null;
        return new UserResponseDto(user.getId(), user.getName(), user.getEmail(), user.getPhone(), user.getCreatedAt(), driverProfileDto);
    }

    private DriverProfileDto convertToDriverProfileDto(DriverProfile driverProfile) {
        List<VehicleDto> vehicleDtos = driverProfile.getVehicles() != null
                ? driverProfile.getVehicles().stream().map(this::convertToVehicleDto).collect(Collectors.toList())
                : Collections.emptyList();
        return new DriverProfileDto(driverProfile.getUserId(), driverProfile.getLicenseNumber(), driverProfile.getApprovalStatus(), driverProfile.isActive(), driverProfile.getAverageRating(), driverProfile.getProfileCreatedAt(), vehicleDtos);
    }

    private VehicleDto convertToVehicleDto(Vehicle vehicle) {
        return new VehicleDto(vehicle.getId(), vehicle.getLicensePlate(), vehicle.getBrand(), vehicle.getModel(), vehicle.getColor(), vehicle.getYear(), vehicle.isDefault());
    }
}