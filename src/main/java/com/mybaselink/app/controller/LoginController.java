package com.mybaselink.app.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 🧭 로그인 페이지 컨트롤러
 *
 * - 브라우저에서 "/login" 요청 시 login.html 반환
 * - 인증 성공 시 Spring Security 설정에 따라 /pages/main/base 이동
 */
@Controller
public class LoginController {

    @GetMapping("/login")
    public String loginPage(Model model) {
        model.addAttribute("pageTitle", "로그인");
        return "login"; // => src/main/resources/templates/pages/login.html
    }

    @GetMapping("/")
    public String redirectRoot() {
        return "redirect:/login";
    }
}
