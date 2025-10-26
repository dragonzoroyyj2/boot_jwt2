package com.mybaselink.app.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Service
public class SimilarStockAdvancedService {

    private static final Logger logger = LoggerFactory.getLogger(SimilarStockAdvancedService.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final TaskStatusService taskStatusService;

    private final String pythonExe = "C:\\Users\\dragon\\AppData\\Local\\Programs\\Python\\Python310\\python.exe";
    private final String scriptPath = "D:\\project\\dev_boot_project\\workspace\\MyBaseLink\\python\\stock\\py\\find_similar_full.py";

    private final AtomicBoolean pythonScriptLock = new AtomicBoolean(false);
    private final ConcurrentMap<String, Process> runningProcesses = new ConcurrentHashMap<>();

    public SimilarStockAdvancedService(TaskStatusService taskStatusService) {
        this.taskStatusService = taskStatusService;
    }

    @Async
    public void startSimilarStockTask(String taskId, String companyCode, String start, String end, int nSimilarStocks) {
        taskStatusService.setTaskStatus(taskId, new TaskStatusService.TaskStatus("IN_PROGRESS", null, null));

        if (pythonScriptLock.compareAndSet(false, true)) {
            try {
                List<Map<String, Object>> results = fetchSimilar(taskId, companyCode, start, end, nSimilarStocks);
                //taskStatusService.setTaskStatus(taskId, new TaskStatusService.TaskStatus("COMPLETED", results, null));
            } catch (Exception e) {
                if ("CANCELLED".equalsIgnoreCase(taskStatusService.getTaskStatus(taskId).getStatus())) return;
                taskStatusService.setTaskStatus(taskId, new TaskStatusService.TaskStatus("FAILED", null, e.getMessage()));
                logger.error("유사 종목 분석 실패: {}", e.getMessage());
            } finally {
                pythonScriptLock.set(false);
                runningProcesses.remove(taskId);
            }
        } else {
            taskStatusService.setTaskStatus(taskId, new TaskStatusService.TaskStatus("FAILED", null, "다른 분석이 진행 중입니다."));
        }
    }

    public List<Map<String, Object>> fetchSimilar(String taskId, String companyCode, String start, String end, int nSimilarStocks)
            throws Exception {
        String[] command = {
                pythonExe, "-u", scriptPath,
                "--base_symbol", companyCode,
                "--start_date", start,
                "--end_date", end,
                "--n_similar", String.valueOf(nSimilarStocks)
        };
        JsonNode result = executePythonScript(taskId, command);
        if (result != null && result.has("similar_stocks")) {
            return mapper.convertValue(result.get("similar_stocks"), new TypeReference<List<Map<String, Object>>>(){});
        }
        return List.of();
    }

    public String fetchChartSingle(String taskId, String symbol, String start, String end) throws Exception {
        String[] command = {
                pythonExe, "-u", scriptPath,
                "--base_symbol", symbol,
                "--start_date", start,
                "--end_date", end
        };
        JsonNode result = executePythonScript(taskId, command);
        if (result != null && result.has("image_data")) {
            return result.get("image_data").asText();
        }
        return null;
    }

    private JsonNode executePythonScript(String taskId, String[] command)
            throws IOException, InterruptedException, ExecutionException, TimeoutException {

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.environment().put("PYTHONIOENCODING", "utf-8");
        pb.directory(new File("D:\\project\\dev_boot_project\\workspace\\MyBaseLink\\python"));
        Process process = pb.start();
        runningProcesses.put(taskId, process);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<String> outputFuture = executor.submit(() -> {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                return r.lines().collect(Collectors.joining("\n"));
            }
        });

        Future<Void> errFuture = executor.submit(() -> {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                r.lines().forEach(line -> logger.info("[Python][{}] {}", taskId, line));
            }
            return null;
        });

        String output = null;
        try {
            output = outputFuture.get(180, TimeUnit.SECONDS);
            errFuture.get(180, TimeUnit.SECONDS);
            boolean finished = process.waitFor(180, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new TimeoutException("Python 실행 시간 초과");
            }
        } finally {
            executor.shutdown();
        }

        int exitCode = process.exitValue();
        if (exitCode != 0) throw new RuntimeException("Python 비정상 종료: " + exitCode);
        return mapper.readTree(output);
    }

    public boolean cancelTask(String taskId) {
        Process p = runningProcesses.remove(taskId);
        if (p != null && p.isAlive()) {
            p.destroyForcibly();
            //taskStatusService.markCancelled(taskId);
            logger.warn("Python 프로세스 강제 종료됨: {}", taskId);
            return true;
        }
        return false;
    }
}
