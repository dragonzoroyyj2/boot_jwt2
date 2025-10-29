package com.mybaselink.app.security;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Component;
import com.mybaselink.app.entity.JwtTokenEntity;
import com.mybaselink.app.repository.JwtTokenRepository;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 🔐 JwtTokenProvider (DB 연동 + HttpOnly 쿠키 지원)
 *
 * ✅ DB + JWT 동시 검증
 * ✅ roles claim 포함
 * ✅ revokeToken() 으로 DB 토큰 폐기
 * ✅ 쿠키 기반 구조 완전 호환
 */
@Component
public class JwtTokenProvider {

    private final JwtTokenRepository jwtTokenRepository;

    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.expiration}")
    public long accessExpirationMillis;

    public JwtTokenProvider(JwtTokenRepository jwtTokenRepository) {
        this.jwtTokenRepository = jwtTokenRepository;
    }

    /** ✅ Access Token 생성 (roles claim 포함) */
    public String generateAccessToken(String username, Collection<? extends GrantedAuthority> roles) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusMillis(accessExpirationMillis);

        List<String> roleList = roles.stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());

        return Jwts.builder()
                .setSubject(username)
                .claim("roles", roleList)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(expiresAt))
                .signWith(Keys.hmacShaKeyFor(secretKey.getBytes()))
                .compact();
    }

    /** ✅ 토큰 검증 (JWT + DB 검증 포함) */
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(secretKey.getBytes())
                    .build()
                    .parseClaimsJws(token);

            Optional<JwtTokenEntity> entity = jwtTokenRepository.findByToken(token);
            return entity.isPresent()
                    && !entity.get().isRevoked()
                    && entity.get().getExpiresAt().isAfter(Instant.now());
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /** ✅ 사용자명 추출 */
    public String getUsername(String token) {
        return getClaims(token).getSubject();
    }

    /** ✅ roles 추출 */
    public List<String> getRoles(String token) {
        Object roles = getClaims(token).get("roles");
        if (roles instanceof List<?>) {
            return ((List<?>) roles).stream()
                    .map(Object::toString)
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    /** ✅ Claims 파싱 */
    private Claims getClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(secretKey.getBytes())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    /** ✅ 만료일 / 잔여시간 */
    public Date extractExpiration(String token) {
        return getClaims(token).getExpiration();
    }

    public long getRemainingMillis(String token) {
        return extractExpiration(token).getTime() - System.currentTimeMillis();
    }

    /** ✅ Spring Security Authentication 생성 */
    public Authentication getAuthentication(String token) {
        List<GrantedAuthority> authorities = getRoles(token).stream()
                .map(role -> (GrantedAuthority) () -> role)
                .collect(Collectors.toList());
        User principal = new User(getUsername(token), "", authorities);
        return new UsernamePasswordAuthenticationToken(principal, token, authorities);
    }

    /** ✅ DB revoke 처리 */
    public void revokeToken(String token) {
        jwtTokenRepository.findByToken(token).ifPresent(t -> {
            t.setRevoked(true);
            jwtTokenRepository.save(t);
        });
    }

    /** ✅ (옵션) 헤더 기반 추출 – 쿠키 이외 환경 호환용 */
    public String resolveToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (bearer != null && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }
}
