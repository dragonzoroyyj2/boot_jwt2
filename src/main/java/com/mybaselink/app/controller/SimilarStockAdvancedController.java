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
public class SimilarStockAdvancedController {

    private final SimilarStockAdvancedService service;

    public SimilarStockAdvancedController(SimilarStockAdvancedService service) {
        this.service = service;
    }

    @GetMapping("/similar-advanced")
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
}
