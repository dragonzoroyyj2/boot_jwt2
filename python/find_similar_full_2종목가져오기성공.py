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
import sys
import backoff
from http.client import RemoteDisconnected

# 스크립트 파일의 디렉토리 경로를 기준으로 경로 설정
script_dir = os.path.dirname(os.path.abspath(__file__))
log_file_path = os.path.join(script_dir, "data", "my_log_file.log")
json_result_path = os.path.join(script_dir, "data", "similarity_result.json")
data_dir = os.path.join(script_dir, "stock_data")

# 로그 파일 및 데이터 디렉토리 생성 (없으면)
os.makedirs(os.path.dirname(log_file_path), exist_ok=True)
os.makedirs(data_dir, exist_ok=True)

# 로깅 설정
logger = logging.getLogger()
logger.setLevel(logging.INFO)

# 로그 포맷 정의
formatter = logging.Formatter("%(asctime)s - %(levelname)s - %(message)s")

# 파일 핸들러 생성 (파일에 로그 기록)
file_handler = logging.FileHandler(log_file_path, encoding='utf-8')
file_handler.setFormatter(formatter)
logger.addHandler(file_handler)

# 스트림 핸들러 생성 (콘솔에 로그 출력)
stream_handler = logging.StreamHandler(sys.stdout)
stream_handler.setFormatter(formatter)
logger.addHandler(stream_handler)

JSON_RESULT_PATH = json_result_path

@backoff.on_exception(backoff.expo, RemoteDisconnected, max_tries=5, jitter=backoff.full_jitter)
def fetch_fdr_with_retry(symbol, start=None, end=None):
    """
    재시도 로직을 포함하여 FinanceDataReader 호출을 래핑하는 함수
    """
    return fdr.DataReader(symbol, start=start, end=end)

def fetch_fdr_and_save(symbol):
    """
    FinanceDataReader를 사용하여 주가 데이터를 가져와 파일로 저장/업데이트하는 함수
    """
    file_path = os.path.join(data_dir, f"{symbol}.parquet")
    today = datetime.now().strftime('%Y-%m-%d')
    try:
        if os.path.exists(file_path):
            existing_df = pd.read_parquet(file_path)
            last_date = existing_df.index.max().strftime('%Y-%m-%d')
            new_data = fetch_fdr_with_retry(symbol, start=last_date, end=today)
            
            if not new_data.empty and new_data.index.max() > existing_df.index.max():
                updated_df = pd.concat([existing_df, new_data[new_data.index > existing_df.index.max()]])
                updated_df.to_parquet(file_path)
                logger.info(f"{symbol} 데이터 업데이트 완료")
            else:
                logger.info(f"{symbol} 최신 데이터 없음. 업데이트 불필요.")
        else:
            df = fetch_fdr_with_retry(symbol)
            df.to_parquet(file_path)
            logger.info(f"{symbol} 전체 데이터 새로 저장")
    except Exception as e:
        logger.error(f"{symbol} 데이터 처리 실패: {e}")

def save_all_data():
    """
    모든 종목의 데이터를 순차적으로 처리하여 파일로 저장 및 업데이트
    """
    logger.info("모든 종목 데이터 업데이트 시작...")
    try:
        krx_list = fdr.StockListing('KRX')
        symbol_col = next((c for c in ['Symbol', 'Code'] if c in krx_list.columns.tolist()), None)
        if not symbol_col:
            raise KeyError("KRX 리스트에서 'Symbol' 또는 'Code' 컬럼을 찾을 수 없습니다.")
        
        krx_symbols = krx_list[symbol_col].tolist()
    except Exception as e:
        logger.error(f"KRX 종목 리스트 로드 실패: {e}")
        return

    # max_workers=1로 설정하여 순차적으로 실행하여 메모리 사용량 최소화
    with ThreadPoolExecutor(max_workers=1) as executor:
        futures = {executor.submit(fetch_fdr_and_save, symbol): symbol for symbol in krx_symbols}
        for future in as_completed(futures):
            future.result()
    logger.info("모든 종목 데이터 업데이트 완료.")

def save_to_json(data, filepath):
    try:
        os.makedirs(os.path.dirname(filepath), exist_ok=True)
        with open(filepath, 'w', encoding='utf-8') as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
        logger.info(f"JSON 저장 완료: {filepath}")
    except Exception as e:
        logger.error(f"JSON 저장 실패: {e}")

def main(base_symbol, start_date, end_date, n_similar_stocks=10):
    
    # 먼저 전체 종목 데이터 업데이트 (순차 처리)
    save_all_data()

    logger.info(f"분석 시작 - 기준 종목: {base_symbol}, 시작일: {start_date}, 종료일: {end_date}")
    
    try:
        krx_list = fdr.StockListing('KRX') 
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

    try:
        base_data = pd.read_parquet(os.path.join(data_dir, f"{base_symbol}.parquet"))
        base_data = base_data.loc[start_date:end_date]
    except FileNotFoundError:
        logger.error(f"기준 종목({base_symbol})의 데이터 파일이 존재하지 않습니다.")
        return
        
    if base_data.empty:
        logger.error(f"기준 종목 ({base_symbol})의 데이터 기간이 너무 짧거나 데이터가 없습니다.")
        return

    base_close_prices = base_data['Close']
    similarities = []
    
    def process_stock(symbol):
        if symbol == base_symbol:
            return None
        
        try:
            file_path = os.path.join(data_dir, f"{symbol}.parquet")
            if not os.path.exists(file_path):
                return None

            data = pd.read_parquet(file_path)
            close_prices = data['Close'].loc[start_date:end_date]
            close_prices = close_prices.reindex(base_close_prices.index).interpolate(method='linear')
            
            if close_prices.isnull().any():
                logger.warning(f"데이터 불충분: {symbol}")
                return None
            
            base_prices_norm = (base_close_prices - np.mean(base_close_prices)) / np.std(base_close_prices)
            stock_prices_norm = (close_prices - np.mean(close_prices)) / np.std(close_prices)
            
            cos_sim = cosine_similarity(base_prices_norm.values.reshape(1, -1), stock_prices_norm.values.reshape(1, -1))
            
            return {
                "ticker": symbol,
                "name": krx_name_map.get(symbol, '알 수 없음'),
                "cosine_similarity": cos_sim.item()
            }
        except Exception as e:
            logger.error(f"데이터 처리 중 오류 발생 {symbol}: {e}")
            return None
    
    # 유사성 분석은 CPU 바운드이므로 5개 스레드로 병렬 처리
    with ThreadPoolExecutor(max_workers=5) as executor:
        futures = {executor.submit(process_stock, symbol): symbol for symbol in krx_symbols}
        
        for future in as_completed(futures):
            result = future.result()
            if result:
                similarities.append(result)
                
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
