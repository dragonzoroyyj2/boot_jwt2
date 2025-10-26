// D:\project\dev_boot_project\workspace\MyBaseLink\src\main\java\com\mybaselink\app\controller\ChartPatternController.java
package com.mybaselink.app.controller;

import com.mybaselink.app.service.ChartPatternService;
import com.mybaselink.app.service.TaskStatusService;
import org.slf4j.*;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Controller
@RequestMapping("/chart")
public class ChartPatternController {

    private static final Logger logger = LoggerFactory.getLogger(ChartPatternController.class);

    private final ChartPatternService chartPatternService;
    private final TaskStatusService taskStatusService;

    public ChartPatternController(ChartPatternService chartPatternService, TaskStatusService taskStatusService) {
        this.chartPatternService = chartPatternService;
        this.taskStatusService = taskStatusService;
    }

    // 패턴 분석 시작
    @PostMapping("/patterns/start")
    public ResponseEntity<Map<String, Object>> startPatternTask(@RequestParam String start,
                                                                @RequestParam String end,
                                                                @RequestParam String pattern,
                                                                @RequestParam(defaultValue = "10") int topN) {
        String taskId = UUID.randomUUID().toString();
        logger.info("📊 차트 패턴 분석 요청 수신: taskId={}, pattern={}, 기간={}~{}", taskId, pattern, start, end);
        chartPatternService.startChartPatternTask(taskId, start, end, pattern, topN);
        return ResponseEntity.accepted().body(Map.of(
                "taskId", taskId,
                "message", "차트 패턴 분석 작업을 시작했습니다."
        ));
    }

    // 상태 조회
    @GetMapping("/task/status/{taskId}")
    public ResponseEntity<TaskStatusService.TaskStatus> getTaskStatus(@PathVariable String taskId) {
        TaskStatusService.TaskStatus status = taskStatusService.getTaskStatus(taskId);
        return ResponseEntity.ok(status);
    }

    // 🔴 취소 API
    @PostMapping("/patterns/cancel")
    public ResponseEntity<Map<String, Object>> cancelPatternTask(@RequestParam String taskId) {
        boolean cancelled = chartPatternService.cancelTask(taskId);
        if (cancelled) {
            return ResponseEntity.ok(Map.of(
                    "taskId", taskId,
                    "status", "CANCELLED",
                    "message", "분석이 취소되었습니다."
            ));
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "error", "취소할 작업을 찾을 수 없습니다."
        ));
    }
}
