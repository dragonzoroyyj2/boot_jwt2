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
"""
기준 종목 데이터 수집:
main() 함수는 FinanceDataReader를 사용해 사용자가 입력한 기준 종목(base_symbol)의 주가 데이터를 지정된 start_date부터 end_date까지 가져옵니다.
이 데이터 중 종가(Close) 컬럼을 유사도 분석의 기준으로 사용합니다.
전체 종목 데이터 가져오기:
fdr.StockListing('KRX')를 호출하여 KRX에 상장된 전체 종목 목록을 가져옵니다.
이 목록을 바탕으로 ThreadPoolExecutor를 활용하여 여러 스레드에서 동시에 다른 종목들의 주가 데이터를 조회합니다.
데이터 전처리 및 유사도 계산:
process_stock() 함수에서 각 종목의 주가 데이터를 가져온 뒤, 기준 종목 데이터의 기간에 맞게 reindex() 함수를 사용해 인덱스를 맞춥니다.
데이터에 결측값(NaN)이 있으면 interpolate()를 이용해 선형으로 보간합니다.
정규화: 종가의 절대적인 값 대신, 기간 동안의 주가 변동 패턴을 비교하기 위해 각 종목의 종가 데이터를 표준정규분포(평균 0, 표준편차 1) 형태로 정규화합니다.
코사인 유사도: sklearn.metrics.pairwise.cosine_similarity 함수를 이용해 정규화된 기준 종목의 종가 벡터와 다른 종목의 종가 벡터 간의 코사인 유사도를 계산합니다. 코사인 유사도 값이 1에 가까울수록 두 차트의 변동 패턴이 유사하다는 것을 의미합니다.
결과 정렬 및 저장:
계산된 유사도 값들을 cosine_similarity를 기준으로 내림차순 정렬합니다.
상위 n개의 유사한 종목들을 JSON 파일로 저장합니다. 또한, 작업 중에도 중간 결과를 저장하여 진행 상황을 확인할 수 있도록 합니다.

이 코드는 단순한 데이터 수집을 넘어, 금융 데이터를 활용한 실제적인 분석(유사 종목 찾기)을 구현한 로직 



-----------
db
-----------
1. 시계열 데이터베이스(TSDB) 사용
주가 데이터처럼 시간 순서대로 발생하는 데이터는 시계열 데이터베이스에 저장하는 것이 가장 효율적입니다. TSDB는 이런 데이터의 저장, 압축, 쿼리에 특화되어 있습니다. 
장점:
대용량 데이터 처리 및 고속 쿼리에 최적화되어 있습니다.
데이터 보관 정책, 다운샘플링 등 유용한 기능을 제공합니다.
추천 솔루션:
InfluxDB: 고성능 및 확장성으로 유명하며, 실시간 분석 및 모니터링에 적합합니다.
QuestDB: SQL 쿼리를 지원하며 고성능으로 시계열 데이터를 처리합니다. 
2. 관계형 데이터베이스(RDBMS) 사용
데이터 양이 아주 많지 않고 복잡한 분석이나 조인(join)이 필요하다면 기존의 관계형 데이터베이스를 사용할 수도 있습니다.
장점: SQL 사용에 익숙하고 데이터 조작이 유연합니다.
추천 솔루션:
PostgreSQL: 특히 시계열 데이터 처리와 인덱싱 기능이 잘 갖춰져 있어 주식 데이터 저장에 적합합니다.
MySQL: 널리 사용되지만, 시계열 데이터 전용 데이터베이스에 비하면 성능이 떨어질 수 있습니다. 
3. 파일 기반 저장
데이터 양이 적거나 분석 목적이 명확할 때는 파일 형태로 저장하는 것도 좋은 방법입니다. 
장점: 구현이 간단하고 별도의 데이터베이스 서버가 필요 없습니다.
추천 방식:
Parquet: 데이터 압축과 쿼리 성능에 최적화된 칼럼 기반 파일 형식입니다. 대용량 데이터를 효율적으로 저장하고 처리할 수 있습니다.
CSV: pandas 같은 라이브러리로 쉽게 읽고 쓸 수 있어 간단한 데이터 저장에 편리합니다. 
추천 데이터 저장 절차
데이터베이스 선택: 프로젝트의 규모와 복잡성에 따라 적절한 데이터베이스를 선택합니다. 대규모 데이터 처리와 실시간 분석이 중요하다면 **시계열 데이터베이스(InfluxDB)**를, 기존 SQL 환경에 익숙하다면 PostgreSQL을 고려할 수 있습니다.
테이블/스키마 설계: 종목 코드, 날짜, 시가, 고가, 저가, 종가, 거래량 등 필요한 데이터를 저장할 테이블을 설계합니다. 효율적인 조회를 위해 종목코드와 날짜를 인덱스로 설정하는 것이 좋습니다.
일일 수집 자동화: 매일 장 마감 후 스크립트를 실행하여 전체 종목의 일별 데이터를 수집하고 데이터베이스에 추가합니다.
백업 및 유지보수: 정기적인 백업을 통해 데이터 손실을 방지하고, 데이터베이스 성능 관리를 위한 유지보수 작업을 수행합니다

"""

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
