package com.mybaselink.app.security;

import com.mybaselink.app.repository.JwtTokenRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 🚪 CustomLogoutHandler (HttpOnly 쿠키 기반)
 *
 * ✅ 기능:
 *  - 로그아웃 시 쿠키(jwt) 제거
 *  - DB에 저장된 JWT를 revoked 처리
 *  - Spring Security logoutFilter 에 의해 호출됨
 */
@Component
public class CustomLogoutHandler implements LogoutHandler {

    private final JwtTokenRepository jwtTokenRepository;

    public CustomLogoutHandler(JwtTokenRepository jwtTokenRepository) {
        this.jwtTokenRepository = jwtTokenRepository;
    }

    @Override
    public void logout(HttpServletRequest request,
                       HttpServletResponse response,
                       Authentication authentication) {

        // ✅ 1. 쿠키에서 JWT 추출
        String token = extractTokenFromCookie(request);

        if (token != null) {
            // ✅ 2. DB에서 해당 토큰 revoke 처리
            Optional.ofNullable(jwtTokenRepository.findByToken(token))
                    .ifPresent(opt -> opt.ifPresent(t -> {
                        t.setRevoked(true);
                        jwtTokenRepository.save(t);
                    }));

            // ✅ 3. 클라이언트 쿠키 삭제 (jwt 쿠키 무효화)
            expireJwtCookie(response);
        }
    }

    /** ✅ 쿠키에서 jwt 토큰 추출 */
    private String extractTokenFromCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        for (Cookie cookie : request.getCookies()) {
            if ("jwt".equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    /** ✅ jwt 쿠키 삭제 처리 */
    private void expireJwtCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie("jwt", null);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge(0);
        // Secure 설정: 로컬은 false, 운영(https)은 SecurityConfig에서 자동처리됨
        cookie.setSecure(false);
        response.addCookie(cookie);
    }
}
