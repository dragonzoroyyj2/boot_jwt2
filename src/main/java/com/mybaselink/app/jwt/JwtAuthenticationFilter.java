package com.mybaselink.app.jwt;

import java.io.IOException;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.mybaselink.app.service.AuthService;
import com.mybaselink.app.service.CustomUserDetailsService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 🔒 JwtAuthenticationFilter
 * - 모든 HTTP 요청에 대해 JWT 토큰 검증
 * - revoked 토큰 차단
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final CustomUserDetailsService userDetailsService;
    private final AuthService authService;

    public JwtAuthenticationFilter(JwtUtil jwtUtil,
                                   CustomUserDetailsService userDetailsService,
                                   AuthService authService) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
        this.authService = authService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String uri = request.getRequestURI();

        // 1️⃣ 인증 예외 경로는 필터링하지 않음
        if (uri.startsWith("/auth/login") || uri.startsWith("/auth/refresh") || uri.startsWith("/auth/validate") || uri.startsWith("/login")) {
            filterChain.doFilter(request, response);
            return;
        }

        // 2️⃣ Authorization 헤더에서 JWT 추출
        String token = jwtUtil.resolveToken(request);

        // 3️⃣ 토큰 유효성 및 revoked 체크
        if (token != null && authService.isTokenValid(token)) {
            String username = jwtUtil.getUsername(token);
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);

            // 4️⃣ 인증 객체 생성 및 SecurityContext 설정
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        // 5️⃣ 다음 필터 체인 호출
        filterChain.doFilter(request, response);
    }
}
