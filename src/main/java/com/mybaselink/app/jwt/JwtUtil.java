package com.mybaselink.app.jwt;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.security.core.userdetails.UserDetails;
import java.security.Key;
import java.util.Date;

/**
 * 🔐 JwtUtil
 *
 * JWT 토큰 생성, 검증, 만료 시간 계산, HTTP 요청에서 토큰 추출
 */
@Component
public class JwtUtil {

    /** JWT 비밀키 */
    @Value("${jwt.secret}")
    private String secretKey;

    /** JWT 만료 시간 (ms 단위) */
    @Value("${jwt.expiration}")
    private long expiration;

    // ============================================================
    // 🔹 JWT 생성 (UserDetails 기준)
    // ============================================================
    public String generateToken(UserDetails userDetails) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expiration);

        Key key = Keys.hmacShaKeyFor(secretKey.getBytes());

        return Jwts.builder()
                .setSubject(userDetails.getUsername())
                .setIssuedAt(now)
                .setExpiration(expiry)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    // ============================================================
    // 🔹 토큰에서 username 추출
    // ============================================================
    public String getUsername(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(secretKey.getBytes())
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getSubject();
    }

    // ============================================================
    // 🔹 토큰 유효성 검증
    // ============================================================
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                .setSigningKey(secretKey.getBytes())
                .build()
                .parseClaimsJws(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    // ============================================================
    // 🔹 HTTP 요청에서 Authorization 헤더의 Bearer 토큰 추출
    // ============================================================
    public String resolveToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (bearer != null && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }

    // ============================================================
    // 🔹 JWT 만료 시간 반환 (ms 단위)
    // ============================================================
    public long getExpiration() {
        return expiration;
    }
}
