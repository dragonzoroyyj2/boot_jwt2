package com.mybaselink.app.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class P01A04PageController {

    // 📄 화면 페이지 연결
    @GetMapping("/pages/p01/p01a04/p01a04List")
    public String p01a05ListPage() {
        return "pages/p01/p01a04/p01a04List";
    }
}
