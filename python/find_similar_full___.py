import yfinance as yf
import pandas as pd
import json
import logging
from concurrent.futures import ThreadPoolExecutor, as_completed

# 로깅 설정
logging.basicConfig(filename="data/my_log_file.log",
                    level=logging.INFO,
                    format="%(asctime)s - %(levelname)s - %(message)s",
                    encoding='utf-8')
logger = logging.getLogger()

KRX_LIST_PATH = "data/krx_list.csv"
JSON_RESULT_PATH = "data/similarity_result.json"

def flatten_columns(df):
    if isinstance(df.columns, pd.MultiIndex):
        df.columns = [' '.join(col).strip() if isinstance(col, tuple) else col for col in df.columns.values]
    return df

def fetch_yf(symbol):
    try:
        data = yf.download(symbol, progress=False)
        data = flatten_columns(data)
        if data.empty:
            logger.warning(f"[yfinance] 데이터 없음: {symbol}")
        return data
    except Exception as e:
        logger.error(f"[yfinance] 조회 실패 {symbol}: {e}")
        return pd.DataFrame()

def save_to_json(data, filepath):
    with open(filepath, 'w', encoding='utf-8-sig') as f:
        json.dump(data, f, ensure_ascii=False, indent=2)

def main(base_symbol, base_name):
    logger.info(f"기준 종목: {base_name} ({base_symbol})")

    stock_results = []

    with ThreadPoolExecutor(max_workers=5) as executor:
        futures = {executor.submit(fetch_yf, base_symbol): base_symbol}
        for future in as_completed(futures):
            data = future.result()
            last_close = data['Close'].iloc[-1].item() if not data.empty else None
            stock_results.append({
                "ticker": base_symbol,
                "name": base_name,
                "last_close": last_close,
                "data": data.to_dict(orient="records")
            })

    save_to_json(stock_results, JSON_RESULT_PATH)
    logger.info("JSON 저장 완료: " + JSON_RESULT_PATH)

if __name__ == "__main__":
    base_symbol = input("티커 코드: ")
    base_name = input("종목명: ")
    main(base_symbol, base_name)
