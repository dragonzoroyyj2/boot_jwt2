# find_similar.py 예제 (간단)
import pandas as pd
from fastdtw import fastdtw
from scipy.spatial.distance import euclidean
import json

base_file = 'python/data/KR_system_base.csv'
candidates = ['python/data/stock1.csv', 'python/data/stock2.csv', 'python/data/stock3.csv']

df_base = pd.read_csv(base_file)
base_series = df_base['Close'].values

results = []
for file in candidates:
    df = pd.read_csv(file)
    target_series = df['Close'].values
    distance, _ = fastdtw(base_series, target_series, dist=euclidean)
    results.append({
        'file': file,
        'distance': distance,
        'dates': df['Date'].tolist(),
        'prices': df['Close'].tolist()
    })

top3 = sorted(results, key=lambda x: x['distance'])
print(json.dumps(top3))
