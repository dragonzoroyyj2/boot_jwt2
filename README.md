1️⃣ Python 설치 확인

Python 3.10 이상 설치되어 있어야 합니다.

설치 후 터미널에서 확인:

python --version


출력 예시:

Python 3.10.5


pip도 설치 확인:

pip --version

2️⃣ 가상환경 만들기 (선택이지만 권장)

프로젝트마다 패키지 버전을 안전하게 관리하려면 가상환경 추천:

cd D:\project\dev_boot_project\workspace\MyBaseLink\python
python -m venv venv


가상환경 활성화:

# Windows
venv\Scripts\activate


활성화되면 프롬프트가 (venv)로 표시됩니다.

3️⃣ 필수 패키지 설치

KRX 스크립트에서 필요한 패키지:

pandas

requests

lxml

beautifulsoup4 (선택)

설치 명령:

pip install pandas requests lxml beautifulsoup4

4️⃣ 설치 확인

Python에서 아래 명령 실행:

python -c "import pandas; import requests; import lxml; import bs4; print('모두 OK')"


출력:

모두 OK


✅ 여기까지 뜨면 Python 환경과 필수 패키지 세팅 완료

5️⃣ 스크립트 테스트

Python 스크립트 실행:

python krx_list_fetch.py


출력 예시:

테이블 컬럼명: ['회사명', '시장구분', '종목코드', '업종', '주요제품', '상장일', '결산월', '대표자명', '홈페이지', '지역']
KRX 데이터 수: 2763
krx_list_full.json 파일 생성 완료


정상적으로 JSON 파일이 생성되어야 합니다.

----------------
설명:

--upgrade pip → pip 최신화

scipy → DTW 계산에 필요

fastdtw → 빠른 DTW 계산

pandas → CSV 처리

& "C:\Users\User\AppData\Local\Programs\Python\Python310\python.exe" -m pip install --upgrade pip
& "C:\Users\User\AppData\Local\Programs\Python\Python310\python.exe" -m pip install scipy fastdtw pandas FinanceDataReader








-----------------
10 - 17

dragonzoroyj04 시작
pip install pandas numpy yfinance openpyxl


2️⃣ 타종목 다운로드

한국거래소(KRX) 또는 Yahoo Finance API 활용 가능

예를 들어 Yahoo Finance는 Python yfinance로 쉽게 다운로드 가능

CSV 파일명 = 종목명.csv (케이엔알시스템.csv, 삼성전자.csv 등)




그러면 목표는 KRX 상장 전체 종목 데이터를 자동으로 다운로드하고, 기준 종목과 비교해서 유사도 계산 후 JSON 반환하는 Python 스크립트입니다.

아래는 전체 통합 버전 초안입니다. 주요 포인트는:

기준 종목 CSV 없으면 자동 다운로드

타 종목 CSV 없으면 자동 다운로드 (KRX 전체 종목 대상)

시작일~종료일 기준 Close 가격 사용

Pandas + yfinance로 다운로드

코사인 유사도 계산 후 JSON 출력

--->>>


KRX 전체 종목 분석을 빠르게 수행할 수 있도록 멀티스레딩을 적용한 Python 스크립트 버전을 만들어드리겠습니다.

이 버전 특징:

기준 종목 CSV 다운로드/로드

KRX 전체 종목 리스트 조회

각 종목의 CSV 다운로드 및 Close 기준 정규화

코사인 유사도 계산

ThreadPoolExecutor로 병렬 처리 → 다운로드/계산 속도 개선

데이터 부족 시 자동 제외

----------------------------------------------------------
정말 로그아웃하시겠습니까?

ChatGPT에서 dragonzoroyj2@gmail.com 계정을 로그아웃하시겠습니까?



private final String pythonExe = "C:\\Users\\dragon\\AppData\\Local\\Programs\\Python\\Python310\\python.exe";
    private final String scriptPath = "D:\\project\\dev_boot_project\\workspace\\MyBaseLink\\python\\find_similar_full.py";
    


/MyBaseLink/src/main/java/com/mybaselink/app/service/SimilarStockAdvancedService.java

/MyBaseLink/src/main/java/com/mybaselink/app/controller/SimilarStockAdvancedController.java


/MyBaseLink/src/main/resources/templates/pages/stock/similar-advanced.html

/MyBaseLink/python/find_similar_full.py

/MyBaseLink/python/data

내구조는 이러하다 절대 바꾸지마

내가 하고싶은거는 실제 케이엔알시스템 종목의 시작일과 종료일 에 일봉 테이터로 타종목의 유사한 차트 종목을 찾고싶은거야

그래서 타종목을 /MyBaseLink/python/data 여기에 csv 파일로 여러종목을 저장하고

 유사정보를 가져와서 화면에 정보를 보여주는거야 




아래는 KRX 코드 기반 YFinance 심볼로 수정된 find_similar_full.py 전체 버전입니다.
주석을 최대한 친절하게 달아서, 각 단계에서 무슨 일이 일어나는지 이해하기 쉽게 만들었습니다.



Python stderr:
HTTP Error 404: {"quoteSummary":{"result":null,"error":{"code":"Not Found","description":"Quote not found for symbol: 케이엔알시스템"}}}

[*********************100%***********************]  1 of 1 completed

1 Failed download:
['케이엔알시스템']: YFTzMissingError('possibly delisted; no timezone found')




import os
import sys
import json
import pandas as pd
import numpy as np
from sklearn.metrics.pairwise import cosine_similarity
import yfinance as yf
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime

# -------------------------
# 설정
# -------------------------
DATA_DIR = r"D:\project\dev_boot_project\workspace\MyBaseLink\python\data"
os.makedirs(DATA_DIR, exist_ok=True)

MIN_LENGTH = 30
MAX_WORKERS = 10
FAILED_CSV = os.path.join(DATA_DIR, "failed_symbols.csv")

# -------------------------
# KRX JSON 로드 및 상장 필터링
# -------------------------
def load_krx_json(json_file):
    try:
        df = pd.read_json(json_file)
        # 상장일을 datetime으로 변환
        df['listedDate'] = pd.to_datetime(df['listedDate'], errors='coerce')

        # 현재 상장 중인 종목만 필터링 (폐지일이 없거나 미래일)
        df = df[df['listedDate'].notna()]

        # YFinance 심볼 생성
        def get_yf_symbol(row):
            if row['market'] == 'KOSDAQ':
                return f"{row['code']}.KQ"
            elif row['market'] == 'KOSPI':
                return f"{row['code']}.KS"
            return None

        df['yf_symbol'] = df.apply(get_yf_symbol, axis=1)
        df = df.dropna(subset=['yf_symbol'])
        return df[['name','yf_symbol']].values.tolist()
    except Exception as e:
        print(f"KRX JSON 로드 실패: {e}", file=sys.stderr)
        return []

# -------------------------
# YFinance 다운로드
# -------------------------
def download_stock_csv(symbol, start, end):
    file_path = os.path.join(DATA_DIR, f"{symbol}.csv")
    if os.path.exists(file_path):
        return file_path
    try:
        df = yf.download(symbol, start=start, end=end, auto_adjust=True)
        if df.empty:
            return None
        df.reset_index(inplace=True)
        df = df[['Date','Close']]
        df.columns = ['date','close']
        df.to_csv(file_path, index=False)
        return file_path
    except Exception:
        return None

# -------------------------
# CSV 로드 및 전처리
# -------------------------
def load_csv(file_path):
    df = pd.read_csv(file_path)
    df.columns = [c.strip().lower() for c in df.columns]
    if not {'date','close'}.issubset(df.columns):
        return None
    df = df.sort_values(by='date').reset_index(drop=True)
    df['close'] = pd.to_numeric(df['close'], errors='coerce')
    df = df.dropna(subset=['close'])
    if len(df) < MIN_LENGTH:
        return None
    return df

# -------------------------
# 종가 정규화
# -------------------------
def normalize_series(series):
    arr = np.array(series, dtype=float)
    if arr.size < MIN_LENGTH:
        return None
    return (arr - np.min(arr)) / (np.max(arr) - np.min(arr) + 1e-9)

# -------------------------
# 코사인 유사도
# -------------------------
def calculate_similarity(base_series, target_series):
    min_len = min(len(base_series), len(target_series))
    base = base_series[-min_len:].reshape(1,-1)
    target = target_series[-min_len:].reshape(1,-1)
    return cosine_similarity(base, target)[0][0]

# -------------------------
# 단일 종목 분석
# -------------------------
def analyze_target(base_close, company_name, symbol, start, end):
    file_path = download_stock_csv(symbol, start, end)
    if file_path is None:
        return {"target": symbol, "company": company_name, "similarity": None, "warning": "데이터 없음"}
    df = load_csv(file_path)
    if df is None:
        return {"target": symbol, "company": company_name, "similarity": None, "warning": "데이터 부족"}
    target_close = normalize_series(df['close'].values)
    if target_close is None:
        return {"target": symbol, "company": company_name, "similarity": None, "warning": "정규화 실패"}
    similarity = calculate_similarity(base_close, target_close)
    return {"target": symbol, "company": company_name, "similarity": round(float(similarity), 4)}

# -------------------------
# 실패 종목 CSV 저장
# -------------------------
def save_failed_csv(failed_list):
    if not failed_list:
        return
    df_failed = pd.DataFrame(failed_list)
    df_failed.to_csv(FAILED_CSV, index=False, encoding='utf-8-sig')
    print(f"실패 종목 CSV 저장 완료: {FAILED_CSV}")

# -------------------------
# 메인
# -------------------------
def main():
    output = {"success": False, "base": None, "start_date": None, "end_date": None, "results": [], "warnings": []}
    failed_list = []

    try:
        if len(sys.argv) < 4:
            raise ValueError("Usage: python find_similar.py <base_symbol> <start_date> <end_date>")
        base_symbol, start_date, end_date = sys.argv[1:4]
        output["base"], output["start_date"], output["end_date"] = base_symbol, start_date, end_date

        base_file = download_stock_csv(base_symbol, start_date, end_date)
        if base_file is None:
            output["success"] = True
            output["warnings"].append(f"{base_symbol} 데이터 부족")
            print(json.dumps(output, ensure_ascii=False, indent=4))
            return

        base_df = load_csv(base_file)
        if base_df is None:
            output["success"] = True
            output["warnings"].append(f"{base_symbol} 데이터 부족")
            print(json.dumps(output, ensure_ascii=False, indent=4))
            return

        base_close = normalize_series(base_df['close'].values)

        krx_list = load_krx_json(r"D:/project/dev_boot_project/workspace/MyBaseLink/python/krx_list_full.json")
        results = []

        # 병렬 분석
        with ThreadPoolExecutor(max_workers=MAX_WORKERS) as executor:
            futures = [executor.submit(analyze_target, base_close, name, sym, start_date, end_date)
                       for name, sym in krx_list if sym != base_symbol]
            for future in as_completed(futures):
                res = future.result()
                if res["similarity"] is None:
                    output["warnings"].append(f"{res['target']} ({res['company']}): {res['warning']}")
                    failed_list.append({"symbol": res['target'], "company": res['company'], "reason": res['warning']})
                else:
                    results.append(res)

        results.sort(key=lambda x: x["similarity"], reverse=True)
        output["success"] = True
        output["results"] = results
        print(json.dumps(output, ensure_ascii=False, indent=4))

        # 실패 종목 CSV 저장
        save_failed_csv(failed_list)

    except Exception as e:
        output["success"] = False
        output["warnings"].append(str(e))
        print(json.dumps(output, ensure_ascii=False, indent=4))

if __name__ == "__main__":
    main()

package com.mybaselink.app.controller;

import com.mybaselink.app.service.SimilarStockAdvancedService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/krx")
public class SimilarStockAdvancedController {

    private final SimilarStockAdvancedService service;

    @Autowired
    public SimilarStockAdvancedController(SimilarStockAdvancedService service) {
        this.service = service;
    }

    @GetMapping("/similar-advanced")
    public ResponseEntity<?> getSimilarStocks(
            @RequestParam String company,
            @RequestParam String start,
            @RequestParam String end
    ) {
        List<Map<String, Object>> results = service.findSimilarStocks(company, start, end);
        return ResponseEntity.ok(results);
    }
}




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




<!DOCTYPE html>
<html lang="ko"
      xmlns:th="http://www.thymeleaf.org"
      xmlns:layout="http://www.ultraq.net.nz/thymeleaf/layout"
      layout:decorate="~{layout/default_layout}">

<head>
  <meta charset="UTF-8">
  <title>유사 차트 종목 분석</title>
  <meta name="viewport" content="width=device-width, initial-scale=1.0">

  <link rel="stylesheet"
        href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.0/css/all.min.css">

  <style>
    body {
      font-family: "Noto Sans KR", Arial, sans-serif;
      background-color: #f9fafb;
      color: #1f2937;
      margin: 0;
      padding: 0;
    }

    .content-container {
      max-width: 1500px;
      margin: 0 auto;
      padding: 1.5rem;
    }

    h2 {
      font-size: 1.3rem;
      font-weight: 700;
      color: #1f2937;
      margin-bottom: 1rem;
    }

    .search-area {
      display: flex;
      flex-wrap: wrap;
      gap: 0.7rem;
      align-items: center;
      background: white;
      padding: 1rem;
      border-radius: 10px;
      box-shadow: 0 2px 6px rgba(0,0,0,0.05);
    }

    input, button {
      padding: 0.6rem 0.8rem;
      font-size: 0.9rem;
      border-radius: 6px;
      border: 1px solid #d1d5db;
    }

    input {
      flex: 1;
      min-width: 150px;
    }

    button {
      background-color: #2563eb;
      color: white;
      border: none;
      cursor: pointer;
      transition: background-color 0.2s ease;
    }

    button:hover {
      background-color: #1e40af;
    }

    table {
      width: 100%;
      margin-top: 1.5rem;
      border-collapse: collapse;
      background-color: white;
      border-radius: 8px;
      overflow: hidden;
      box-shadow: 0 2px 6px rgba(0,0,0,0.05);
    }

    th, td {
      padding: 0.8rem 1rem;
      border-bottom: 1px solid #e5e7eb;
      text-align: left;
      font-size: 0.9rem;
    }

    th {
      background-color: #f3f4f6;
      font-weight: 600;
    }

    tr:hover {
      background-color: #f9fafb;
    }

    .loading {
      display: none;
      margin-top: 1rem;
      text-align: center;
      font-size: 0.9rem;
      color: #2563eb;
    }

    .loading.active {
      display: block;
    }

    .warning {
      margin-top: 1rem;
      color: red;
      font-size: 0.9rem;
    }
  </style>
</head>

<body>
<div layout:fragment="content">
  <div class="content-container">
    <h2>📊 유사 차트 종목 분석</h2>

    <!-- 검색/분석 영역 -->
    <div class="search-area">
      <input type="text" id="company" placeholder="기준 종목명 (예: 케이엔알시스템)" value="케이엔알시스템">
      <input type="date" id="start" value="2024-03-07">
      <input type="date" id="end" value="2024-12-09">
      <button id="searchBtn"><i class="fa-solid fa-magnifying-glass"></i> 분석 시작</button>
    </div>

    <!-- 로딩/경고 메시지 -->
    <div class="loading" id="loading">🔍 분석 중입니다... 잠시만 기다려주세요.</div>
    <div class="warning" id="warning"></div>

    <!-- 결과 테이블 -->
    <table id="resultTable">
      <thead>
        <tr>
          <th>종목명</th>
          <th>유사도 점수</th>
          <th>비교기간 (일수)</th>
          <th>데이터 포인트</th>
        </tr>
      </thead>
      <tbody></tbody>
    </table>
  </div>
</div>

<th:block layout:fragment="pageScript">
<script>
document.getElementById("searchBtn").addEventListener("click", () => {
  const company = document.getElementById("company").value.trim();
  const start = document.getElementById("start").value;
  const end = document.getElementById("end").value;
  const loading = document.getElementById("loading");
  const warning = document.getElementById("warning");
  const tbody = document.querySelector("#resultTable tbody");

  if (!company || !start || !end) {
    alert("종목명, 시작일, 종료일을 모두 입력해주세요.");
    return;
  }

  tbody.innerHTML = "";
  warning.textContent = "";
  loading.classList.add("active");

  fetch(`/api/krx/similar-advanced?company=${encodeURIComponent(company)}&start=${start}&end=${end}`)
    .then(res => res.json())
    .then(data => {
      loading.classList.remove("active");

      if (!data || typeof data !== 'object') {
        alert("결과 데이터 형식이 올바르지 않습니다.");
        return;
      }

      // warning 메시지 표시
      if (data.warning) {
        warning.textContent = "⚠ " + data.warning;
      }

      // 결과 테이블 채우기
      if (Array.isArray(data.results)) {
        data.results.forEach(item => {
          const tr = document.createElement("tr");
          tr.innerHTML = `
            <td>${item.file || '-'}</td>
            <td>${item.similarity !== undefined ? item.similarity.toFixed(4) : '-'}</td>
            <td>${item.dates ? item.dates.length : '-'}</td>
            <td>${item.prices ? item.prices.length : '-'}</td>
          `;
          tbody.appendChild(tr);
        });
      }
    })
    .catch(err => {
      loading.classList.remove("active");
      warning.textContent = "⚠ 데이터 분석 중 오류가 발생했습니다.";
      console.error(err);
    });
});
</script>
</th:block>
</body>
</html>



<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">

    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.5.6</version>
        <relativePath/>
    </parent>

    <groupId>com.mybaselink</groupId>
    <artifactId>MyBaseLink</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>MyBaseLink</name>
    <description>MyBaseLink Application &amp; Management Platform</description>

    <properties>
        <!-- JDK 21 기준 -->
        <java.version>21</java.version>
        <jjwt.version>0.11.5</jjwt.version>
    </properties>

    <dependencies>
        <!-- Web + REST -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- Spring Security + JWT -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-api</artifactId>
            <version>${jjwt.version}</version>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-impl</artifactId>
            <version>${jjwt.version}</version>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-jackson</artifactId>
            <version>${jjwt.version}</version>
            <scope>runtime</scope>
        </dependency>

        <!-- Thymeleaf 템플릿 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-thymeleaf</artifactId>
        </dependency>
        <dependency>
            <groupId>nz.net.ultraq.thymeleaf</groupId>
            <artifactId>thymeleaf-layout-dialect</artifactId>
        </dependency>

        <!-- JPA + PostgreSQL -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
	   <dependency>
	    <groupId>org.postgresql</groupId>
	    <artifactId>postgresql</artifactId>
	    <version>42.6.0</version> <!-- JDK21 호환 최신버전 권장 -->
		</dependency>


        <!-- Validation -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>

        <!-- Apache POI (Excel Export) -->
        <dependency>
            <groupId>org.apache.poi</groupId>
            <artifactId>poi-ooxml</artifactId>
            <version>5.2.5</version>
        </dependency>

        <!-- DevTools (개발 자동 리로드, runtime 전용) -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-devtools</artifactId>
            <scope>runtime</scope>
        </dependency>
        
		<dependency>
		    <groupId>org.json</groupId>
		    <artifactId>json</artifactId>
		    <version>20210307</version>
		</dependency>

        <!-- Test -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>

    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>

</project>

-------------------

원하시면 제가 Python 스크립트에서 실제 Naver Finance / Yahoo Finance CSV 다운로드까지 완전 구현해서
이 HTML 버튼 클릭만으로 KRX 전체 종목 유사도 분석까지 바로 돌아가게 통합 예제까지 만들어 드릴 수 있습니다.

이거 진행할까요?

python -m pip install scikit-learn

pip install yfinance

