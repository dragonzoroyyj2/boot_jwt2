package com.mybaselink.app.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SysController {

    // 📄 화면 페이지 연결
    @GetMapping("/pages/sy/syusr/syusr01List")
    public String syusr01ListPage() {
        return "pages/sy/syusr/syusr01List";
    }
}
