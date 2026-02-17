package com.visionstock.config;

import com.visionstock.model.auth.User;
import com.visionstock.model.enums.UserRole;
import com.visionstock.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Locale;

@Configuration
@Profile({"dev", "local"})
public class DataInitializer {

    private static final Logger logger = LoggerFactory.getLogger(DataInitializer.class);

    @Bean
    @ConditionalOnProperty(value = "app.seed.admin.enabled", havingValue = "true")
    CommandLineRunner seedAdminUser(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.seed.admin.nome:}") String nome,
            @Value("${app.seed.admin.email:}") String email,
            @Value("${app.seed.admin.password:}") String password
    ) {
        return args -> {
            String normalizedEmail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
            String sanitizedName = nome == null ? "" : nome.trim();

            if (normalizedEmail.isBlank() || password == null || password.isBlank()) {
                logger.warn("Admin seed skipped: app.seed.admin.email/password must be configured.");
                return;
            }

            if (userRepository.existsByEmailIgnoreCaseAndDeletedAtIsNull(normalizedEmail)) {
                logger.info("Admin seed skipped: user {} already exists.", normalizedEmail);
                return;
            }

            User admin = User.builder()
                    .nome(sanitizedName.isBlank() ? "Admin" : sanitizedName)
                    .email(normalizedEmail)
                    .senhaHash(passwordEncoder.encode(password))
                    .role(UserRole.ADMIN.name())
                    .ativo(true)
                    .build();

            userRepository.save(admin);
            logger.info("Bootstrap ADMIN user created successfully: {}", normalizedEmail);
        };
    }
}
