package com.mybaselink.app.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * 🔐 사용자 엔티티
 *
 * - id: PK
 * - username: 로그인 ID
 * - password: BCrypt 암호화 비밀번호
 * - fullName: 실제 이름
 * - email: 이메일
 * - phone: 전화번호
 * - role: 권한 (USER, ADMIN 등)
 * - status: 계정 상태 (active, inactive)
 * - lastLogin: 마지막 로그인 시각
 * - createdAt: 생성일
 * - updatedAt: 수정일
 */
@Entity
@Table(name = "users")
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // BIGSERIAL PK

    @Column(name = "username", nullable = false, unique = true, length = 50)
    private String username; // 로그인 ID

    @Column(name = "password", nullable = false, length = 100)
    private String password; // BCrypt 암호화

    @Column(name = "full_name", length = 100)
    private String fullName; // 실제 이름

    @Column(name = "email", length = 100, unique = true)
    private String email;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "role", nullable = false, length = 20)
    private String role = "USER"; // 기본 USER

    @Column(name = "status", nullable = false, length = 10)
    private String status = "active"; // 계정 상태

    @Column(name = "last_login")
    private Instant lastLogin;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();

    public UserEntity() {}

    public UserEntity(String username, String password, String fullName, String email,
                           String phone, String role, String status) {
        this.username = username;
        this.password = password;
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.role = role;
        this.status = status;
    }

    // ===== Getter / Setter =====
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getLastLogin() { return lastLogin; }
    public void setLastLogin(Instant lastLogin) { this.lastLogin = lastLogin; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
