package com.mybaselink.app.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.mybaselink.app.jwt.JwtAuthenticationFilter;
import com.mybaselink.app.jwt.CustomLogoutHandler;   // ✅ 추가
import com.mybaselink.app.service.CustomUserDetailsService;

/**
 * 🔐 SecurityConfig - MyNova 통합 인증 설정
 *
 * ✅ 주요 기능
 * 1. JWT 기반 인증 (세션 병행 가능)
 * 2. /auth/** → 인증 관련 API (로그인, 세션연장, 토큰 검증)
 * 3. /api/**  → JWT 인증 필수 (데이터 처리용)
 * 4. 정적 리소스 및 /login, /error 는 모두 접근 허용
 */
@Configuration
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CustomUserDetailsService userDetailsService;
    private final CustomLogoutHandler customLogoutHandler; // ✅ 추가

    // ✅ 생성자에 CustomLogoutHandler 주입 추가
    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          CustomUserDetailsService userDetailsService,
                          CustomLogoutHandler customLogoutHandler) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.userDetailsService = userDetailsService;
        this.customLogoutHandler = customLogoutHandler;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        // ✅ 정적 리소스 허용 경로
        String[] staticResources = {
                "/common/**", "/css/**", "/js/**", "/images/**", "/test_report/**", "/favicon.ico"
        };

        // ✅ 로그인 없이 접근 가능한 공개 엔드포인트
        String[] publicEndpoints = {
                "/", "/login", "/error",
                "/auth/login", "/auth/refresh", "/auth/validate"
        };

        http
            // 🔒 CSRF 비활성화 (JWT 기반일 때 필수)
            .csrf(csrf -> csrf.disable())

            // ✅ 요청별 접근 정책
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(staticResources).permitAll()  // 정적 리소스
                .requestMatchers(publicEndpoints).permitAll()  // 공개 API
                .requestMatchers("/auth/**", "/api/**").authenticated() // 인증 필요
                .requestMatchers("/pages/main/base**").permitAll()
                .requestMatchers("/pages/**").permitAll()
                .anyRequest().denyAll() // 그 외는 접근 차단
            )

            // 🚨 인증 실패 / 권한 부족 시 처리
            .exceptionHandling(exception -> exception
                .authenticationEntryPoint((request, response, authException) -> {
                    String uri = request.getRequestURI();
                    if (uri.startsWith("/api") || uri.startsWith("/auth")) {
                        response.setStatus(401);
                        response.setContentType("application/json;charset=UTF-8");
                        response.getWriter().write("{\"error\":\"인증이 필요합니다.\"}");
                    } else {
                        response.sendRedirect("/login");
                    }
                })
                .accessDeniedPage("/error")
            )

            // ✅ 로그인 폼 설정 (세션 기반 페이지 로그인)
            .formLogin(form -> form
                .loginPage("/login")
                .defaultSuccessUrl("/pages/main/base", true)
                .permitAll()
            )

            // 🚪 로그아웃 설정
            .logout(logout -> logout
                .logoutUrl("/logout")
                .addLogoutHandler(customLogoutHandler) // ✅ JWT 토큰 무효화 반영
                .logoutSuccessUrl("/login?logout")
                .permitAll()
            )

            // 🧩 세션 정책 설정
            // 👉 JWT 기반 API 보호를 위해 STATELESS 권장
            // 👉 단, Thymeleaf 로그인 페이지 사용 시 IF_REQUIRED 도 가능
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
            )

            // ✅ UserDetailsService 등록
            .userDetailsService(userDetailsService);

        // 🧱 JWT 필터 등록 (폼 로그인보다 먼저 실행)
        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // 🔑 인증 매니저 (로그인 시 인증 수행)
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    // 🔐 비밀번호 암호화 방식 (BCrypt)
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
