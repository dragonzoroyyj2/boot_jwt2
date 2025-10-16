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
