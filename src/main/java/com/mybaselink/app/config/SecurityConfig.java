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

import com.mybaselink.app.jwt.CustomLogoutHandler;   
import com.mybaselink.app.security.jwt.JwtAuthenticationFilter;
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

        String[] staticResources = {
                "/common/**", "/css/**", "/js/**", "/images/**","/favicon/**", "/test_report/**", "/favicon.ico", "/apple-icon-*.png", "/android-icon-*.png", "/manifest.json"
        };

        String[] publicEndpoints = {
                "/", "/login", "/error",
                "/auth/login", "/auth/refresh", "/auth/validate",
                "/api/krx/**" ,"/pages/stock/**"
        };

        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(staticResources).permitAll()
                .requestMatchers(publicEndpoints).permitAll()
                .requestMatchers("/auth/**", "/api/**").authenticated()
                .requestMatchers("/pages/main/base**").permitAll()
                .requestMatchers("/pages/**").permitAll()
                .anyRequest().denyAll()
            )
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
            .formLogin(form -> form
                .loginPage("/login")
                .defaultSuccessUrl("/pages/main/base", true)
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .addLogoutHandler(customLogoutHandler)
                .logoutSuccessUrl("/login?logout")
                .permitAll()
            )
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
            )
            .userDetailsService(userDetailsService);

        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
