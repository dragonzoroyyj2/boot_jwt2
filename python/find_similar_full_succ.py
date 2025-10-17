import sys
import os
import json
import yfinance as yf
import pandas as pd
from sklearn.preprocessing import MinMaxScaler
from dtw import dtw

def find_similar_stocks(target_company, start_date, end_date):
    try:
        target_data = yf.download(target_company, start=start_date, end=end_date)
        if target_data.empty:
            print(f"기준 종목 데이터를 찾을 수 없습니다: {target_company}", file=sys.stderr)
            return []

        target_series = target_data['Close'].dropna().values.reshape(-1, 1)
        if len(target_series) == 0:
            print(f"기준 종목에 유효한 종가 데이터가 없습니다: {target_company}", file=sys.stderr)
            return []

        results = []
        sample_tickers = ["005930.KS", "000660.KS", "035420.KS", "005380.KS", "006400.KS", "199430.KQ"]

        for ticker in sample_tickers:
            if ticker == target_company:
                continue

            try:
                stock_data = yf.download(ticker, start=start_date, end=end_date)
                if stock_data.empty:
                    continue
                
                stock_series = stock_data['Close'].dropna().values.reshape(-1, 1)
                
                if len(stock_series) == 0:
                    continue
                    
                min_len = min(len(target_series), len(stock_series))
                target_series_trimmed = target_series[-min_len:]
                stock_series_trimmed = stock_series[-min_len:]
                
                scaler = MinMaxScaler()
                target_scaled = scaler.fit_transform(target_series_trimmed)
                stock_scaled = scaler.fit_transform(stock_series_trimmed)

                alignment = dtw(target_scaled, stock_scaled)
                d = alignment.distance
                
                similarity_score = 1 / (1 + d)
                
                results.append({
                    "ticker": ticker,
                    "similarity_score": similarity_score,
                    "distance": d,
                    "comparison_days": min_len
                })

            except Exception as e:
                print(f"[{ticker}] 처리 중 오류 발생: {e}", file=sys.stderr)
                continue
        
        results.sort(key=lambda x: x['similarity_score'], reverse=True)
        
        return results

    except Exception as e:
        print(f"전체 스크립트 실행 중 오류 발생: {e}", file=sys.stderr)
        return []

if __name__ == "__main__":
    if len(sys.argv) < 4:
        print("사용법: python find_similar_full.py <종목코드> <시작일> <종료일>", file=sys.stderr)
        sys.exit(1)

    company_code = sys.argv[1]
    start_date = sys.argv[2]
    end_date = sys.argv[3]

    print(f"기준 종목: {company_code}, 기간: {start_date} ~ {end_date}")

    similar_stocks = find_similar_stocks(company_code, start_date, end_date)
    
    # JSON 결과를 객체로 감싸서 저장
    output_data = {"results": similar_stocks}

    json_path = "D:\\project\\dev_boot_project\\workspace\\MyBaseLink\\python\\data\\similarity_result_succ.json"
    
    os.makedirs(os.path.dirname(json_path), exist_ok=True)
    
    with open(json_path, 'w', encoding='utf-8') as f:
        json.dump(output_data, f, ensure_ascii=False, indent=4)
        
    print(f"유사 종목 분석 결과가 {json_path}에 저장되었습니다.")
