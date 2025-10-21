package com.mybaselink.app.controller;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import com.mybaselink.app.entity.JwtTokenEntity;
import com.mybaselink.app.security.jwt.JwtTokenProvider;
import com.mybaselink.app.service.AuthService;
import com.mybaselink.app.service.CustomUserDetailsService;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthService authService;
    private final CustomUserDetailsService userDetailsService;

    public AuthController(AuthenticationManager authenticationManager,
                          JwtTokenProvider jwtTokenProvider,
                          AuthService authService,
                          CustomUserDetailsService userDetailsService) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenProvider = jwtTokenProvider;
        this.authService = authService;
        this.userDetailsService = userDetailsService;
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String,Object>> login(@RequestBody Map<String,String> request) {
        String username = request.get("username");
        String password = request.get("password");

        try {
            Authentication auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username,password)
            );

            UserDetails userDetails = (UserDetails) auth.getPrincipal();
            String token = jwtTokenProvider.generateAccessToken(userDetails.getUsername());
            Instant now = Instant.now();
            Instant expiresAt = now.plusMillis(jwtTokenProvider.accessExpirationMillis);

            authService.login(userDetails, token, expiresAt);

            Map<String,Object> result = new HashMap<>();
            result.put("token", token);
            result.put("username", username);
            result.put("sessionMillis", expiresAt.toEpochMilli());
            result.put("serverTime", now.toEpochMilli());
            result.put("message", "로그인 성공");

            return ResponseEntity.ok(result);

        } catch(Exception e) {
            Map<String,Object> error = new HashMap<>();
            error.put("error", "아이디 또는 비밀번호가 올바르지 않습니다.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String,Object>> logout(HttpServletRequest request) {
        String token = jwtTokenProvider.resolveToken(request);
        if(token != null) authService.logout(token);

        Map<String,Object> response = new HashMap<>();
        response.put("message", "로그아웃 처리 완료");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<Map<String,Object>> refreshSession(HttpServletRequest request) {
        String oldToken = jwtTokenProvider.resolveToken(request);
        Map<String,Object> response = new HashMap<>();

        if(oldToken == null || !authService.isTokenValid(oldToken)) {
            response.put("error","세션이 만료되었거나 유효하지 않습니다.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        String username = jwtTokenProvider.getUsername(oldToken);
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        String newToken = jwtTokenProvider.generateAccessToken(username);
        Instant now = Instant.now();
        Instant expiresAt = now.plusMillis(jwtTokenProvider.accessExpirationMillis);

        Optional<JwtTokenEntity> existing = authService.findByToken(newToken);
        if(existing.isPresent()) {
            response.put("error","세션 연장이 이미 진행중입니다.");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }

        authService.refreshToken(oldToken, newToken, expiresAt);

        response.put("token", newToken);
        response.put("username", username);
        response.put("sessionMillis", expiresAt.toEpochMilli());
        response.put("serverTime", now.toEpochMilli());
        response.put("message","세션 연장 완료");

        return ResponseEntity.ok(response);
    }

    @GetMapping("/validate")
    public ResponseEntity<Map<String,Object>> validateToken(HttpServletRequest request) {
        String token = jwtTokenProvider.resolveToken(request);
        Map<String,Object> result = new HashMap<>();

        if(token != null && authService.isTokenValid(token)) {
            result.put("valid",true);
            result.put("username", jwtTokenProvider.getUsername(token));
            return ResponseEntity.ok(result);
        } else {
            result.put("valid",false);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(result);
        }
    }
}

