package com.tech.orbi.config;

import com.tech.orbi.Repository.RoleRepository;
import com.tech.orbi.Repository.UserRepository;
import com.tech.orbi.entity.Role;
import com.tech.orbi.entity.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;


import java.sql.Timestamp;
import java.time.Instant;
import java.util.Set;

@Configuration
public class AdminUserConfig implements CommandLineRunner {

    @Value("${admin.password}")
    private String adminPassword;

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;


    public AdminUserConfig(RoleRepository roleRepository, UserRepository userRepository, BCryptPasswordEncoder passwordEncoder) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) throws Exception {

        var now = Instant.now();
        var roleAdmin = roleRepository.findByName(Role.Values.ADMIN.name());

        // Step 1: Find or create the admin user.
        userRepository.findByEmail("admin").ifPresentOrElse(
                user -> System.out.println("Admin user already exists."),
                () -> {
                    System.out.println("Admin user not found, creating it.");
                    var newUser = new User();
                    newUser.setName("Admin");
                    newUser.setEmail("admin");
                    newUser.setPassword(passwordEncoder.encode(adminPassword));

                    newUser.setRoles(Set.of(roleAdmin));
//                    newUser.setCreatedAt(Timestamp.from(now));
                    userRepository.save(newUser);
                }
        );
    }
}