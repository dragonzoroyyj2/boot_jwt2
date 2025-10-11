package com.mybaselink.app.jwt;

import com.mybaselink.app.entity.JwtTokenEntity;
import com.mybaselink.app.repository.JwtTokenRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 🚪 CustomLogoutHandler
 *
 * ✅ 로그아웃 시 JWT 토큰을 DB에서 "비활성화(revoked=true)" 처리
 * ✅ 세션 종료 외에도 JWT 토큰을 무효화하여 재사용 방지
 */
@Component
public class CustomLogoutHandler implements LogoutHandler {

    private final JwtUtil jwtUtil;
    private final JwtTokenRepository jwtTokenRepository;

    public CustomLogoutHandler(JwtUtil jwtUtil, JwtTokenRepository jwtTokenRepository) {
        this.jwtUtil = jwtUtil;
        this.jwtTokenRepository = jwtTokenRepository;
    }

    @Override
    public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {

        // 1️⃣ 요청 헤더에서 토큰 추출
        String token = jwtUtil.resolveToken(request);
        if (token == null) return;

        // 2️⃣ DB에 저장된 해당 토큰을 조회
        Optional<JwtTokenEntity> tokenEntityOpt = jwtTokenRepository.findByToken(token);

        // 3️⃣ 토큰이 존재하면 revoked = true 로 업데이트
        tokenEntityOpt.ifPresent(entity -> {
            entity.setRevoked(true);
            jwtTokenRepository.save(entity);
        });
    }
}
