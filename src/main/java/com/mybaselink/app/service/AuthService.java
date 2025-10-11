package com.mybaselink.app.service;

import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.mybaselink.app.entity.JwtTokenEntity;
import com.mybaselink.app.repository.JwtTokenRepository;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * 🔑 AuthService
 *
 * - 로그인 시 JWT 발급 및 DB 저장
 * - 동시 로그인 제한 처리
 * - 로그아웃 시 토큰 취소 처리 (revoked = true)
 * - 토큰 연장 (refresh)
 * - 토큰 유효성 검증
 */
@Service
public class AuthService {

    private final JwtTokenRepository tokenRepository;

    public AuthService(JwtTokenRepository tokenRepository) {
        this.tokenRepository = tokenRepository;
    }

    // ============================================================
    // 🟢 로그인 처리 + DB 저장 + 동시 로그인 제한
    // ============================================================
    @Transactional
    public void login(UserDetails userDetails, String token, Instant expiresAt) {

        String username = userDetails.getUsername();

        // 1️⃣ 기존 활성 토큰 확인
        List<JwtTokenEntity> activeTokens = tokenRepository.findByUsernameAndRevokedFalse(username);

        // 2️⃣ 기존 토큰 모두 취소 (동시 로그인 제한)
        for (JwtTokenEntity t : activeTokens) {
            t.setRevoked(true);
            tokenRepository.save(t);
        }

        // 3️⃣ 새 토큰 DB 저장
        JwtTokenEntity jwtToken = new JwtTokenEntity();
        jwtToken.setUsername(username);
        jwtToken.setToken(token);
        jwtToken.setIssuedAt(Instant.now());
        jwtToken.setExpiresAt(expiresAt);
        jwtToken.setRevoked(false);

        tokenRepository.save(jwtToken);
    }

    // ============================================================
    // 🔴 로그아웃 처리
    // ============================================================
    @Transactional
    public void logout(String token) {
        tokenRepository.findByToken(token).ifPresent(jwt -> {
            jwt.setRevoked(true);
            tokenRepository.save(jwt);
        });
    }

    // ============================================================
    // 🔁 토큰 연장
    // ============================================================
    @Transactional
    public void refreshToken(String oldToken, String newToken, Instant newExpiresAt) {
        tokenRepository.findByToken(oldToken).ifPresent(jwt -> {
            // 기존 토큰 취소
            jwt.setRevoked(true);
            tokenRepository.save(jwt);

            // 새 토큰 저장
            JwtTokenEntity newJwt = new JwtTokenEntity();
            newJwt.setUsername(jwt.getUsername());
            newJwt.setToken(newToken);
            newJwt.setIssuedAt(Instant.now());
            newJwt.setExpiresAt(newExpiresAt);
            newJwt.setRevoked(false);

            tokenRepository.save(newJwt);
        });
    }

    // ============================================================
    // 🟣 토큰 유효성 검증
    // ============================================================
    @Transactional(readOnly = true)
    public boolean isTokenValid(String token) {
        return tokenRepository.findByToken(token)
                .map(jwt -> !jwt.isRevoked() && jwt.getExpiresAt().isAfter(Instant.now()))
                .orElse(false);
    }
}
