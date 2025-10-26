package com.mybaselink.app.controller;

import com.mybaselink.app.service.SimilarStockAdvancedService;
import com.mybaselink.app.service.TaskStatusService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

@RestController
@RequestMapping("/api/krx")
public class SimilarStockAdvancedController {

    private static final Logger logger = LoggerFactory.getLogger(SimilarStockAdvancedController.class);
    private final SimilarStockAdvancedService service;
    private final TaskStatusService taskStatusService;

    @Autowired
    public SimilarStockAdvancedController(SimilarStockAdvancedService service, TaskStatusService taskStatusService) {
        this.service = service;
        this.taskStatusService = taskStatusService;
    }

    /**
     * 유사 종목 분석 요청 (비동기)
     */
    @GetMapping("/similar-advanced/request")
    public ResponseEntity<?> requestSimilarAdvanced(
            @RequestParam String companyCode,
            @RequestParam String start,
            @RequestParam String end,
            @RequestParam(defaultValue = "10") int nSimilarStocks
    ) {
        String taskId = UUID.randomUUID().toString();
        logger.info("📊 유사 종목 분석 요청 수신: {}", taskId);
        service.startSimilarStockTask(taskId, companyCode, start, end, nSimilarStocks);
        return ResponseEntity.accepted().body(Map.of(
                "taskId", taskId,
                "message", "유사 종목 분석 작업을 시작했습니다."
        ));
    }

    /**
     * 작업 취소
     */
    @PostMapping("/similar-advanced/task/cancel")
    public ResponseEntity<?> cancelTask(@RequestParam String taskId) {
        boolean cancelled = service.cancelTask(taskId);
        if (cancelled) {
            return ResponseEntity.ok(Map.of(
                    "status", "CANCELLED",
                    "message", "분석이 취소되었습니다."
            ));
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "취소할 작업을 찾을 수 없습니다."));
    }

    /**
     * 작업 상태 조회 (NPE 방지 완전 수정)
     */
    @GetMapping("/similar-advanced/task/status")
    public ResponseEntity<?> getTaskStatus(@RequestParam String taskId) {
        TaskStatusService.TaskStatus status = taskStatusService.getTaskStatus(taskId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("taskId", taskId);
        response.put("status", status != null ? status.getStatus() : "UNKNOWN");
        response.put("result", status != null && status.getResult() != null ? status.getResult() : Collections.emptyList());
        response.put("error", status != null && status.getErrorMessage() != null ? status.getErrorMessage() : "");

        logger.info("📡 작업 상태 조회 [{}]: {}", taskId, response.get("status"));
        return ResponseEntity.ok(response);
    }
}
