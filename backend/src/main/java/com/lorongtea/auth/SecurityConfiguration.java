package com.lorongtea.auth;

import java.util.List;

import com.lorongtea.api.ApiException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.HandlerExceptionResolver;

@Configuration(proxyBeanMethods = false)
class SecurityConfiguration {
    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, AccountMapper accounts,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver errors) throws Exception {
        AuthenticationEntryPoint unauthorized = (request, response, failure) -> {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
            errors.resolveException(request, response, null,
                    new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "A valid access token is required"));
        };

        return http
                .csrf(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(access -> access
                        .requestMatchers("/api/v1/staff/**").hasRole("STAFF")
                        .anyRequest().permitAll())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(unauthorized)
                        .accessDeniedHandler((request, response, failure) ->
                                errors.resolveException(request, response, null,
                                        new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN",
                                                "This role cannot access the resource"))))
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .authenticationEntryPoint(unauthorized)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(token -> {
                            AccountIdentity identity = accounts.findActiveById(userId(token.getSubject()));
                            if (identity == null) {
                                throw new InvalidBearerTokenException("Account is unavailable");
                            }
                            return UsernamePasswordAuthenticationToken.authenticated(identity, null,
                                    List.of(new SimpleGrantedAuthority("ROLE_" + identity.role().name())));
                        })))
                .build();
    }

    private long userId(String subject) {
        try {
            long id = Long.parseLong(subject);
            if (id > 0 && Long.toString(id).equals(subject)) {
                return id;
            }
        } catch (NumberFormatException ignored) {
            // An invalid subject is handled as an invalid token.
        }
        throw new InvalidBearerTokenException("Invalid token subject");
    }
}
