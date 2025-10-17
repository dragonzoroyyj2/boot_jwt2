import pandas as pd
import json
import logging
from concurrent.futures import ThreadPoolExecutor, as_completed
import FinanceDataReader as fdr
from sklearn.metrics.pairwise import cosine_similarity
import numpy as np
import argparse
import time
import os
from datetime import datetime
import threading

# 스크립트 파일의 디렉토리 경로를 기준으로 경로 설정
script_dir = os.path.dirname(os.path.abspath(__file__))
log_file_path = os.path.join(script_dir, "data", "my_log_file.log")
json_result_path = os.path.join(script_dir, "data", "similarity_result.json")

# 로그 파일 디렉토리 생성 (없으면)
os.makedirs(os.path.dirname(log_file_path), exist_ok=True)

# 로깅 설정
logging.basicConfig(filename=log_file_path,
                    level=logging.INFO,
                    format="%(asctime)s - %(levelname)s - %(message)s",
                    encoding='utf-8')
logger = logging.getLogger()

JSON_RESULT_PATH = json_result_path

# 전역 변수로 실패 횟수 및 Lock 객체 관리
CONNECTION_ABORT_THRESHOLD = 10
connection_abort_count = 0
count_lock = threading.Lock()

def fetch_fdr(symbol, start_date=None, end_date=None):
    """
    FinanceDataReader를 사용하여 주가 데이터를 가져오는 함수
    """
    try:
        if isinstance(symbol, str) and symbol.endswith(('.KS', '.KQ')):
            symbol = symbol[:6]
        
        data = fdr.DataReader(symbol, start_date, end_date)
        if data.empty:
            logger.warning(f"[FinanceDataReader] 데이터 없음: {symbol}")
            return pd.DataFrame()
        return data
    except Exception as e:
        if 'Remote end closed connection' in str(e):
            with count_lock:
                global connection_abort_count
                connection_abort_count += 1
        logger.error(f"[FinanceDataReader] 조회 실패 {symbol}: {e}")
        return pd.DataFrame()

def save_to_json(data, filepath):
    try:
        os.makedirs(os.path.dirname(filepath), exist_ok=True)
        with open(filepath, 'w', encoding='utf-8') as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
        logger.info(f"JSON 저장 완료: {filepath}")
    except Exception as e:
        logger.error(f"JSON 저장 실패: {e}")

def main(base_symbol, start_date, end_date, n_similar_stocks=10):
    global connection_abort_count
    connection_abort_count = 0
    
    logger.info(f"분석 시작 - 기준 종목: {base_symbol}, 시작일: {start_date}, 종료일: {end_date}")
    
    try:
        krx_list = fdr.StockListing('KRX')
        
        # 동적으로 컬럼명 찾기
        available_cols = krx_list.columns.tolist()
        symbol_col = next((c for c in ['Symbol', 'Code'] if c in available_cols), None)
        name_col = next((c for c in ['Name'] if c in available_cols), None)
        
        if not symbol_col or not name_col:
            raise KeyError(f"필요한 컬럼 중 하나가 없습니다. (현재 컬럼: {available_cols})")
        
        krx_symbols = krx_list[symbol_col].tolist()
        krx_name_map = krx_list.set_index(symbol_col)[name_col].to_dict()
    except Exception as e:
        logger.error(f"KRX 종목 리스트 로드 실패: {e}")
        return

    base_data = fetch_fdr(base_symbol, start_date=start_date, end_date=end_date)
    if base_data.empty:
        logger.error(f"기준 종목 데이터 로드 실패: {base_symbol}")
        return

    base_close_prices = base_data['Close']
    if len(base_close_prices) == 0:
        logger.error(f"기준 종목 ({base_symbol})의 데이터 기간이 너무 짧거나 데이터가 없습니다.")
        return

    similarities = []
    
    def process_stock(symbol, start_date, end_date, base_close_prices):
        global connection_abort_count
        if symbol == base_symbol:
            return None
        
        if connection_abort_count >= CONNECTION_ABORT_THRESHOLD:
            return None
            
        try:
            data = fetch_fdr(symbol, start_date=start_date, end_date=end_date)
            if not data.empty:
                close_prices = data['Close'].reindex(base_close_prices.index).interpolate(method='linear')

                if close_prices.isnull().any():
                    logger.warning(f"데이터 불충분: {symbol}")
                    return None
                
                base_prices_norm = (base_close_prices - np.mean(base_close_prices)) / np.std(base_close_prices)
                stock_prices_norm = (close_prices - np.mean(close_prices)) / np.std(close_prices)
                
                cos_sim = cosine_similarity(base_prices_norm.values.reshape(1, -1), stock_prices_norm.values.reshape(1, -1))
                time.sleep(0.5)
                
                return {
                    "ticker": symbol,
                    "name": krx_name_map.get(symbol, '알 수 없음'),
                    "cosine_similarity": cos_sim.item()
                }
        except Exception as e:
            logger.error(f"데이터 처리 중 오류 발생 {symbol}: {e}")
            return None
    
    with ThreadPoolExecutor(max_workers=20) as executor:
        futures = {executor.submit(process_stock, symbol, start_date, end_date, base_close_prices): symbol for symbol in krx_symbols}
        
        for future in as_completed(futures):
            if connection_abort_count >= CONNECTION_ABORT_THRESHOLD:
                logger.error(f"연결 끊김 오류가 {CONNECTION_ABORT_THRESHOLD}회 발생하여 작업을 중단합니다.")
                executor.shutdown(wait=False, cancel_futures=True)
                break
            
            result = future.result()
            if result:
                similarities.append(result)
                similarities.sort(key=lambda x: x['cosine_similarity'], reverse=True)
                top_n_similar_stocks = similarities[:n_similar_stocks]
                save_to_json(top_n_similar_stocks, JSON_RESULT_PATH)

    similarities.sort(key=lambda x: x['cosine_similarity'], reverse=True)
    top_n_similar_stocks = similarities[:n_similar_stocks]
    
    save_to_json(top_n_similar_stocks, JSON_RESULT_PATH)
    logger.info(f"분석 완료 - 상위 {len(top_n_similar_stocks)}개 종목 결과 파일: {JSON_RESULT_PATH}")
    
if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="코사인 유사도를 이용해 주식 종목을 분석합니다.")
    parser.add_argument("symbol", help="기준 종목 티커 코드 (예: 005930)")
    parser.add_argument("start", help="시작일 (YYYY-MM-DD)")
    parser.add_argument("end", help="종료일 (YYYY-MM-DD)")
    parser.add_argument("--n", dest="n_similar_stocks", type=int, default=10, help="가져올 유사한 종목의 수 (기본값: 10)")
    args = parser.parse_args()

    main(args.symbol, args.start, args.end, args.n_similar_stocks)
