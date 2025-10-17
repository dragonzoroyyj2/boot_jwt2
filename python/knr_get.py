import yfinance as yf

# 케이엔알시스템의 야후 파이낸스 종목 코드
ticker = "199430.KQ"

# 해당 종목의 주식 정보 가져오기
knr_system = yf.Ticker(ticker)

# 최근 주가 정보 출력
history = knr_system.history(period="1mo")
print(history)
