package com.mybaselink.app.repository;

import com.mybaselink.app.entity.JwtTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.Optional;
import java.util.List;

/**
 * 🔒 JwtTokenRepository
 *
 * JWT 토큰의 DB 저장 및 조회 전용 레포지토리
 * - 특정 토큰 조회
 * - 만료 토큰 삭제
 * - 사용자 활성 토큰 조회
 */
@Repository
public interface JwtTokenRepository extends JpaRepository<JwtTokenEntity, Long> {

    /**
     * 🔹 특정 토큰 조회
     */
    Optional<JwtTokenEntity> findByToken(String token);

    /**
     * 🔹 만료된 토큰 삭제
     * @param now 현재 시각
     * @return 삭제된 토큰 수
     */
    long deleteAllByExpiresAtBefore(Instant now);

    /**
     * 🔹 사용자의 활성 토큰 조회 (revoked = false)
     */
    List<JwtTokenEntity> findByUsernameAndRevokedFalse(String username);
}
