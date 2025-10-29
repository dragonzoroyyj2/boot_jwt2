package com.mybaselink.app.service;

import com.mybaselink.app.entity.LoginUserEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * 🔐 CustomUserDetailsService - Spring Security용 UserDetailsService 구현
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final LoginUserService userService;

    public CustomUserDetailsService(LoginUserService userService) {
        this.userService = userService;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // Optional 대응: 반드시 존재해야 하므로 getRequiredByUsername 사용
        LoginUserEntity user = userService.getRequiredByUsername(username);

        GrantedAuthority authority = new SimpleGrantedAuthority(user.getRole());

        return org.springframework.security.core.userdetails.User
                .withUsername(user.getUsername())
                .password(user.getPassword())
                .authorities(authority)
                .accountExpired(false)
                .accountLocked(false)
                .credentialsExpired(false)
                .disabled(false)
                .build();
    }
}
