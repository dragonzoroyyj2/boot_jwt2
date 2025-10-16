package com.mybaselink.app.service;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class SimilarStockAdvancedService {

    // Python 실행 경로
    private final String pythonExe = "C:\\Users\\dragon\\AppData\\Local\\Programs\\Python\\Python310\\python.exe";
    // Python 스크립트 경로
    private final String scriptPath = "D:\\project\\dev_boot_project\\workspace\\MyBaseLink\\python\\find_similar_full.py";

    /**
     * baseName 종목과 유사한 KRX 종목을 분석
     * @param baseName - 비교 기준 종목 (KRX 기반 YFinance 심볼: 199430.KQ 등)
     * @param start - 조회 시작일 (YYYY-MM-DD)
     * @param end - 조회 종료일 (YYYY-MM-DD)
     * @return 유사도 결과 리스트
     */
    public List<Map<String, Object>> findSimilarStocks(String baseName, String start, String end) {
        List<Map<String, Object>> resultList = new ArrayList<>();
        try {
            // Python 스크립트 실행
            ProcessBuilder pb = new ProcessBuilder(pythonExe, scriptPath, baseName, start, end);
            pb.environment().put("PYTHONUTF8", "1"); // UTF-8 강제
            pb.directory(new File("D:\\project\\dev_boot_project\\workspace\\MyBaseLink\\python")); // 작업 디렉토리
            Process process = pb.start();

            // Python stdout 읽기
            BufferedReader stdout = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
            BufferedReader stderr = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8));

            StringBuilder output = new StringBuilder();
            String line;
            while ((line = stdout.readLine()) != null) output.append(line);

            // Python stderr 읽기 (경고 메시지 확인용)
            StringBuilder errors = new StringBuilder();
            while ((line = stderr.readLine()) != null) errors.append(line).append("\n");
            System.out.println("Python stderr:\n" + errors.toString());

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException("Python 스크립트 실패, exitCode=" + exitCode);
            }

            // Python JSON 파싱
            JSONObject json = new JSONObject(output.toString());
            if (!json.optBoolean("success", false)) {
                throw new RuntimeException("분석 실패: " + json.optString("warning"));
            }

            JSONArray results = json.getJSONArray("results");
            for (int i = 0; i < results.length(); i++) {
                JSONObject item = results.getJSONObject(i);
                Map<String, Object> map = new HashMap<>();

                // Python에서 반환된 target은 이미 .KS/.KQ 심볼
                String yfSymbol = item.optString("target");
                map.put("symbol", yfSymbol); // symbol 필드로 변경
                map.put("company", item.optString("company")); // 회사명 추가
                map.put("similarity", item.optDouble("similarity"));

                resultList.add(map);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return resultList;
    }
}
