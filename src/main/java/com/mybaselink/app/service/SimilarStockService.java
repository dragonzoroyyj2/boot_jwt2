package com.mybaselink.app.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Service
public class SimilarStockService {

    private final ObjectMapper mapper = new ObjectMapper();
    private final String pythonExe = "C:\\Users\\dragon\\AppData\\Local\\Programs\\Python\\Python310\\python.exe";
    private final String scriptPath = "D:\\project\\dev_boot_project\\workspace\\MyBaseLink\\python\\find_similar_full_succ.py";
    private final String jsonPath = "D:\\project\\dev_boot_project\\workspace\\MyBaseLink\\python\\data\\similarity_result_succ.json";

    private final Map<String, String> tickerMap = new HashMap<>();

    public SimilarStockService() {
        tickerMap.put("케이엔알시스템", "199430.KQ");
        tickerMap.put("삼성전자", "005930.KS");
        tickerMap.put("SK하이닉스", "000660.KS");
        tickerMap.put("NAVER", "035420.KS");
    }

    public List<Map<String, Object>> fetchSimilar(String company, String start, String end) {
        try {
            String stockTicker = tickerMap.getOrDefault(company, company);
            
            if (stockTicker.equals(company) && isKorean(company)) {
                 System.err.println("[Java] 한글 종목명에 대한 티커를 찾을 수 없거나 티커 코드가 잘못되었습니다: " + company);
                 return List.of();
            }

            ProcessBuilder pb = new ProcessBuilder(
                    pythonExe,
                    scriptPath,
                    stockTicker,
                    start,
                    end
            );

            pb.environment().put("PYTHONIOENCODING", "utf-8");
            pb.redirectErrorStream(true);
            Process process = pb.start();

            try (BufferedReader reader =
                         new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("[Python] " + line);
                }
            }

            int exitCode = process.waitFor();
            System.out.println("[Java] Python 프로세스 종료 코드: " + exitCode);

            File file = new File(jsonPath);
            if (!file.exists() || file.length() == 0) {
                System.err.println("[Java] 결과 JSON 파일이 존재하지 않거나 비어있습니다: " + jsonPath);
                return List.of();
            }

            // 🟢 수정된 부분: JSON을 Map으로 읽고, "results" 키의 리스트 추출
            Map<String, Object> resultMap = mapper.readValue(file, new TypeReference<Map<String, Object>>() {});
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> resultList = (List<Map<String, Object>>) resultMap.get("results");
            
            System.out.println("[Java] 분석 결과 로드 완료. 총 항목 수: " + resultList.size());
            return resultList;

        } catch (Exception e) {
            e.printStackTrace();
            return List.of();
        }
    }
    
    private boolean isKorean(String str) {
        return str.chars().anyMatch(c -> Character.getType(c) == Character.OTHER_LETTER);
    }
}
