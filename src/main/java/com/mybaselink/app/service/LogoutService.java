package com.mybaselink.app.service;

import com.mybaselink.app.repository.JwtTokenRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * 🧹 LogoutService
 *
 * ✅ JWT 토큰 관련 정리 작업 (만료된 토큰 삭제, 로그아웃 처리 등)
 */
@Service
public class LogoutService {

    private final JwtTokenRepository jwtTokenRepository;

    public LogoutService(JwtTokenRepository jwtTokenRepository) {
        this.jwtTokenRepository = jwtTokenRepository;
    }

    /**
     * 🔹 만료된 토큰 자동 정리 (스케줄러에서 주기적으로 호출 가능)
     */
    public long cleanExpiredTokens() {
        return jwtTokenRepository.deleteAllByExpiresAtBefore(Instant.now());
    }
}
