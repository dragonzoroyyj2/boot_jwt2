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
import com.mybaselink.app.jwt.JwtUtil;
import com.mybaselink.app.service.AuthService;
import com.mybaselink.app.service.CustomUserDetailsService;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final AuthService authService;
    private final CustomUserDetailsService userDetailsService;

    public AuthController(AuthenticationManager authenticationManager,
                          JwtUtil jwtUtil,
                          AuthService authService,
                          CustomUserDetailsService userDetailsService) {
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.authService = authService;
        this.userDetailsService = userDetailsService;
    }

    // =================================================
    // 로그인
    // =================================================
    @PostMapping("/login")
    public ResponseEntity<Map<String,Object>> login(@RequestBody Map<String,String> request) {
        String username = request.get("username");
        String password = request.get("password");

        try {
            Authentication auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username,password)
            );

            UserDetails userDetails = (UserDetails) auth.getPrincipal();
            String token = jwtUtil.generateToken(userDetails);
            Instant now = Instant.now();
            Instant expiresAt = now.plusMillis(jwtUtil.getExpiration());

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

    // =================================================
    // 로그아웃
    // =================================================
    @PostMapping("/logout")
    public ResponseEntity<Map<String,Object>> logout(HttpServletRequest request) {
        String token = jwtUtil.resolveToken(request);
        if(token != null) authService.logout(token);

        Map<String,Object> response = new HashMap<>();
        response.put("message", "로그아웃 처리 완료");
        return ResponseEntity.ok(response);
    }

    // =================================================
    // 세션 연장 (중복 토큰 방지)
    // =================================================
    @PostMapping("/refresh")
    public ResponseEntity<Map<String,Object>> refreshSession(HttpServletRequest request) {
        String oldToken = jwtUtil.resolveToken(request);
        Map<String,Object> response = new HashMap<>();

        if(oldToken == null || !authService.isTokenValid(oldToken)) {
            response.put("error","세션이 만료되었거나 유효하지 않습니다.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        String username = jwtUtil.getUsername(oldToken);
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        String newToken = jwtUtil.generateToken(userDetails);
        Instant now = Instant.now();
        Instant expiresAt = now.plusMillis(jwtUtil.getExpiration());

        // 🔹 Repository에서 중복 토큰 체크
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

    // =================================================
    // 토큰 검증
    // =================================================
    @GetMapping("/validate")
    public ResponseEntity<Map<String,Object>> validateToken(HttpServletRequest request) {
        String token = jwtUtil.resolveToken(request);
        Map<String,Object> result = new HashMap<>();

        if(token != null && authService.isTokenValid(token)) {
            result.put("valid",true);
            result.put("username", jwtUtil.getUsername(token));
            return ResponseEntity.ok(result);
        } else {
            result.put("valid",false);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(result);
        }
    }
}
