package com.mybaselink.app.controller;

import java.time.Instant;
import java.util.*;

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

    /** ✅ 로그인 (roles 포함 토큰 발급) */
    @PostMapping("/login")
    public ResponseEntity<Map<String,Object>> login(@RequestBody Map<String,String> request) {
        String username = request.get("username");
        String password = request.get("password");

        try {
            Authentication auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password)
            );

            UserDetails userDetails = (UserDetails) auth.getPrincipal();

            // ✅ 변경된 부분 — roles(권한)도 함께 전달
            String token = jwtTokenProvider.generateAccessToken(
                    userDetails.getUsername(),
                    userDetails.getAuthorities()
            );

            Instant now = Instant.now();
            Instant expiresAt = now.plusMillis(jwtTokenProvider.accessExpirationMillis);

            authService.login(userDetails, token, expiresAt);

            Map<String,Object> result = new HashMap<>();
            result.put("token", token);
            result.put("username", username);
            result.put("role", userDetails.getAuthorities());
            result.put("sessionMillis", expiresAt.toEpochMilli());
            result.put("serverTime", now.toEpochMilli());
            result.put("message", "로그인 성공");

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            Map<String,Object> error = new HashMap<>();
            error.put("error", "아이디 또는 비밀번호가 올바르지 않습니다.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
        }
    }

    /** ✅ 로그아웃 */
    @PostMapping("/logout")
    public ResponseEntity<Map<String,Object>> logout(HttpServletRequest request) {
        String token = jwtTokenProvider.resolveToken(request);
        if (token != null) authService.logout(token);

        Map<String,Object> response = new HashMap<>();
        response.put("message", "로그아웃 처리 완료");
        return ResponseEntity.ok(response);
    }

    /** ✅ 세션 연장 (5분 이하만 허용) */
    @PostMapping("/refresh")
    public ResponseEntity<Map<String,Object>> refreshSession(HttpServletRequest request) {
        String oldToken = jwtTokenProvider.resolveToken(request);
        Map<String,Object> response = new HashMap<>();

        if (oldToken == null || !authService.isTokenValid(oldToken)) {
            response.put("error", "세션이 만료되었거나 유효하지 않습니다.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        String username = jwtTokenProvider.getUsername(oldToken);
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);

        // ✅ 남은 시간이 5분 이상이면 새로고침 거부
        long remainingMillis = jwtTokenProvider.getRemainingMillis(oldToken);
        if (remainingMillis > 5 * 60 * 1000) {
            response.put("error", "5분 이상 남아 새로고침 불가");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
        }

        // ✅ 새 토큰 생성 시에도 roles 포함
        String newToken = jwtTokenProvider.generateAccessToken(
                username,
                userDetails.getAuthorities()
        );

        Instant now = Instant.now();
        Instant expiresAt = now.plusMillis(jwtTokenProvider.accessExpirationMillis);

        Optional<JwtTokenEntity> existing = authService.findByToken(newToken);
        if (existing.isPresent()) {
            response.put("error", "세션 연장이 이미 진행 중입니다.");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }

        authService.refreshToken(oldToken, newToken, expiresAt);

        response.put("token", newToken);
        response.put("username", username);
        response.put("role", userDetails.getAuthorities());
        response.put("sessionMillis", expiresAt.toEpochMilli());
        response.put("serverTime", now.toEpochMilli());
        response.put("message", "세션 연장 완료");

        return ResponseEntity.ok(response);
    }

    /** ✅ 토큰 유효성 검증 */
    @GetMapping("/validate")
    public ResponseEntity<Map<String,Object>> validateToken(HttpServletRequest request) {
        String token = jwtTokenProvider.resolveToken(request);
        Map<String,Object> result = new HashMap<>();

        if (token != null && authService.isTokenValid(token)) {
            result.put("valid", true);
            result.put("username", jwtTokenProvider.getUsername(token));
            result.put("roles", jwtTokenProvider.getRoles(token));
            return ResponseEntity.ok(result);
        } else {
            result.put("valid", false);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(result);
        }
    }
}
