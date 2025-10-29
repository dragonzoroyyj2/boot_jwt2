package com.mybaselink.app.service;

import com.mybaselink.app.entity.LoginUserEntity;
import com.mybaselink.app.repository.LoginUserRepository;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 🔑 LoginUserService - 로그인 전용 사용자 서비스
 */
@Service
public class LoginUserService {

    private final LoginUserRepository userRepository;

    public LoginUserService(LoginUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * 🔹 username 기반 사용자 조회 (Optional)
     */
    public Optional<LoginUserEntity> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    /**
     * 🔹 반드시 존재해야 할 때(인증 등) 사용하는 편의 메서드
     *    없으면 UsernameNotFoundException 발생
     */
    public LoginUserEntity getRequiredByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다: " + username));
    }
}
