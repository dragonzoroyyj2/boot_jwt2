# krx_list.py
# 사용법: python krx_list.py
# 출력: krx_list.json (UTF-8-sig), Thymeleaf에서 읽음

import FinanceDataReader as fdr
import pandas as pd
import json
import os

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
DATA_DIR = os.path.join(BASE_DIR, "data")
os.makedirs(DATA_DIR, exist_ok=True)

krx = fdr.StockListing('KRX')

# 필요 컬럼만 선택, 없으면 빈 문자열로 처리
columns = ['Symbol', 'Market', 'Name', 'Sector', 'Industry', 'ListingDate', 'Representative', 'HomePage', 'Region']
for col in columns:
    if col not in krx.columns:
        krx[col] = ''

# CSV 저장 (Excel 호환 한글)
csv_path = os.path.join(DATA_DIR, 'krx_list.csv')
krx.to_csv(csv_path, index=False, encoding='utf-8-sig')

# JSON 저장
json_path = os.path.join(DATA_DIR, 'krx_list.json')
krx[columns].fillna('').to_json(json_path, orient='records', force_ascii=False)

print(json.dumps({"status":"ok", "count": len(krx)}, ensure_ascii=False))
