# -*- coding: utf-8 -*-
import os
import sys
import argparse
import json
import platform
import subprocess
import ssl
import urllib3
from http.client import RemoteDisconnected
import base64
from io import BytesIO
import pandas as pd
import FinanceDataReader as fdr
import matplotlib.pyplot as plt
import backoff
import pickle
from datetime import datetime, timedelta
from multiprocessing import Pool, Manager, freeze_support
import time
import hashlib
from functools import wraps
import logging
from logging.handlers import QueueHandler, QueueListener
import matplotlib.font_manager as fm
from matplotlib import rc
import filelock

# ========================================================================
# 로깅 및 데이터 디렉토리 설정
# ========================================================================
script_dir = os.path.dirname(os.path.abspath(__file__))
log_file_path = os.path.join(script_dir, "log", "my_log_file.log")
data_dir = os.path.join(script_dir, "stock_data")
chart_cache_dir = os.path.join(data_dir, "charts")
os.makedirs(os.path.dirname(log_file_path), exist_ok=True)
os.makedirs(data_dir, exist_ok=True)
os.makedirs(chart_cache_dir, exist_ok=True)

# 멀티프로세싱을 위한 큐 로깅 설정
def setup_logging_queue(queue):
    """메인 프로세스에서 로깅 큐 핸들러 설정"""
    handler = QueueHandler(queue)
    logger = logging.getLogger()
    logger.setLevel(logging.INFO)
    if logger.hasHandlers():
        logger.handlers.clear()
    logger.addHandler(handler)

# 워커 프로세스용 로깅 설정
def worker_log_init(queue):
    """워커 프로세스가 시작될 때 로깅 핸들러 설정"""
    h = QueueHandler(queue)
    root = logging.getLogger()
    if root.hasHandlers():
        root.handlers.clear()
    root.addHandler(h)
    root.setLevel(logging.INFO)

# ========================================================================
# 한글 폰트 설정
# ========================================================================
def set_korean_font():
    """
    플랫폼에 관계없이 한글 폰트를 설정합니다.
    서버 환경을 위해 폰트를 파일로 배포하는 방식을 권장합니다.
    """
    try:
        font_path = os.path.join(script_dir, 'fonts', 'NanumGothic.ttf')
        if os.path.exists(font_path):
            fm.fontManager.addfont(font_path)
            rc('font', family=fm.FontProperties(fname=font_path).get_name())
            logging.info("폰트 파일(NanumGothic.ttf)을 사용하여 한글 폰트 설정 완료.")
        else:
            if platform.system() == 'Windows':
                font_name = 'Malgun Gothic'
            elif platform.system() == 'Darwin':
                font_name = 'AppleGothic'
            else:
                font_name = 'NanumGothic'
            rc('font', family=font_name)
            logging.info(f"시스템 폰트({font_name})로 한글 폰트 설정 완료.")

    except Exception as e:
        logging.warning(f"한글 폰트 설정 중 오류 발생: {e}. 기본 폰트로 대체합니다.")
        rc('font', family='DejaVu Sans')
    
    plt.rcParams['axes.unicode_minus'] = False

# ========================================================================
# 데이터 캐싱 및 파일 잠금
# ========================================================================
def get_stock_listing(data_dir):
    """KRX 상장 종목 목록을 가져와 캐싱"""
    cache_path = os.path.join(data_dir, "stock_listing.pkl")
    lock_path = os.path.join(data_dir, "krx_listing.lock")
    with filelock.FileLock(lock_path):
        if os.path.exists(cache_path) and (datetime.now() - datetime.fromtimestamp(os.path.getmtime(cache_path))).days < 1:
            with open(cache_path, 'rb') as f:
                return pickle.load(f)
        
        krx = fdr.StockListing('KRX')
        with open(cache_path, 'wb') as f:
            pickle.dump(krx, f)
        return krx

@backoff.on_exception(backoff.expo, (RemoteDisconnected, ssl.SSLError, urllib3.exceptions.SSLError), max_tries=3)
def fetch_fdr_with_retry_with_cache(symbol, start=None, end=None, data_dir=data_dir):
    """FinanceDataReader를 사용하여 주식 데이터를 가져오고 캐시를 적용"""
    cache_key = f"{symbol}_{start}_{end}"
    cache_filename = f"{hashlib.md5(cache_key.encode()).hexdigest()}.pkl"
    cache_path = os.path.join(data_dir, cache_filename)
    
    lock_path = os.path.join(data_dir, f"{symbol}.lock")
    with filelock.FileLock(lock_path):
        if os.path.exists(cache_path):
            if (datetime.now() - datetime.fromtimestamp(os.path.getmtime(cache_path))) < timedelta(days=1):
                logging.info(f"캐시에서 {symbol} 데이터 불러옴: {cache_path}")
                with open(cache_path, 'rb') as f:
                    return pickle.load(f)

        try:
            df = fdr.DataReader(symbol, start=start, end=end)
            if not df.empty:
                with open(cache_path, 'wb') as f:
                    pickle.dump(df, f)
                logging.info(f"FinanceDataReader에서 {symbol} 데이터 새로 가져와 캐시에 저장")
            return df
        except Exception as e:
            raise RuntimeError(f"{symbol} 데이터 조회 중 오류: {e}") from e

# ========================================================================
# API 요청 제어 (Rate Limiting)
# ========================================================================
# 파일 기반으로 RateLimiter 구현 (pickling 오류 방지)
class FileBasedRateLimiter:
    def __init__(self, per_second, data_dir):
        self.interval = 1.0 / per_second
        self.lock_path = os.path.join(data_dir, "rate_limit.lock")
        self.last_call_path = os.path.join(data_dir, "last_call_time.txt")
        self.lock = filelock.FileLock(self.lock_path)
        os.makedirs(os.path.dirname(self.last_call_path), exist_ok=True)

    def wait(self):
        with self.lock:
            try:
                with open(self.last_call_path, 'r') as f:
                    last_call_time = float(f.read())
            except (IOError, ValueError):
                last_call_time = 0.0

            current_time = time.time()
            elapsed_time = current_time - last_call_time
            if elapsed_time < self.interval:
                time.sleep(self.interval - elapsed_time)
            
            with open(self.last_call_path, 'w') as f:
                f.write(str(time.time()))

def rate_limit(api_limiter):
    """API 요청 제한 데코레이터"""
    def decorator(func):
        @wraps(func)
        def wrapper(*args, **kwargs):
            api_limiter.wait()
            return func(*args, **kwargs)
        return wrapper
    return decorator

# ========================================================================
# 종목 데이터 처리 워커 함수 (멀티프로세싱용)
# ========================================================================
worker_api_limiter = None

def worker_initializer(queue, per_second, data_dir):
    """워커 프로세스가 시작될 때 로깅 핸들러와 RateLimiter 설정"""
    worker_log_init(queue)
    global worker_api_limiter
    worker_api_limiter = FileBasedRateLimiter(per_second=per_second, data_dir=data_dir)

def process_stock_data(args_tuple):
    """멀티프로세싱 워커 함수"""
    symbol, start, end, krx = args_tuple
    
    try:
        logging.info(f"심볼 {symbol} 데이터 조회 중...")
        
        @rate_limit(worker_api_limiter)
        def _fetch_data():
            return fetch_fdr_with_retry_with_cache(symbol, start=start, end=end)

        df = _fetch_data()
        
        if df is None or len(df) < 2:
            logging.warning(f"심볼 {symbol}에 대한 데이터가 불충분합니다. (레코드 수: {len(df)})")
            return None
        
        df['CloseDiff'] = df['Close'].diff()
        downward_streak_raw = (df['CloseDiff'] < 0).astype(int).groupby((df['CloseDiff'] >= 0).cumsum()).sum().max()
        
        downward_streak = int(downward_streak_raw) if not pd.isna(downward_streak_raw) else 0
        
        name_series = krx.loc[krx['Code'] == symbol, 'Name']
        name = name_series.iloc[0] if not name_series.empty else "N/A" #iloc로 수정
        
        return {"ticker": symbol, "name": name, "streak": downward_streak}
    except Exception as e:
        logging.error(f"심볼 {symbol} 처리 중 오류: {e}", exc_info=True)
        return None

# ========================================================================
# 차트 생성 및 캐싱 메서드
# ========================================================================
def generate_chart_with_cache(symbol, stock_name, df, start_date, end_date):
    """
    주식 데이터를 기반으로 차트를 생성하고 Base64로 인코딩된 이미지 데이터를 반환합니다.
    캐시 파일이 존재하면 캐시를 사용합니다.
    """
    cache_key = f"{symbol}_{start_date}_{end_date}"
    cache_filename = f"{hashlib.md5(cache_key.encode()).hexdigest()}.png"
    cache_path = os.path.join(chart_cache_dir, cache_filename)

    lock_path = os.path.join(chart_cache_dir, f"{hashlib.md5(cache_key.encode()).hexdigest()}.lock")
    with filelock.FileLock(lock_path):
        if os.path.exists(cache_path):
            logging.info(f"캐시에서 차트 이미지 불러옴: {cache_path}")
            with open(cache_path, 'rb') as f:
                img_base64 = base64.b64encode(f.read()).decode()
            return img_base64

        try:
            set_korean_font()
            plt.figure(figsize=(10, 5))
            plt.plot(df.index, df['Close'], label=stock_name)
            plt.title(f"{stock_name} 종가 차트 ({start_date} ~ {end_date})")
            plt.xlabel("날짜")
            plt.ylabel("종가")
            plt.grid(True)
            plt.tight_layout()

            buffer = BytesIO()
            plt.savefig(buffer, format='png')
            plt.savefig(cache_path, format='png')
            plt.close()
            buffer.seek(0)
            
            img_base64 = base64.b64encode(buffer.read()).decode()
            logging.info(f"새 차트 생성 후 캐시에 저장: {cache_path}")
            return img_base64
        except Exception as e:
            logging.error(f"차트 생성 중 오류 발생: {e}", exc_info=True)
            return None

# ========================================================================
# 메인 함수
# ========================================================================
def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base_symbol", default="ALL", help="조회할 종목 (ALL 또는 특정 종목 코드)")
    parser.add_argument("--start_date", required=True, help="조회 시작 날짜 (YYYY-MM-DD)")
    parser.add_argument("--end_date", required=True, help="조회 종료 날짜 (YYYY-MM-DD)")
    parser.add_argument("--topN", type=int, default=10, help="상위 N개 종목 (리스트 조회 시에만 사용)")
    parser.add_argument("--chart", action="store_true", help="개별 종목 차트 생성")
    args = parser.parse_args()

    log_queue = Manager().Queue()
    listener = QueueListener(log_queue, logging.FileHandler(log_file_path, encoding='utf-8'), logging.StreamHandler(sys.stderr))
    listener.start()
    setup_logging_queue(log_queue)
    logging.info("Python 스크립트 실행 시작: " + " ".join(sys.argv))

    try:
        krx = get_stock_listing(data_dir)
        
        if args.chart:
            if args.base_symbol == "ALL":
                raise ValueError("차트 생성 모드에서는 기준 종목(--base_symbol)이 'ALL'일 수 없습니다.")
            
            symbol = args.base_symbol
            name_series = krx.loc[krx['Code'] == symbol, 'Name']
            stock_name = name_series.iloc[0] if not name_series.empty else symbol

            df = fetch_fdr_with_retry_with_cache(symbol, start=args.start_date, end=args.end_date)
            
            if df is not None and not df.empty:
                img_base64 = generate_chart_with_cache(symbol, stock_name, df, args.start_date, args.end_date)
                if img_base64:
                    print(json.dumps({"image_data": img_base64}, ensure_ascii=False))
                else:
                    print(json.dumps({"error": "차트 생성에 실패했습니다."}, ensure_ascii=False))
            else:
                print(json.dumps({"error": f"{symbol}에 대한 데이터가 없습니다."}, ensure_ascii=False))
        else:
            if args.base_symbol != "ALL":
                if args.base_symbol not in krx['Code'].values:
                    raise ValueError(f"{args.base_symbol} 종목이 존재하지 않습니다.")
                base_list = [args.base_symbol]
            else:
                base_list = krx['Code'].tolist()

            results = []
            if args.base_symbol == "ALL":
                num_processes = os.cpu_count() or 1
                logging.info(f"멀티프로세싱으로 {len(base_list)}개 종목 처리 시작 (프로세스 수: {num_processes})")

                with Pool(processes=num_processes, initializer=worker_initializer, initargs=(log_queue, 1, data_dir,)) as pool:
                    args_list = [(symbol, args.start_date, args.end_date, krx) for symbol in base_list]
                    results = [res for res in pool.map(process_stock_data, args_list) if res]

            else:
                api_limiter = FileBasedRateLimiter(per_second=2, data_dir=data_dir)
                res = process_stock_data((args.base_symbol, args.start_date, args.end_date, krx, api_limiter))
                if res:
                    results.append(res)
            
            sorted_results = sorted(results, key=lambda x: x['streak'], reverse=True)
            top_N_results = sorted_results[:args.topN]
            print(json.dumps(top_N_results, ensure_ascii=False, indent=2))
            
    except Exception as e:
        logging.error(f"스크립트 실행 중 치명적인 오류 발생: {e}", exc_info=True)
        print(json.dumps({"error": str(e)}, ensure_ascii=False))
    finally:
        listener.stop()

if __name__ == '__main__':
    freeze_support()
    main()
