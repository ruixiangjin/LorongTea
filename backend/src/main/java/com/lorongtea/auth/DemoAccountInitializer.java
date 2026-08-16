package com.lorongtea.auth;

import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("local")
@Order(1)
class DemoAccountInitializer implements ApplicationRunner {
    private final JdbcClient jdbc;
    private final PasswordEncoder passwords;
    private final String password;

    DemoAccountInitializer(JdbcClient jdbc, PasswordEncoder passwords,
            @Value("${LORONG_DEMO_PASSWORD:}") String password) {
        this.jdbc = jdbc;
        this.passwords = passwords;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (password.isEmpty()) {
            return;
        }
        if (password.isBlank() || password.getBytes(StandardCharsets.UTF_8).length > AuthService.MAX_PASSWORD_BYTES) {
            throw new IllegalStateException("LORONG_DEMO_PASSWORD must be non-blank and at most 72 UTF-8 bytes");
        }

        jdbc.sql("""
                INSERT INTO app_user (username, password_hash, role)
                VALUES ('demo-customer', :hash, 'CUSTOMER')
                ON DUPLICATE KEY UPDATE id = app_user.id
                """).param("hash", passwords.encode(password)).update();
        addStaff("demo-bb-staff", "bukit-bintang");
        addStaff("demo-bb-staff-2", "bukit-bintang");
        addStaff("demo-pj-staff", "petaling-jaya");
    }

    private void addStaff(String username, String storeCode) {
        jdbc.sql("""
                INSERT INTO app_user (username, password_hash, role, store_id)
                SELECT :username, :hash, 'STAFF', s.id
                FROM store s WHERE s.code = :storeCode
                ON DUPLICATE KEY UPDATE id = app_user.id
                """).param("username", username).param("hash", passwords.encode(password))
                .param("storeCode", storeCode).update();
    }
}
