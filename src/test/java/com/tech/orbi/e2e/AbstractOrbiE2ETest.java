package com.tech.orbi.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tech.orbi.Repository.DeliveryRepository;
import com.tech.orbi.Repository.DriverRepository;
import com.tech.orbi.Repository.RatingRepository;
import com.tech.orbi.Repository.RoleRepository;
import com.tech.orbi.Repository.RouteRepository;
import com.tech.orbi.Repository.UserRepository;
import com.tech.orbi.dto.LoginRequestDto;
import com.tech.orbi.dto.LoginResponseDto;
import com.tech.orbi.entity.Delivery;
import com.tech.orbi.entity.DeliveryStatus;
import com.tech.orbi.entity.Driver;
import com.tech.orbi.entity.Role;
import com.tech.orbi.entity.Route;
import com.tech.orbi.entity.RouteStatus;
import com.tech.orbi.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:orbi_e2e;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
                "spring.jpa.show-sql=false",
                "spring.sql.init.mode=always",
                "spring.jpa.defer-datasource-initialization=true",
                "admin.password=e2e-admin-pass",
                "ors.api-key=e2e-dummy-ors-key",
                "ors.base-url=http://localhost:9999",
                "orbi.route.surface-weight-enabled=true",
                "jwt.public.key=classpath:app.pub",
                "jwt.private.key=classpath:app.key"
        }
)
@AutoConfigureMockMvc
public abstract class AbstractOrbiE2ETest {

    private static final AtomicInteger SEQ = new AtomicInteger(1);

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected BCryptPasswordEncoder passwordEncoder;

    @Autowired
    protected JwtDecoder jwtDecoder;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected RoleRepository roleRepository;

    @Autowired
    protected DriverRepository driverRepository;

    @Autowired
    protected RouteRepository routeRepository;

    @Autowired
    protected DeliveryRepository deliveryRepository;

    @Autowired
    protected RatingRepository ratingRepository;

    @BeforeEach
    void cleanDatabasePreservingRolesAndAdmin() {
        ratingRepository.deleteAllInBatch();
        deliveryRepository.deleteAllInBatch();
        routeRepository.deleteAllInBatch();

        List<User> allUsers = userRepository.findAll();
        for (User user : allUsers) {
            if (!"admin".equalsIgnoreCase(user.getEmail())) {
                if (user.getRoles() != null && !user.getRoles().isEmpty()) {
                    user.setRoles(new HashSet<>());
                    userRepository.saveAndFlush(user);
                }
            }
        }

        // Use deleteAll() instead of deleteAllInBatch(): Driver maps @JoinTable("tb_users_role"),
        // so deleteAllInBatch() generates an unqualified DELETE FROM tb_users_role that strips admin's ADMIN role.
        driverRepository.deleteAll();
        driverRepository.flush();

        for (User user : userRepository.findAll()) {
            if (!"admin".equalsIgnoreCase(user.getEmail())) {
                userRepository.delete(user);
            }
        }
        userRepository.flush();

        ensureRoleExists(Role.Values.ADMIN.name());
        ensureRoleExists(Role.Values.BASIC.name());
        ensureRoleExists(Role.Values.DRIVER.name());

        userRepository.findByEmail("admin").ifPresent(adminUser ->
                assertThat(adminUser.getRoles())
                        .extracting(Role::getName)
                        .contains(Role.Values.ADMIN.name())
        );
    }

    private Role ensureRoleExists(String roleName) {
        Role existing = roleRepository.findByName(roleName);
        if (existing != null) {
            return existing;
        }
        Role role = new Role();
        role.setName(roleName);
        return roleRepository.saveAndFlush(role);
    }

    protected String uniqueTag() {
        return String.valueOf(SEQ.getAndIncrement());
    }

    protected User seedUser(String name, String email, String phone, String rawPassword, String roleName) {
        Role role = ensureRoleExists(roleName);
        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setPhone(phone);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setRoles(new HashSet<>(Set.of(role)));
        return userRepository.saveAndFlush(user);
    }

    protected DriverSession seedDriverWithLogin(String prefix, String rawPassword) throws Exception {
        String tag = uniqueTag();
        String email = prefix + "." + tag + "@orbi.com";
        String phone = "1199" + String.format("%07d", Integer.parseInt(tag));
        String cnh = "CNH" + String.format("%08d", Integer.parseInt(tag));

        User user = seedUser("Driver " + prefix + " " + tag, email, phone, rawPassword, Role.Values.DRIVER.name());

        Driver driver = new Driver();
        driver.setUserId(user.getId());
        driver.setDriverName(user.getName());
        driver.setCnh(cnh);
        driver.setPhone(phone);
        driver.setRoles(null);
        Driver savedDriver = driverRepository.saveAndFlush(driver);

        String token = loginAndGetToken(email, rawPassword);
        return new DriverSession(user, savedDriver, token, email);
    }

    protected UserSession seedUserWithLogin(String prefix, String roleName, String rawPassword) throws Exception {
        String tag = uniqueTag();
        String email = prefix + "." + tag + "@orbi.com";
        String phone = "1188" + String.format("%07d", Integer.parseInt(tag));
        User user = seedUser("User " + prefix + " " + tag, email, phone, rawPassword, roleName);
        String token = loginAndGetToken(email, rawPassword);
        return new UserSession(user, token, email);
    }

    protected String loginAndGetToken(String email, String rawPassword) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequestDto(email, rawPassword))))
                .andExpect(status().isOk())
                .andReturn();

        LoginResponseDto response = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                LoginResponseDto.class
        );
        assertThat(response.accessToken()).isNotBlank();
        return response.accessToken();
    }

    protected Route seedRoute(Driver assignedDriver, User createdBy, RouteStatus status, int totalStops) {
        Route route = new Route();
        route.setDriver(assignedDriver);
        route.setCreatedBy(createdBy);
        route.setStatus(status);
        route.setTotalStops(totalStops);
        route.setTotalDistance(12.5);
        route.setTotalCost(45.0);
        if (assignedDriver != null) {
            route.setAssignedAt(LocalDateTime.now());
        }
        return routeRepository.saveAndFlush(route);
    }

    protected Delivery seedDelivery(Route route, int order, String address, DeliveryStatus status) {
        Delivery delivery = new Delivery();
        delivery.setRoute(route);
        delivery.setOrder(order);
        delivery.setAddress(address);
        delivery.setLatitude(new BigDecimal("-23.55052000"));
        delivery.setLongitude(new BigDecimal("-46.63330800"));
        delivery.setStatus(status);
        delivery.setRecipientEmail("dest" + order + "@orbi.com");
        return deliveryRepository.saveAndFlush(delivery);
    }

    protected record DriverSession(User user, Driver driver, String jwt, String email) {}

    protected record UserSession(User user, String jwt, String email) {}
}
