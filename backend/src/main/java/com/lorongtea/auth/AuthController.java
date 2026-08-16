package com.lorongtea.auth;

import com.lorongtea.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class AuthController {
    private final AuthService auth;

    AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/api/v1/auth/login")
    ResponseEntity<ApiResponse<AuthService.LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new ApiResponse<>(auth.login(request.username(), request.password())));
    }

    record LoginRequest(@NotBlank @Size(max = 64) String username,
                        @NotBlank @Size(max = AuthService.MAX_PASSWORD_BYTES) String password) {
    }
}
