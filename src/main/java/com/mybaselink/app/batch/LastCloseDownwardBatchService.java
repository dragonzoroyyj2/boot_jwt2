package com.mybaselink.app.batch;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CachePut;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Python 스크립트를 호출하여 연속 하락 종목 조회 및 차트 반환 서비스 (배치용)
 */
@Service("lastCloseDownwardBatchService")
public class LastCloseDownwardBatchService {

    private static final Logger logger = LoggerFactory.getLogger(LastCloseDownwardBatchService.class);

    private final ObjectMapper mapper = new ObjectMapper();

    // Python 실행 경로
    private final String pythonExe = "C:\\Users\\dragon\\AppData\\Local\\Programs\\Python\\Python310\\python.exe";

    // Python 스크립트 경로
    private final String scriptPath = "D:\\project\\dev_boot_project\\workspace\\MyBaseLink\\python\\find_last_close_downward.py";

    /**
     * 스케줄러용: 항상 Python을 호출하여 캐시를 갱신
     * @param start 조회 시작 날짜 (YYYY-MM-DD)
     * @param end 조회 종료 날짜 (YYYY-MM-DD)
     * @param topN 상위 N개 종목
     * @return 연속 하락 종목 리스트
     */
    @CachePut(value = "lastCloseDownwardCache", key = "#start + '-' + #end + '-' + #topN")
    public List<Map<String, Object>> refreshAndPutLastCloseDownward(String start, String end, int topN) {
        try {
            String[] command = {
                    pythonExe,
                    "-u",
                    scriptPath,
                    "--base_symbol", "ALL",
                    "--start_date", start,
                    "--end_date", end,
                    "--topN", String.valueOf(topN)
            };
            logger.info("캐시 갱신 (CachePut): Python 스크립트를 호출하여 연속 하락 종목 조회. 기간: {} ~ {}, 상위 {}개", start, end, topN);

            JsonNode pythonResult = executePythonScript(command);
            if (pythonResult != null) {
                if (pythonResult.has("error")) {
                    String errorMsg = pythonResult.get("error").asText();
                    logger.error("Python 스크립트 실행 오류: {}", errorMsg);
                    throw new RuntimeException(errorMsg);
                }
                List<Map<String, Object>> results = mapper.convertValue(
                        pythonResult,
                        new TypeReference<List<Map<String, Object>>>() {}
                );
                logger.info("연속 하락 종목 조회 완료. 총 {}건", results.size());
                return results;
            } else {
                logger.error("Python 스크립트 결과가 null입니다.");
                throw new RuntimeException("Python 스크립트 결과가 null입니다.");
            }
        } catch (Exception e) {
            logger.error("Python 스크립트 호출 실패", e);
            throw new RuntimeException("Python 스크립트 호출 실패: " + e.getMessage());
        }
    }

    /**
     * Python 스크립트 실행 및 JSON 파싱
     */
    private JsonNode executePythonScript(String[] command)
            throws IOException, InterruptedException, ExecutionException, TimeoutException {

        File scriptDir = new File("D:\\project\\dev_boot_project\\workspace\\MyBaseLink\\python");
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(scriptDir);
        pb.environment().put("PYTHONIOENCODING", "utf-8");

        Process process = pb.start();

        // 표준 에러(로그)를 실시간으로 읽는 별도 스레드
        ExecutorService errorExecutor = Executors.newSingleThreadExecutor();
        Future<?> errorFuture = errorExecutor.submit(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                reader.lines().forEach(line -> logger.error("[Python ERR] {}", line));
            } catch (IOException e) {
                logger.error("Error reading Python stderr stream", e);
            }
        });

        // 표준 출력(JSON 결과)을 읽는 스레드
        ExecutorService outputExecutor = Executors.newSingleThreadExecutor();
        Future<String> outputFuture = outputExecutor.submit(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            } catch (IOException e) {
                logger.error("Error reading Python stdout stream", e);
                return "";
            }
        });

        String pythonOutput = null;
        try {
            boolean finished = process.waitFor(600, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new TimeoutException("Python 프로세스가 시간 내 종료되지 않았습니다.");
            }

            pythonOutput = outputFuture.get();

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                throw new RuntimeException("Python 스크립트 종료 코드: " + exitCode + ". Python 출력: " + pythonOutput);
            }
        } catch (TimeoutException e) {
            process.destroyForcibly();
            throw e;
        } finally {
            errorExecutor.shutdown();
            outputExecutor.shutdown();
        }

        if (pythonOutput == null || pythonOutput.trim().isEmpty()) {
            throw new RuntimeException("Python 스크립트 출력이 없습니다.");
        }

        return mapper.readTree(pythonOutput);
    }
}
