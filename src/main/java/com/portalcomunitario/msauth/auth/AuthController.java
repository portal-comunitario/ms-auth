package com.portalcomunitario.msauth.auth;

import com.portalcomunitario.msauth.user.User;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/google")
    public ResponseEntity<AuthResponse> google(@RequestBody GoogleAuthRequest request) {
        String idToken = request != null ? request.idToken() : null;
        AuthService.AuthResult result = authService.authenticate(idToken);

        User user = result.user();
        AuthResponse body = new AuthResponse(
                result.token(),
                new UserDto(user.getEmail(), user.getName(), user.getRole().name(), user.getTenantId()));
        return ResponseEntity.ok(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleInvalidToken(IllegalArgumentException ex) {
        String message = ex.getMessage() != null ? ex.getMessage() : "Token inválido";
        return ResponseEntity.status(401).body(Map.of("error", message));
    }

    public record GoogleAuthRequest(String idToken) {}
    public record AuthResponse(String token, UserDto user) {}
    public record UserDto(String email, String name, String role, String tenantId) {}
}
