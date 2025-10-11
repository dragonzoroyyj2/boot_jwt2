package com.mybaselink.app.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * 🔐 JwtTokenEntity
 *
 * JWT 토큰의 발급, 만료, 로그아웃(무효화) 상태를 DB에 저장하여
 * 세션 관리 및 동시 로그인 제한 기능을 지원하는 엔티티 클래스입니다.
 *
 * ⚙️ 주요 컬럼:
 * - username: 사용자 로그인 ID
 * - token: JWT 문자열
 * - createdAt: DB에 저장된 시각
 * - issuedAt: JWT 발급 시각
 * - expiresAt: 만료 시각
 * - revoked: 강제 무효화 여부
 */
@Entity
@Table(name = "jwt_tokens", schema = "mybaselink")
public class JwtTokenEntity {

    // ============================================================
    // 🔑 기본키 (자동 증가)
    // ============================================================
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ============================================================
    // 👤 사용자명 (username 기반 인증)
    // ============================================================
    @Column(name = "username", nullable = false, length = 50)
    private String username;

    // ============================================================
    // 🔐 JWT 토큰 문자열
    // ============================================================
    @Column(name = "token", nullable = false, length = 1024)
    private String token;

    // ============================================================
    // 🕒 DB 저장 시각 (토큰이 DB에 최초 등록된 시점)
    // ============================================================
    @Column(name = "created_at", columnDefinition = "TIMESTAMPTZ DEFAULT NOW()")
    private Instant createdAt = Instant.now();

    // ============================================================
    // 🕓 JWT 발급 시각 (IssuedAt)
    // ============================================================
    @Column(name = "issued_at")
    private Instant issuedAt;

    // ============================================================
    // ⏳ JWT 만료 시각 (Expiration)
    // ============================================================
    @Column(name = "expires_at")
    private Instant expiresAt;

    // ============================================================
    // 🚫 강제 무효화 여부 (로그아웃, 중복 로그인 방지용)
    // ============================================================
    @Column(name = "revoked", nullable = false)
    private boolean revoked = false;

    // ============================================================
    // 🧩 Getter / Setter
    // ============================================================
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
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
    // 🧾 디버깅 / 로깅용 toString()
    // ============================================================
    @Override
    public String toString() {
        return "JwtTokenEntity{" +
                "id=" + id +
                ", username='" + username + '\'' +
                ", token='" + token + '\'' +
                ", createdAt=" + createdAt +
                ", issuedAt=" + issuedAt +
                ", expiresAt=" + expiresAt +
                ", revoked=" + revoked +
                '}';
    }
}
