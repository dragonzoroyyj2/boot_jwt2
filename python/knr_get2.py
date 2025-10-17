import yfinance as yf
from datetime import datetime, timedelta

# 케이엔알시스템의 야후 파이낸스 티커 코드 (코스닥 상장)
ticker_symbol = "199430.KQ"

try:
    # 데이터 조회 종료일 설정 (오늘 날짜)
    end_date = datetime.today()
    # 데이터 조회 시작일 설정 (최근 1년)
    start_date = end_date - timedelta(days=365)

    # yfinance.download() 함수를 사용해 과거 주가 데이터 다운로드
    data = yf.download(ticker_symbol, start=start_date, end=end_date)

    if data.empty:
        print(f"[{ticker_symbol}] 해당 기간에 데이터를 찾을 수 없습니다. 티커 코드 또는 조회 기간을 확인하세요.")
    else:
        # 다운로드한 데이터 출력
        print("================= 과거 주가 데이터 (최근 1년) =================")
        print(data)

        # 마지막 거래일의 종가(Close) 정보만 출력
        # .iloc[-1]로 Series를 가져온 후, .item()으로 실제 float 값 추출
        yesterday_close = data['Close'].iloc[-1].item()
        
        last_trading_day = data.index[-1]

        print("\n==================== 어제 종가 정보 ====================")
        print(f"조회 날짜: {last_trading_day.strftime('%Y-%m-%d')}")
        print(f"종가: {yesterday_close:.2f}")

        # 원하는 경우 CSV 파일로 저장
        data.to_csv(f"{ticker_symbol}_historical_data.csv")
        print(f"\n[{ticker_symbol}]_historical_data.csv 파일로 저장되었습니다.")

except yf.exceptions.YFRateLimitError as e:
    print(f"오류: {e}")
    print("요청 제한에 걸렸습니다. 잠시 후 다시 시도해 주세요.")
except Exception as e:
    print(f"알 수 없는 오류가 발생했습니다: {e}")
