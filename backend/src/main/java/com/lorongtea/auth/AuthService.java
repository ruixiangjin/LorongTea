package com.lorongtea.auth;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.lorongtea.api.ApiException;
import com.lorongtea.auth.AccountIdentity.Role;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
class AuthService {
    private static final Duration TOKEN_LIFETIME = Duration.ofMinutes(30);
    static final int MAX_PASSWORD_BYTES = 72;

    private final AccountMapper accounts;
    private final PasswordEncoder passwords;
    private final JwtEncoder tokens;
    private final String dummyHash;

    AuthService(AccountMapper accounts, PasswordEncoder passwords, JwtEncoder tokens) {
        this.accounts = accounts;
        this.passwords = passwords;
        this.tokens = tokens;
        this.dummyHash = passwords.encode(UUID.randomUUID().toString());
    }

    LoginResponse login(String username, String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Invalid request");
        }

        AccountMapper.Account account = accounts.findActiveByUsername(username);
        String hash = account == null ? dummyHash : account.passwordHash();
        if (!passwords.matches(password, hash) || account == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid username or password");
        }

        Instant now = Instant.now();
        String userId = Long.toString(account.id());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(JwtConfiguration.ISSUER)
                .audience(List.of(JwtConfiguration.AUDIENCE))
                .subject(userId)
                .issuedAt(now)
                .expiresAt(now.plus(TOKEN_LIFETIME))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = tokens.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new LoginResponse(token, TOKEN_LIFETIME.toSeconds(), account.role(), userId);
    }

    record LoginResponse(String token, long expiresIn, Role role, String userId) {
    }
}
