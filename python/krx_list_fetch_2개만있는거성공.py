import requests
import pandas as pd
import json

def fetch_krx():
    url = "http://kind.krx.co.kr/corpgeneral/corpList.do?method=download&searchType=13"
    try:
        response = requests.get(url)
        response.raise_for_status()
        df = pd.read_html(response.content, encoding='CP949')[0]
        df = df[['종목코드', '회사명']]
        df.columns = ['code', 'name']
        return df
    except Exception as e:
        print(f"KRX 데이터 fetch 실패: {e}")
        return pd.DataFrame()

def save_json(df):
    if df.empty:
        print("KRX 전체 데이터가 비어 있습니다.")
        return

    df["code"] = df["code"].apply(lambda x: str(x).zfill(6))
    json_path = r"D:\project\dev_boot_project\workspace\MyBaseLink\python\krx_list_full.json"
    try:
        df.to_json(json_path, orient='records', force_ascii=False, indent=4)
        print(f"{json_path} 파일 생성 완료")
    except Exception as e:
        print(f"JSON 저장 실패: {e}")

if __name__ == "__main__":
    df_krx = fetch_krx()
    print(f"KRX 데이터 수: {len(df_krx)}")
    save_json(df_krx)
