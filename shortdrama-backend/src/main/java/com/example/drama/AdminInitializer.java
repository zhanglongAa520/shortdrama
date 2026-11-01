package com.example.drama;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(20)
public class AdminInitializer implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(AdminInitializer.class);
    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final String adminPassword;

    public AdminInitializer(AppUserRepository users, PasswordEncoder passwordEncoder,
                            @Value("${app.admin-password:}") String adminPassword) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.adminPassword = adminPassword;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (users.existsByUsernameIgnoreCase("admin")) return;
        if (adminPassword == null || adminPassword.isBlank()) {
            log.warn("Admin account was not created: set YINGKE_ADMIN_PASSWORD before first startup");
            return;
        }
        if (adminPassword.length() < 8 || adminPassword.length() > 72) {
            throw new IllegalStateException("YINGKE_ADMIN_PASSWORD must contain 8 to 72 characters");
        }
        AppUser admin = new AppUser();
        admin.setUsername("admin");
        admin.setNickname("管理员");
        admin.setPasswordHash(passwordEncoder.encode(adminPassword));
        admin.setRole(UserRole.ADMIN);
        users.save(admin);
        log.info("Initial admin account created");
    }
}
