import pandas as pd
from scipy.spatial.distance import euclidean
from fastdtw import fastdtw
import json
import os
import glob

base_file = 'python/data/KR_system_base.csv'
start_date = '2024-03-07'
end_date = '2024-12-09'

# 기준 종목 불러오기
df_base = pd.read_csv(base_file)
df_base = df_base[(df_base['Date'] >= start_date) & (df_base['Date'] <= end_date)]
base_series = df_base['Close'].values

results = []

# python/data 내 모든 종목 비교
for file in glob.glob('python/data/*.csv'):
    if 'KR_system_base' in file:
        continue

    df = pd.read_csv(file)
    df = df[(df['Date'] >= start_date) & (df['Date'] <= end_date)]
    if len(df) < 10:
        continue

    target_series = df['Close'].values
    distance, _ = fastdtw(base_series, target_series, dist=euclidean)
    results.append({
        'file': os.path.basename(file),
        'distance': distance
    })

# 거리순 정렬 후 상위 3개
results = sorted(results, key=lambda x: x['distance'])
top3 = results[:3]

print(json.dumps(top3, ensure_ascii=False, indent=2))
