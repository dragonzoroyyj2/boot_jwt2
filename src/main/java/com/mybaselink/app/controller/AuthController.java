package com.mybaselink.app.controller;

import com.mybaselink.app.security.JwtTokenProvider;
import com.mybaselink.app.service.AuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

/**
 * 🔐 AuthController – HttpOnly 쿠키 기반 JWT 인증 컨트롤러 (Spring Boot 3.5.x)
 *
 * ✅ 특징:
 * - Spring Security 6.x 호환 (formLogin 비활성 + fetch 기반)
 * - 로컬(HTTP) / 운영(HTTPS) 자동 분기
 * - AuthenticationManager 순환참조 문제 해결
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthenticationConfiguration authenticationConfiguration;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthService authService;

    public AuthController(AuthenticationConfiguration authenticationConfiguration,
                          JwtTokenProvider jwtTokenProvider,
                          AuthService authService) {
        this.authenticationConfiguration = authenticationConfiguration;
        this.jwtTokenProvider = jwtTokenProvider;
        this.authService = authService;
    }

    // 요청 DTO
    private static class LoginRequest {
        public String username;
        public String password;
    }

    /**
     * ✅ 로그인 처리 (POST /auth/login)
     * - AuthenticationManager를 직접 획득 (순환참조 방지)
     * - 성공 시 JWT를 HttpOnly 쿠키에 저장
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest requestBody,
                                   HttpServletResponse response,
                                   HttpServletRequest request) {
        try {
            AuthenticationManager authenticationManager =
                    authenticationConfiguration.getAuthenticationManager();

            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            requestBody.username,
                            requestBody.password
                    )
            );

            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            String username = userDetails.getUsername();

            // JWT 생성 및 DB 저장
            String token = jwtTokenProvider.generateAccessToken(username, userDetails.getAuthorities());
            Instant expiresAt = Instant.now().plusMillis(jwtTokenProvider.accessExpirationMillis);
            authService.login(userDetails, token, expiresAt);

            // HttpOnly 쿠키 설정 (로컬: secure=false, 운영: true)
            ResponseCookie cookie = ResponseCookie.from("jwt", token)
                    .httpOnly(true)
                    .secure(request.isSecure()) // HTTPS면 true
                    .sameSite(request.isSecure() ? "Strict" : "Lax")
                    .path("/")
                    .maxAge(Duration.ofMillis(jwtTokenProvider.accessExpirationMillis))
                    .build();
            response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

            return ResponseEntity.ok(Map.of(
                    "message", "로그인 성공",
                    "username", username,
                    "sessionMillis", jwtTokenProvider.accessExpirationMillis,
                    "serverTime", System.currentTimeMillis()
            ));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "로그인 실패: " + e.getMessage()));
        }
    }

    /**
     * ✅ 토큰 갱신 (POST /auth/refresh)
     * - 쿠키에 저장된 JWT 검증 후 새 토큰 발급
     */
    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(HttpServletRequest request,
                                          HttpServletResponse response) {
        String oldToken = extractCookieToken(request, "jwt");
        if (oldToken == null || !authService.isTokenValid(oldToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "만료되었거나 유효하지 않은 토큰입니다."));
        }

        try {
            String username = jwtTokenProvider.getUsername(oldToken);
            UserDetails userDetails = authService.loadUserByUsername(username);

            String newToken = jwtTokenProvider.generateAccessToken(username, userDetails.getAuthorities());
            Instant newExpiresAt = Instant.now().plusMillis(jwtTokenProvider.accessExpirationMillis);

            authService.refreshToken(oldToken, newToken, newExpiresAt);
            addJwtCookie(response, newToken, request.isSecure());

            return ResponseEntity.ok(Map.of(
                    "message", "토큰 갱신 성공",
                    "sessionMillis", jwtTokenProvider.accessExpirationMillis,
                    "serverTime", System.currentTimeMillis()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "토큰 갱신 실패: " + e.getMessage()));
        }
    }

    /**
     * ✅ 로그아웃 (POST /auth/logout)
     * - CustomLogoutHandler에서 DB revoke 및 쿠키 삭제 처리
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logout() {
        return ResponseEntity.ok(Map.of("message", "로그아웃 완료"));
    }

    /**
     * ✅ 현재 로그인된 사용자 조회 (GET /auth/me)
     */
    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "인증되지 않은 사용자입니다."));
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof UserDetails userDetails) {
            return ResponseEntity.ok(Map.of(
                    "name", userDetails.getUsername(),
                    "email", userDetails.getUsername() + "@mybaselink.com",
                    "role", userDetails.getAuthorities().stream().findFirst().map(Object::toString).orElse("ROLE_USER")
            ));
        }

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "사용자 정보를 가져올 수 없습니다."));
    }

    // ========================================
    // 내부 유틸
    // ========================================
    private void addJwtCookie(HttpServletResponse response, String token, boolean secure) {
        ResponseCookie cookie = ResponseCookie.from("jwt", token)
                .httpOnly(true)
                .secure(secure)
                .sameSite(secure ? "Strict" : "Lax")
                .path("/")
                .maxAge(Duration.ofMillis(jwtTokenProvider.accessExpirationMillis))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private String extractCookieToken(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return null;
        Optional<Cookie> cookieOpt = Arrays.stream(request.getCookies())
                .filter(c -> name.equals(c.getName()))
                .findFirst();
        return cookieOpt.map(Cookie::getValue).orElse(null);
    }
}
