package com.mybaselink.app.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * 🔐 JwtTokenEntity (안정형 완전판)
 *
 * ✅ JWT 토큰 관리용 엔티티
 * - 토큰 발급, 만료, 무효화 상태 추적
 * - 중복 로그인 방지 / 세션 연장 시 갱신
 */
@Entity
@Table(
    name = "jwt_tokens",
    schema = "mybaselink",
    uniqueConstraints = {
        @UniqueConstraint(name = "unique_token", columnNames = "token")
    }
)
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
    // 🔐 JWT 토큰 문자열 (고유)
    // ============================================================
    @Column(name = "token", nullable = false, length = 1024, unique = true)
    private String token;

    // ============================================================
    // 🕒 DB 저장 시각
    // ============================================================
    @Column(name = "created_at", columnDefinition = "TIMESTAMPTZ DEFAULT NOW()")
    private Instant createdAt;

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
    // 🚫 강제 무효화 여부 (로그아웃 / 중복 로그인 방지용)
    // ============================================================
    @Column(name = "revoked", nullable = false)
    private boolean revoked = false;

    // ============================================================
    // 🧩 Getter / Setter
    // ============================================================
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getIssuedAt() { return issuedAt; }
    public void setIssuedAt(Instant issuedAt) { this.issuedAt = issuedAt; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public boolean isRevoked() { return revoked; }
    public void setRevoked(boolean revoked) { this.revoked = revoked; }

    // ============================================================
    // 🧭 엔티티 생성 전 자동 시간 세팅
    // ============================================================
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
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
