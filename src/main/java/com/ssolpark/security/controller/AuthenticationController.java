package com.ssolpark.security.controller;

import com.ssolpark.security.common.DataApiResponse;
import com.ssolpark.security.dto.auth.JwtRequest;
import com.ssolpark.security.service.AuthenticationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@Slf4j
public class AuthenticationController {

    private final AuthenticationService authenticationService;

    public AuthenticationController(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @PostMapping
    public DataApiResponse login (@RequestBody JwtRequest jwtRequest) {
        return authenticationService.authenticateForJwt(jwtRequest);
    }
}
