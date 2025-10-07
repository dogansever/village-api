package com.company.game.village.user;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @PostConstruct
    public void onStart() {
        try {
            register("DoganS", "dogansever@test.com", "1", true);
        } catch (Exception e) {
            // zaten varsa hata fırlatır, yoksa oluşturur
        }
    }

    public User register(String username, String email, String password, boolean isAdmin) {
        if (userRepository.findByUsername(username).isPresent())
            throw new RuntimeException("Username already exists");

        if (userRepository.findByEmail(email).isPresent())
            throw new RuntimeException("Email already exists");

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setIsAdmin(isAdmin);
        user.setPassword(passwordEncoder.encode(password));

        return userRepository.save(user);
    }

    public String login(String username, String password) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!passwordEncoder.matches(password, user.getPassword()))
            throw new RuntimeException("Invalid password");

        // Basit JWT token üretimi (örnek)
        return Jwts.builder()
                .setSubject(user.getId().toString())
                .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes()))
                .compact();
    }

    private final String jwtSecret = "supersecretkeysupersecretkey123456"; // config’de sakla

    public User getUserFromToken(String tokenHeader) {
        try {
            String token = tokenHeader.replace("Bearer ", "");
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(jwtSecret.getBytes())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            UUID userId = UUID.fromString(claims.getSubject());
            return userRepository.findById(userId).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }
}


