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

    public UserController(UserRepository userRepository,
                          DriverRepository driverRepository,
                          RoleRepository roleRepository) {
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
    public ResponseEntity<User> getUserById(@PathVariable UUID userId) {
        return userRepository.findById(userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Transactional
    @PreAuthorize("hasAuthority('SCOPE_ADMIN') or authentication.principal.claims['sub'] == #userId.toString()")
    @PutMapping("/{userId}")
    public ResponseEntity<User> updateUserById(@PathVariable UUID userId, @RequestBody UserUpdateDto userUpdateDto) {
        return userRepository.findById(userId).map(user -> {
            user.setName(userUpdateDto.name() != null ? userUpdateDto.name() : user.getName());
            user.setEmail(userUpdateDto.email() != null ? userUpdateDto.email() : user.getEmail());
            user.setRoles(userUpdateDto.role() != null ? userUpdateDto.role() : user.getRoles());
            User updatedUser = userRepository.save(user);
            return ResponseEntity.ok(updatedUser);
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

    /**
     * CORREÇÃO: Recebe corretamente o PathVariable userId, busca o usuário correspondente,
     * e vincula as informações necessárias de identificação no perfil do motorista de forma atômica.
     */
    @Transactional
    @PostMapping("/{userId}/driver-profile")
    public ResponseEntity<Void> createDriverProfile(@PathVariable UUID userId, @RequestBody CreateDriverDto driverDto) {
        return userRepository.findById(userId).map(user -> {
            Driver driver = new Driver();
            driver.setUserId(userId);
            driver.setDriverName(user.getName());
            driver.setPhone(user.getPhone());
            driver.setCnh(driverDto.licenseNumber());

            var driverRole = roleRepository.findByName(Role.Values.DRIVER.name());
            driver.setRoles(Set.of(driverRole));
            driverRepository.save(driver);

            // Adiciona a role de motorista para o usuário também
            user.getRoles().add(driverRole);
            userRepository.save(user);

            return ResponseEntity.ok().<Void>build();
        }).orElse(ResponseEntity.notFound().build());
    }

    /**
     * CORREÇÃO: Resolve o erro de compilação do BodyBuilder incompleto ResponseEntity.ok(),
     * retornando corretamente o DriverDto composto por User e dados do perfil do motorista.
     */
    @GetMapping("/{userId}/driver-profile")
    public ResponseEntity<DriverDto> getDriverProfile(@PathVariable UUID userId) {
        return driverRepository.findById(userId)
                .flatMap(driver -> userRepository.findById(userId)
                        .map(user -> ResponseEntity.ok(new DriverDto(
                                user,
                                driver.getCnh(),
                                driver.getProfileCreatedAt()
                        ))))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * CORREÇÃO: Corrige o parâmetro PathVariable para bater exatamente com "{userId}"
     * e implementa a exclusão física do perfil do motorista no repositório.
     */
    @Transactional
    @DeleteMapping("/{userId}/driver-profile")
    @PreAuthorize("hasAuthority('SCOPE_ADMIN') or authentication.name == #userId.toString()")
    public ResponseEntity<Void> deleteDriverProfile(@PathVariable("userId") UUID userId) {
        if (!driverRepository.existsById(userId)) {
            return ResponseEntity.notFound().build();
        }
        driverRepository.deleteById(userId);
        return ResponseEntity.noContent().build();
    }
}