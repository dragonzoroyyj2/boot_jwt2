package com.mybaselink.app.controller;

import com.mybaselink.app.service.SimilarStockAdvancedService;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/krx")
public class SimilarStockAdvancedController2 {

    private final SimilarStockAdvancedService service;

    public SimilarStockAdvancedController2(SimilarStockAdvancedService service) {
        this.service = service;
    }

    @GetMapping("/similar-advanced_bak")
    public ResponseEntity<Map<String, Object>> getSimilarStocks(
            @RequestParam String companyCode,
            @RequestParam String start,
            @RequestParam String end,
            @RequestParam(defaultValue = "10") int nSimilarStocks
    ) {
        try {
            List<Map<String,Object>> results = service.fetchSimilar(companyCode, start, end, nSimilarStocks);
            
            Map<String, Object> responseBody = Map.of(
                "status", "success",
                "count", results.size(),
                "results", results
            );
            
            return new ResponseEntity<>(responseBody, HttpStatus.OK);

        } catch (Exception e) {
            e.printStackTrace();
            Map<String, Object> errorBody = Map.of(
                "status", "error",
                "message", e.getMessage(),
                "results", Collections.emptyList()
            );
            return new ResponseEntity<>(errorBody, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    
    @GetMapping("/chart_bak")
    public ResponseEntity<String> getChart(
            @RequestParam String baseSymbol,
            @RequestParam String compareSymbol,
            @RequestParam String start,
            @RequestParam String end
    ) {
        try {
            String base64Image = service.fetchChart(baseSymbol, compareSymbol, start, end);
            if (base64Image != null) {
                return ResponseEntity.ok(base64Image);
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("차트 생성 실패");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("차트 조회 중 오류 발생: " + e.getMessage());
        }
    }
}
