package com.mybaselink.app.controller;

import com.mybaselink.app.service.SimilarStockAdvancedService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/krx")
public class SimilarStockAdvancedController {

    private final SimilarStockAdvancedService service;

    @Autowired
    public SimilarStockAdvancedController(SimilarStockAdvancedService service) {
        this.service = service;
    }

    @GetMapping("/similar-advanced")
    public ResponseEntity<?> getSimilarStocks(
            @RequestParam String company,
            @RequestParam String start,
            @RequestParam String end
    ) {
        List<Map<String, Object>> results = service.findSimilarStocks(company, start, end);
        return ResponseEntity.ok(results);
    }
}
