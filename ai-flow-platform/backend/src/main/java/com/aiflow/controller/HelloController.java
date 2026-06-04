package com.aiflow.controller;

import com.aiflow.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:5173")
public class HelloController {

    private final JwtUtil jwtUtil;

    public HelloController(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @GetMapping("/hello")
    public ResponseEntity<?> hello() {
        return ResponseEntity.ok(Map.of("message", "Hello World! Server is running."));
    }

    @GetMapping("/hello-auth")
    public ResponseEntity<?> helloAuth(HttpServletRequest request, Authentication authentication) {
        String authHeader = request.getHeader("Authorization");
        String username = "";

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            username = jwtUtil.getUsernameFromToken(token);
        }

        return ResponseEntity.ok(Map.of("message", "Hello " + username + ", you are authenticated."));
    }
}
