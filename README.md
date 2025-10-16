1️⃣ Python 설치 확인

Python 3.10 이상 설치되어 있어야 합니다.

설치 후 터미널에서 확인:

python --version


출력 예시:

Python 3.10.5


pip도 설치 확인:

pip --version

2️⃣ 가상환경 만들기 (선택이지만 권장)

프로젝트마다 패키지 버전을 안전하게 관리하려면 가상환경 추천:

cd D:\project\dev_boot_project\workspace\MyBaseLink\python
python -m venv venv


가상환경 활성화:

# Windows
venv\Scripts\activate


활성화되면 프롬프트가 (venv)로 표시됩니다.

3️⃣ 필수 패키지 설치

KRX 스크립트에서 필요한 패키지:

pandas

requests

lxml

beautifulsoup4 (선택)

설치 명령:

pip install pandas requests lxml beautifulsoup4

4️⃣ 설치 확인

Python에서 아래 명령 실행:

python -c "import pandas; import requests; import lxml; import bs4; print('모두 OK')"


출력:

모두 OK


✅ 여기까지 뜨면 Python 환경과 필수 패키지 세팅 완료

5️⃣ 스크립트 테스트

Python 스크립트 실행:

python krx_list_fetch.py


출력 예시:

테이블 컬럼명: ['회사명', '시장구분', '종목코드', '업종', '주요제품', '상장일', '결산월', '대표자명', '홈페이지', '지역']
KRX 데이터 수: 2763
krx_list_full.json 파일 생성 완료


정상적으로 JSON 파일이 생성되어야 합니다.

----------------
설명:

--upgrade pip → pip 최신화

scipy → DTW 계산에 필요

fastdtw → 빠른 DTW 계산

pandas → CSV 처리

& "C:\Users\User\AppData\Local\Programs\Python\Python310\python.exe" -m pip install --upgrade pip
& "C:\Users\User\AppData\Local\Programs\Python\Python310\python.exe" -m pip install scipy fastdtw pandas FinanceDataReader








-----------------
10 - 17

dragonzoroyj04 시작
pip install pandas numpy yfinance openpyxl


2️⃣ 타종목 다운로드

한국거래소(KRX) 또는 Yahoo Finance API 활용 가능

예를 들어 Yahoo Finance는 Python yfinance로 쉽게 다운로드 가능

CSV 파일명 = 종목명.csv (케이엔알시스템.csv, 삼성전자.csv 등)




그러면 목표는 KRX 상장 전체 종목 데이터를 자동으로 다운로드하고, 기준 종목과 비교해서 유사도 계산 후 JSON 반환하는 Python 스크립트입니다.

아래는 전체 통합 버전 초안입니다. 주요 포인트는:

기준 종목 CSV 없으면 자동 다운로드

타 종목 CSV 없으면 자동 다운로드 (KRX 전체 종목 대상)

시작일~종료일 기준 Close 가격 사용

Pandas + yfinance로 다운로드

코사인 유사도 계산 후 JSON 출력

--->>>


KRX 전체 종목 분석을 빠르게 수행할 수 있도록 멀티스레딩을 적용한 Python 스크립트 버전을 만들어드리겠습니다.

이 버전 특징:

기준 종목 CSV 다운로드/로드

KRX 전체 종목 리스트 조회

각 종목의 CSV 다운로드 및 Close 기준 정규화

코사인 유사도 계산

ThreadPoolExecutor로 병렬 처리 → 다운로드/계산 속도 개선

데이터 부족 시 자동 제외

----------------------------------------------------------
정말 로그아웃하시겠습니까?

ChatGPT에서 dragonzoroyj2@gmail.com 계정을 로그아웃하시겠습니까?



private final String pythonExe = "C:\\Users\\dragon\\AppData\\Local\\Programs\\Python\\Python310\\python.exe";
    private final String scriptPath = "D:\\project\\dev_boot_project\\workspace\\MyBaseLink\\python\\find_similar_full.py";
    


/MyBaseLink/src/main/java/com/mybaselink/app/service/SimilarStockAdvancedService.java

/MyBaseLink/src/main/java/com/mybaselink/app/controller/SimilarStockAdvancedController.java


/MyBaseLink/src/main/resources/templates/pages/stock/similar-advanced.html

/MyBaseLink/python/find_similar_full.py

/MyBaseLink/python/data

내구조는 이러하다 절대 바꾸지마

내가 하고싶은거는 실제 케이엔알시스템 종목의 시작일과 종료일 에 일봉 테이터로 타종목의 유사한 차트 종목을 찾고싶은거야

그래서 타종목을 /MyBaseLink/python/data 여기에 csv 파일로 여러종목을 저장하고

 유사정보를 가져와서 화면에 정보를 보여주는거야 
