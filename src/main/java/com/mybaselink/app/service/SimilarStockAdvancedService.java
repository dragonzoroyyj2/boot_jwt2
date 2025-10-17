package com.mybaselink.app.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Service
public class SimilarStockAdvancedService {

    private final ObjectMapper mapper = new ObjectMapper();
    private final String pythonExe = "C:\\Users\\dragon\\AppData\\Local\\Programs\\Python\\Python310\\python.exe";
    private final String scriptPath = "D:\\project\\dev_boot_project\\workspace\\MyBaseLink\\python\\find_similar_full.py";
    private final String jsonPath = "D:\\project\\dev_boot_project\\workspace\\MyBaseLink\\python\\data\\similarity_result.json";

    public List<Map<String, Object>> fetchSimilar(String companyCode, String start, String end, int nSimilarStocks) {
        try {
            // 파이썬 스크립트가 있는 디렉토리를 작업 디렉토리로 설정
            File scriptDir = new File("D:\\project\\dev_boot_project\\workspace\\MyBaseLink\\python");

            ProcessBuilder pb = new ProcessBuilder(
                    pythonExe,
                    scriptPath,
                    companyCode,
                    start,
                    end,
                    "--n", String.valueOf(nSimilarStocks)
            );
            // 작업 디렉토리 설정
            pb.directory(scriptDir); 

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
            if (exitCode != 0) {
                throw new RuntimeException("Python script execution failed with exit code: " + exitCode);
            }

            File file = new File(jsonPath);
            if (!file.exists() || file.length() == 0) {
                System.err.println("JSON 결과 파일이 존재하지 않거나 비어 있습니다.");
                return List.of();
            }

            List<Map<String, Object>> resultList = mapper.readValue(file, new TypeReference<List<Map<String, Object>>>(){});

            return resultList;

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("유사 종목 조회 중 오류 발생: " + e.getMessage());
            return List.of();
        }
    }
}
