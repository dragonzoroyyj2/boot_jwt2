package com.mybaselink.app.service;

import com.mybaselink.app.entity.JwtTokenEntity;
import com.mybaselink.app.entity.LoginUserEntity;
import com.mybaselink.app.repository.LoginUserRepository;
import com.mybaselink.app.security.JwtTokenProvider;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
/*
 LoginUserRepository   ←   LoginUserService   ←  CustomUserDetailsService
                                  │
                                  ▼
                           AuthService
                             ├─ JwtService (토큰 관리)
                             └─ JwtTokenProvider (JWT 발급/검증)
                                  │
                                  ▼
                        JwtAuthenticationFilter
                                  │
                                  ▼
                           CustomLogoutHandler
 */

/**
 * 🔐 AuthService (최신 통합 버전)
 *
 * ✅ 역할:
 *  - 사용자 인증
 *  - JWT 생성 및 저장 (JwtService 위임)
 *  - Refresh, Logout, Profile 처리
 */
@Service
@Transactional
public class AuthService implements UserDetailsService {

    private final LoginUserRepository loginUserRepository;
    private final JwtService jwtService;            // ✅ 토큰 관리 전담
    private final JwtTokenProvider jwtTokenProvider;

    public AuthService(LoginUserRepository loginUserRepository,
                       JwtService jwtService,
                       JwtTokenProvider jwtTokenProvider) {
        this.loginUserRepository = loginUserRepository;
        this.jwtService = jwtService;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    // ============================================================
    // ✅ 사용자 인증 정보 로드 (Spring Security 표준)
    // ============================================================

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        LoginUserEntity user = loginUserRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다: " + username));

        return User.builder()
                .username(user.getUsername())
                .password(user.getPassword())
                .roles(user.getRole().replace("ROLE_", "")) // ROLE_ 접두어 제거 후 등록
                .build();
    }

    // ============================================================
    // ✅ 로그인 시 JWT 저장
    // ============================================================

    public void login(UserDetails userDetails, String token, Instant expiresAt) {
        // ✅ 기존 사용자 토큰 전부 폐기 후 새로 저장
        jwtService.getActiveTokens(userDetails.getUsername())
                .forEach(t -> jwtService.revokeToken(t.getToken()));

        JwtTokenEntity jwtEntity = new JwtTokenEntity();
        jwtEntity.setUsername(userDetails.getUsername());
        jwtEntity.setToken(token);
        jwtEntity.setIssuedAt(Instant.now());
        jwtEntity.setExpiresAt(expiresAt);
        jwtEntity.setRevoked(false);
        jwtService.saveToken(jwtEntity);
    }

    // ============================================================
    // ✅ 토큰 검증
    // ============================================================

    public boolean isTokenValid(String token) {
        if (token == null || token.isEmpty()) return false;

        Optional<JwtTokenEntity> tokenOpt = jwtService.getToken(token);
        if (tokenOpt.isEmpty()) return false;

        JwtTokenEntity dbToken = tokenOpt.get();
        boolean expired = dbToken.getExpiresAt() != null && dbToken.getExpiresAt().isBefore(Instant.now());
        boolean revoked = dbToken.isRevoked();

        return !expired && !revoked && jwtTokenProvider.validateToken(token);
    }

    // ============================================================
    // ✅ 토큰 갱신
    // ============================================================

    public void refreshToken(String oldToken, String newToken, Instant newExpiresAt) {
        jwtService.revokeToken(oldToken);

        String username = jwtTokenProvider.getUsername(oldToken);

        JwtTokenEntity newEntity = new JwtTokenEntity();
        newEntity.setUsername(username);
        newEntity.setToken(newToken);
        newEntity.setIssuedAt(Instant.now());
        newEntity.setExpiresAt(newExpiresAt);
        newEntity.setRevoked(false);

        jwtService.saveToken(newEntity);
    }

    // ============================================================
    // ✅ 현재 로그인된 사용자 조회 (/auth/me)
    // ============================================================

    public Optional<LoginUserEntity> getCurrentUser(String username) {
        return loginUserRepository.findByUsername(username);
    }
}
