package com.lorongtea.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class PingController {
    @GetMapping("/api/v1/ping")
    ApiResponse<Ping> ping() {
        return new ApiResponse<>(new Ping("pong"));
    }

    record Ping(String status) {
    }
}
