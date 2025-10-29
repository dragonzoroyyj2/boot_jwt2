package com.mybaselink.app.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * 🔒 JwtTokenEntity
 *
 * JWT 토큰 DB 관리 엔티티
 * ---------------------------------------------
 * ✅ DB 테이블명: jwt_tokens
 * ✅ 기능:
 *   - 발급된 JWT 토큰 저장
 *   - 만료일(expiration) 및 폐기 여부(revoked) 관리
 *   - 사용자 단위 조회/폐기 가능
 */
@Entity
@Table(name = "jwt_tokens",
       indexes = {
           @Index(name = "idx_jwt_token_username", columnList = "username"),
           @Index(name = "idx_jwt_token_token", columnList = "token")
       })
public class JwtTokenEntity {

    /** 기본 키 (자동 증가) */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 토큰 문자열 (JWT 본문) */
    @Column(nullable = false, unique = true, length = 512)
    private String token;

    /** 사용자명 (LoginUserEntity.username) */
    @Column(nullable = false, length = 100)
    private String username;

    /** 발급 시각 */
    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    /** 만료 시각 */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** 폐기 여부 (로그아웃/재로그인 등) */
    @Column(nullable = false)
    private boolean revoked = false;

    // ============================================================
    // ✅ 기본 생성자
    // ============================================================

    public JwtTokenEntity() {}

    // ============================================================
    // ✅ Getter / Setter
    // ============================================================

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public void setIssuedAt(Instant issuedAt) {
        this.issuedAt = issuedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public boolean isRevoked() {
        return revoked;
    }

    public void setRevoked(boolean revoked) {
        this.revoked = revoked;
    }

    // ============================================================
    // ✅ 편의 메서드
    // ============================================================

    /** 토큰이 만료되었는지 여부 */
    @Transient
    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(Instant.now());
    }

    /** 토큰이 사용 가능한지 여부 */
    @Transient
    public boolean isActive() {
        return !revoked && !isExpired();
    }
}
