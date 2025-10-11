package com.mybaselink.app.controller;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import com.mybaselink.app.jwt.JwtUtil;
import com.mybaselink.app.service.AuthService;
import com.mybaselink.app.service.CustomUserDetailsService;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 🔐 AuthController
 *
 * - 로그인 / 로그아웃
 * - JWT 세션 연장
 * - 토큰 검증
 * - 동시에 로그인 제한 / 토큰 취소 처리
 */
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

    // ============================================================
    // 🟢 로그인 처리
    // ============================================================
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        String password = request.get("password");

        try {
            // 1️⃣ Spring Security 인증 수행
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password)
            );

            // 2️⃣ 인증 성공 후 UserDetails 가져오기
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();

            // 3️⃣ JWT 토큰 생성
            String token = jwtUtil.generateToken(userDetails);

            // 4️⃣ DB 저장 및 기존 활성 토큰 취소 (동시 로그인 제한)
            Instant now = Instant.now();
            Instant expiresAt = now.plusMillis(jwtUtil.getExpiration());
            authService.login(userDetails, token, expiresAt);

            // 5️⃣ 응답 데이터 구성
            Map<String, Object> result = new HashMap<>();
            result.put("token", token);
            result.put("username", username);
            result.put("sessionMillis", expiresAt.toEpochMilli());
            result.put("serverTime", now.toEpochMilli());
            result.put("message", "로그인 성공");

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            // 🔴 인증 실패 처리
            Map<String, Object> error = new HashMap<>();
            error.put("error", "아이디 또는 비밀번호가 올바르지 않습니다.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
        }
    }

    // ============================================================
    // 🔴 로그아웃 처리
    // ============================================================
    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(HttpServletRequest request) {
        // 1️⃣ 요청 헤더에서 JWT 토큰 추출
        String token = jwtUtil.resolveToken(request);

        // 2️⃣ 토큰 존재 시 DB에서 revoked 처리
        if (token != null) {
            authService.logout(token);
        }

        // 3️⃣ 응답
        Map<String, Object> response = new HashMap<>();
        response.put("message", "로그아웃 처리 완료");
        return ResponseEntity.ok(response);
    }

    // ============================================================
    // 🔁 세션 연장
    // ============================================================
    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refreshSession(HttpServletRequest request) {
        String token = jwtUtil.resolveToken(request);
        Map<String, Object> response = new HashMap<>();

        // 1️⃣ 토큰 존재 확인 + 유효성 체크
        if (token == null || !authService.isTokenValid(token)) {
            response.put("error", "세션이 만료되었거나 유효하지 않습니다.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        // 2️⃣ 사용자 정보 로딩
        String username = jwtUtil.getUsername(token);
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);

        // 3️⃣ 새로운 토큰 생성
        String newToken = jwtUtil.generateToken(userDetails);

        // 4️⃣ DB 업데이트
        Instant now = Instant.now();
        Instant expiresAt = now.plusMillis(jwtUtil.getExpiration());
        authService.refreshToken(token, newToken, expiresAt);

        // 5️⃣ 응답 구성
        response.put("token", newToken);
        response.put("username", username);
        response.put("sessionMillis", expiresAt.toEpochMilli());
        response.put("serverTime", now.toEpochMilli());
        response.put("message", "세션이 연장되었습니다.");

        return ResponseEntity.ok(response);
    }

    // ============================================================
    // 🟣 토큰 검증
    // ============================================================
    @GetMapping("/validate")
    public ResponseEntity<Map<String, Object>> validateToken(HttpServletRequest request) {
        String token = jwtUtil.resolveToken(request);
        Map<String, Object> result = new HashMap<>();

        // 1️⃣ 토큰 존재 + DB 및 유효성 확인
        if (token != null && authService.isTokenValid(token)) {
            result.put("valid", true);
            result.put("username", jwtUtil.getUsername(token));
            return ResponseEntity.ok(result);
        } else {
            result.put("valid", false);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(result);
        }
    }
}
