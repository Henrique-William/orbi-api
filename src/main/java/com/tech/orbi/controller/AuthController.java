package com.tech.orbi.controller;

import com.tech.orbi.Repository.RoleRepository;
import com.tech.orbi.Repository.UserRepository;
import com.tech.orbi.dto.LoginRequestDto;
import com.tech.orbi.dto.LoginResponseDto;
import com.tech.orbi.dto.RegisterUserDto;
import com.tech.orbi.entity.Role;
import com.tech.orbi.entity.User;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.*;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final JwtEncoder jwtEncoder;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtDecoder jwtDecoder;

    public AuthController(UserRepository userRepository, RoleRepository roleRepository, JwtEncoder jwtEncoder, BCryptPasswordEncoder passwordEncoder, JwtDecoder jwtDecoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.jwtEncoder = jwtEncoder;
        this.passwordEncoder = passwordEncoder;
        this.jwtDecoder = jwtDecoder;
    }

    @Transactional
    @PostMapping("/register")
    public ResponseEntity<Void> register(@RequestBody RegisterUserDto dto) {

        var user = new User();
        var basicRole = roleRepository.findByName(Role.Values.BASIC.name());
        var userFromDb = userRepository.findByEmail(dto.email());

        if (userFromDb.isPresent()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY);
        }

        user.setName(dto.name());
        user.setEmail(dto.email());
        user.setPhone(dto.phoneNumber());
        user.setPassword(passwordEncoder.encode(dto.password()));
        user.setRoles(Set.of(basicRole));

        userRepository.save(user);

        return ResponseEntity.ok().build();

    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDto> login(@RequestBody LoginRequestDto loginRequestDto, HttpServletResponse response) {

        var user = userRepository.findByEmail(loginRequestDto.email());

        if (user.isEmpty() || !user.get().isLoginCorrect(loginRequestDto, passwordEncoder)) {
            throw new BadCredentialsException("user or password is invalid");
        }

        var now = Instant.now();
        var expiresIn = 300L;

        var scopes = user.get().getRoles()
                .stream()
                .map(Role::getRoleName)
                .collect(Collectors.joining(" "));

        var claims = JwtClaimsSet.builder()
                .issuer("orbi")
                .subject(user.get().getId().toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(expiresIn))
                .claim("scope", scopes)
                .build();

        var jwtValue = jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();

        ResponseCookie cookie = ResponseCookie.from("token", jwtValue)
                .httpOnly(true)
                .secure(false)
                .path("/") // true em produção
                .maxAge(Duration.ofSeconds(expiresIn))
                .sameSite("Strict")
                .build();

        response.setHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        return ResponseEntity.ok(new LoginResponseDto(jwtValue, expiresIn));
    }

    @GetMapping("/validate")
    public ResponseEntity<Void> validateToken(HttpServletRequest request) {

        Cookie[] cookies = request.getCookies();

        if (cookies == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String token = null;

        for (Cookie cookie : cookies) {
            if ("token".equals(cookie.getName())) {
                token = cookie.getValue();
                break;
            }
        }

        if (token == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            Jwt jwt = jwtDecoder.decode(token);

            if (jwt.getExpiresAt().isBefore(Instant.now())) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            return ResponseEntity.ok().build();

        } catch (JwtException e){
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

    }

}
