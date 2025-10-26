// D:\project\dev_boot_project\workspace\MyBaseLink\src\main\java\com\mybaselink\app\service\ChartPatternService.java
package com.mybaselink.app.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Service
public class ChartPatternService {

    private static final Logger logger = LoggerFactory.getLogger(ChartPatternService.class);

    private final ObjectMapper mapper = new ObjectMapper();
    private final TaskStatusService taskStatusService;

    private final String pythonExe = "C:\\Users\\dragon\\AppData\\Local\\Programs\\Python\\Python310\\python.exe";
    private final String scriptPath = "D:\\project\\dev_boot_project\\workspace\\MyBaseLink\\python\\stock\\py\\find_chart_patterns.py";

    // 단일 실행 선점 + 프로세스 레지스트리(취소/강제종료용)
    private final AtomicBoolean pythonScriptLock = new AtomicBoolean(false);
    private final ConcurrentMap<String, Process> runningProcesses = new ConcurrentHashMap<>();

    public ChartPatternService(TaskStatusService taskStatusService) {
        this.taskStatusService = taskStatusService;
    }

    // =========================
    // 패턴 분석 시작 (비동기)
    // =========================
    @Async
    public void startChartPatternTask(String taskId, String start, String end, String pattern, int topN) {
        taskStatusService.setTaskStatus(taskId, new TaskStatusService.TaskStatus("IN_PROGRESS", null, null));

        if (!pythonScriptLock.compareAndSet(false, true)) {
            taskStatusService.setTaskStatus(taskId, new TaskStatusService.TaskStatus("FAILED", null, "다른 분석이 진행 중입니다."));
            return;
        }

        try {
            List<Map<String, Object>> results = executePatternScan(taskId, start, end, pattern, topN);
            //taskStatusService.setTaskStatus(taskId, new TaskStatusService.TaskStatus("COMPLETED", results, null));
            logger.info("[{}] 패턴 분석 완료 ({} 건)", taskId, results.size());
        } catch (Exception e) {
            // 취소로 이미 상태가 변경된 경우는 로그만
            if (!"CANCELLED".equalsIgnoreCase(taskStatusService.getTaskStatus(taskId).getStatus())) {
                taskStatusService.setTaskStatus(taskId, new TaskStatusService.TaskStatus("FAILED", null, e.getMessage()));
            }
            logger.error("[{}] 패턴 분석 실패: {}", taskId, e.getMessage(), e);
        } finally {
            pythonScriptLock.set(false);
            runningProcesses.remove(taskId);
        }
    }

    // 실제 파이썬 실행 (패턴 스캔)
    public List<Map<String, Object>> executePatternScan(String taskId, String start, String end, String pattern, int topN)
            throws Exception {

        String[] command = {
                pythonExe, "-u", scriptPath,
                // find_chart_patterns.py 는 --start/--end 와 --start_date/--end_date 둘 다 지원
                "--start", start,
                "--end", end,
                "--pattern", pattern,
                "--topN", String.valueOf(topN),
                "--workers", String.valueOf(Math.max(2, Runtime.getRuntime().availableProcessors()))
        };

        JsonNode result = runPythonAndGetJson(taskId, command);

        // 스크립트는 리스트(JSON 배열)를 반환
        if (result != null && result.isArray()) {
            return mapper.convertValue(result, new TypeReference<List<Map<String, Object>>>() {});
        }
        return List.of();
    }

    // 공통 파이썬 실행부
    private JsonNode runPythonAndGetJson(String taskId, String[] command)
            throws IOException, InterruptedException, ExecutionException, TimeoutException {

        logger.info("[{}] Python 실행: {}", taskId, Arrays.toString(command));
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.environment().put("PYTHONIOENCODING", "utf-8");
        pb.directory(new File("D:\\project\\dev_boot_project\\workspace\\MyBaseLink\\python"));
        Process process = pb.start();
        runningProcesses.put(taskId, process);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        Future<String> out = executor.submit(() -> {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                return r.lines().collect(Collectors.joining("\n"));
            }
        });
        Future<Void> err = executor.submit(() -> {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                r.lines().forEach(line -> logger.info("[Python][{}] {}", taskId, line));
            }
            return null;
        });

        String stdout = null;
        try {
            stdout = out.get(180, TimeUnit.SECONDS);
            err.get(180, TimeUnit.SECONDS);

            boolean finished = process.waitFor(180, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new TimeoutException("Python 실행 시간 초과");
            }
        } finally {
            executor.shutdown();
        }

        int exit = process.exitValue();
        runningProcesses.remove(taskId);

        if (exit != 0) {
            throw new RuntimeException("Python 비정상 종료(exit=" + exit + ")");
        }
        if (stdout == null || stdout.isBlank()) {
            throw new RuntimeException("Python 출력이 비어 있습니다.");
        }
        return mapper.readTree(stdout);
    }

    // =========================
    // 취소 (프로세스 강제 종료)
    // =========================
    public boolean cancelTask(String taskId) {
        Process p = runningProcesses.remove(taskId);
        if (p != null && p.isAlive()) {
            logger.warn("[{}] 취소 요청 수신 → Python 프로세스 강제 종료", taskId);
            p.destroyForcibly();
            taskStatusService.setTaskStatus(taskId, new TaskStatusService.TaskStatus("CANCELLED", null, "사용자에 의해 취소됨"));
            pythonScriptLock.set(false);
            return true;
        }
        return false;
    }
}
