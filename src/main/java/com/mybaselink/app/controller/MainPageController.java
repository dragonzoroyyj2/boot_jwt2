package com.mybaselink.app.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 📄 메인 페이지 컨트롤러
 * 
 * - /pages/main/base 요청 시 base.html 템플릿을 반환
 * - JWT 로그인 성공 후 기본 홈 페이지 역할
 */
@Controller
public class MainPageController {

    @GetMapping("/pages/main/base")
    public String basePage() {
        // templates/pages/main/base.html 파일을 렌더링
        return "pages/main/base";
    }
}
