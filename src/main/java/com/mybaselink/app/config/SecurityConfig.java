package com.mybaselink.app.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;

import com.mybaselink.app.security.CustomLogoutHandler;
import com.mybaselink.app.security.JwtAuthenticationFilter;
import com.mybaselink.app.service.CustomUserDetailsService;

/**
 * 🔐 SecurityConfig — 실전용 혼합형 보안 설정
 * 
 * ✅ formLogin + JWT 기반 fetch 로그인 둘 다 지원
 * ✅ /login 은 세션 기반, /auth/login 은 JWT 기반
 * ✅ Stateless + IF_REQUIRED 병행 구조
 * ✅ Spring Boot 3.5.x / Security 6.x 호환
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CustomUserDetailsService userDetailsService;
    private final CustomLogoutHandler customLogoutHandler;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          CustomUserDetailsService userDetailsService,
                          CustomLogoutHandler customLogoutHandler) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.userDetailsService = userDetailsService;
        this.customLogoutHandler = customLogoutHandler;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        // ✅ 정적 리소스
        String[] staticResources = {
                "/common/**", "/css/**", "/js/**", "/images/**",
                "/favicon/**", "/test_report/**",
                "/favicon.ico", "/apple-icon-*.png", "/android-icon-*.png", "/manifest.json"
        };

        // ✅ 로그인 없이 접근 허용
        String[] publicEndpoints = {
                "/", "/login", "/error",
                "/auth/login", "/auth/refresh", "/auth/me",
                "/pages/login", "/pages/**",
                "/api/krx/**"
        };

        http
            // ✅ CORS (로컬 + 운영 환경 모두 지원)
            .cors(cors -> cors.configurationSource(req -> {
                CorsConfiguration cfg = new CorsConfiguration();
                cfg.setAllowCredentials(true);
                cfg.setAllowedOriginPatterns(List.of(
                        "http://localhost:8080",
                        "http://127.0.0.1:8080",
                        "https://mybaselink.com",
                        "https://www.mybaselink.com"
                ));
                cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
                cfg.setAllowedHeaders(List.of("*"));
                cfg.setExposedHeaders(List.of("Set-Cookie"));
                return cfg;
            }))

            // ✅ CSRF 비활성화 (REST API 사용)
            .csrf(csrf -> csrf.disable())

            // ✅ 접근 제어 정책
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers(staticResources).permitAll()
                    .requestMatchers(publicEndpoints).permitAll()
                    .anyRequest().authenticated()
            )

            // ✅ formLogin (세션 기반 Thymeleaf 로그인)
            .formLogin(form -> form
            	    .loginPage("/login")
            	    .loginProcessingUrl("/login")   // ✅ formLogin은 /login 전용
            	    .defaultSuccessUrl("/pages/main/base", true)
            	    .permitAll()
            	)


            // ✅ JWT + 세션 병행: 세션은 필요할 때만 생성
            .sessionManagement(session ->
                    session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
            )

            // ✅ 로그아웃 (CustomLogoutHandler 사용)
            .logout(logout -> logout
                    .logoutUrl("/logout")
                    .addLogoutHandler(customLogoutHandler)
                    .logoutSuccessHandler((req, res, auth) -> {
                        res.setStatus(200);
                        res.setContentType("application/json;charset=UTF-8");
                        res.getWriter().write("{\"message\":\"로그아웃 성공\"}");
                    })
                    .permitAll()
            )

            // ✅ 사용자 인증 서비스 지정
            .userDetailsService(userDetailsService);

        // ✅ JWT 필터 추가 (폼 로그인보다 먼저)
        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // ✅ 인증 매니저 (AuthController에서 사용)
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    // ✅ 비밀번호 암호화 (BCrypt)
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
