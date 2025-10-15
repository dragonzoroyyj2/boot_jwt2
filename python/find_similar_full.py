# find_similar_full.py
# 사용법: python find_similar_full.py 2024-03-07 2024-12-09
# 출력: JSON (상위 5개 유사종목)

import sys, os, json, glob
import pandas as pd
from scipy.spatial.distance import euclidean
from fastdtw import fastdtw

try:
    import FinanceDataReader as fdr
    FDR_AVAILABLE = True
except Exception:
    FDR_AVAILABLE = False

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
DATA_DIR = os.path.join(BASE_DIR, "data")
os.makedirs(DATA_DIR, exist_ok=True)

def read_csv_safe(path):
    try:
        df = pd.read_csv(path)
        if 'Date' not in df.columns:
            if df.index.name and 'date' in df.index.name.lower():
                df = df.reset_index()
            else:
                for c in df.columns:
                    if 'date' in c.lower() or '일자' in c:
                        df = df.rename(columns={c:'Date'})
                        break
        df['Date'] = df['Date'].astype(str)
        return df
    except Exception:
        return None

def ensure_sample_data(start, end, base_name='케이엔알시스템', sample_count=20):
    # 1) 기준 종목 다운로드
    base_file = os.path.join(DATA_DIR, 'KR_system_base.csv')
    if not os.path.exists(base_file) and FDR_AVAILABLE:
        krx = fdr.StockListing('KRX')
        found = krx[krx['Name'].str.contains(base_name, case=False, na=False)]
        if found.empty:
            # 데이터 없으면 안내 후 샘플 생성
            print(json.dumps({"error": f"'{base_name}' 종목을 FDR에서 찾을 수 없습니다. 샘플 CSV 생성"}, ensure_ascii=False))
            # 샘플 CSV 생성 (날짜, 가격 더미)
            dates = pd.date_range(start=start, end=end)
            prices = pd.Series([100 + i*0.5 for i in range(len(dates))])
            df = pd.DataFrame({"Date": dates.strftime("%Y-%m-%d"), "Close": prices})
            df.to_csv(base_file, index=False)
        else:
            code = found.iloc[0]['Code']
            df = fdr.DataReader(code, start, end)
            if df.empty:
                # 데이터 없으면 샘플 생성
                dates = pd.date_range(start=start, end=end)
                prices = pd.Series([100 + i*0.5 for i in range(len(dates))])
                df = pd.DataFrame({"Date": dates.strftime("%Y-%m-%d"), "Close": prices})
            df.to_csv(base_file, index=False)

    # 2) 샘플 비교용 종목 CSV
    csvs = glob.glob(os.path.join(DATA_DIR, '*.csv'))
    if len(csvs) <= 1 and FDR_AVAILABLE:
        krx = fdr.StockListing('KRX')
        samples = krx.sample(min(sample_count, len(krx)))
        for _, row in samples.iterrows():
            name = row['Name']
            code = row['Code']
            try:
                df = fdr.DataReader(code, start, end)
                if df.empty: continue
                safe_name = "".join(ch for ch in name if ch.isalnum() or ch==' ')
                df.to_csv(os.path.join(DATA_DIR, f"{safe_name}.csv"), index=False)
            except Exception:
                continue

def main():
    if len(sys.argv) < 3:
        print(json.dumps({"error":"need start_date end_date"}, ensure_ascii=False))
        return
    start = sys.argv[1]
    end = sys.argv[2]

    # CSV 생성
    ensure_sample_data(start, end)

    # 기준 데이터
    base_path = os.path.join(DATA_DIR, 'KR_system_base.csv')
    base_df = read_csv_safe(base_path)
    if base_df is None or base_df.empty:
        print(json.dumps({"error":"base data not found or empty"}, ensure_ascii=False))
        return

    base_df = base_df[(base_df['Date'] >= start) & (base_df['Date'] <= end)]
    close_col_candidates = [c for c in base_df.columns if 'close' in c.lower()]
    if not close_col_candidates:
        print(json.dumps({"error":"Close column not found in base"}, ensure_ascii=False))
        return
    base_series = base_df[close_col_candidates[0]].values

    results = []
    for file in glob.glob(os.path.join(DATA_DIR, '*.csv')):
        if os.path.basename(file) == 'KR_system_base.csv':
            continue
        df = read_csv_safe(file)
        if df is None or df.empty:
            continue
        df = df[(df['Date'] >= start) & (df['Date'] <= end)]
        close_cols = [c for c in df.columns if 'close' in c.lower()]
        if not close_cols or len(df) < 10:
            continue
        target_series = df[close_cols[0]].values
        try:
            distance, _ = fastdtw(base_series, target_series, dist=euclidean)
        except Exception:
            continue
        results.append({
            "file": os.path.basename(file),
            "distance": float(distance),
            "dates": df['Date'].tolist(),
            "prices": df[close_cols[0]].tolist()
        })

    results = sorted(results, key=lambda x: x['distance'])
    topN = results[:5]
    print(json.dumps(topN, ensure_ascii=False))

if __name__ == '__main__':
    main()
