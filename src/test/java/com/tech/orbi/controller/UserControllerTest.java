package com.tech.orbi.controller;

import com.tech.orbi.Repository.DriverRepository;
import com.tech.orbi.Repository.RoleRepository;
import com.tech.orbi.Repository.UserRepository;
import com.tech.orbi.dto.CreateDriverDto;
import com.tech.orbi.entity.Role;
import com.tech.orbi.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private DriverRepository driverRepository;

    @InjectMocks
    private UserController userController;

    private UUID userId;
    private User user;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = new User();
        user.setId(userId);
        user.setName("Teste");
        user.setPhone("1999999999");
        user.setRoles(new HashSet<>());
    }

    @Test
    void testCreateDriverProfileWithDuplicateCnhThrowsUnprocessableEntity() {
        CreateDriverDto dto = new CreateDriverDto("12345678900");
        when(driverRepository.existsByCnh("12345678900")).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> userController.createDriverProfile(userId, dto));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.getStatusCode());
        assertTrue(ex.getReason().contains("CNH já cadastrada no sistema"));
        verify(driverRepository, never()).save(any());
    }

    @Test
    void testCreateDriverProfileSuccess() {
        CreateDriverDto dto = new CreateDriverDto("98765432100");
        when(driverRepository.existsByCnh("98765432100")).thenReturn(false);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        Role driverRole = new Role();
        driverRole.setName(Role.Values.DRIVER.name());
        when(roleRepository.findByName(Role.Values.DRIVER.name())).thenReturn(driverRole);

        ResponseEntity<Void> response = userController.createDriverProfile(userId, dto);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(driverRepository).save(argThat(driver -> {
            assertEquals("98765432100", driver.getCnh());
            assertEquals(userId, driver.getUserId());
            return true;
        }));
    }
}
