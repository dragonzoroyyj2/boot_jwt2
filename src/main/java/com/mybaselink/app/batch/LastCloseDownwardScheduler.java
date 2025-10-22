package com.mybaselink.app.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Component
public class LastCloseDownwardScheduler {

    private static final Logger logger = LoggerFactory.getLogger(LastCloseDownwardScheduler.class);
    private final LastCloseDownwardBatchService lastCloseDownwardBatchService;

    @Autowired
    public LastCloseDownwardScheduler(LastCloseDownwardBatchService lastCloseDownwardBatchService) {
        this.lastCloseDownwardBatchService = lastCloseDownwardBatchService;
    }

    /**
     * 매일 오전 2시에 캐시를 갱신합니다.
     * @CachePut이 적용된 서비스 메서드를 호출하여 기존 캐시를 유지하면서 새로운 값으로 갱신합니다.
     * cron = "초 분 시 일 월 요일"
     * 0 0 2 * * ? : 매일 2시 정각
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void prefetchAndCacheData() {
        logger.info("스케줄러 실행: 연속 하락 종목 캐시 갱신 시작");

        String endDate = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        
        try {
            // 1개월 전 데이터 갱신
            String start1Month = LocalDate.now().minusMonths(1).format(DateTimeFormatter.ISO_LOCAL_DATE);
            List<Map<String, Object>> result1Month = lastCloseDownwardBatchService.refreshAndPutLastCloseDownward(start1Month, endDate, 10);
            logger.info("1개월 데이터 캐시 갱신 완료. 결과 건수: {}", result1Month != null ? result1Month.size() : "null");
            
            // 3개월 전 데이터 갱신
            String start3Months = LocalDate.now().minusMonths(3).format(DateTimeFormatter.ISO_LOCAL_DATE);
            List<Map<String, Object>> result3Months = lastCloseDownwardBatchService.refreshAndPutLastCloseDownward(start3Months, endDate, 10);
            logger.info("3개월 데이터 캐시 갱신 완료. 결과 건수: {}", result3Months != null ? result3Months.size() : "null");
            
            // 6개월 전 데이터 갱신
            String start6Months = LocalDate.now().minusMonths(6).format(DateTimeFormatter.ISO_LOCAL_DATE);
            List<Map<String, Object>> result6Months = lastCloseDownwardBatchService.refreshAndPutLastCloseDownward(start6Months, endDate, 10);
            logger.info("6개월 데이터 캐시 갱신 완료. 결과 건수: {}", result6Months != null ? result6Months.size() : "null");

        } catch (Exception e) {
            logger.error("스케줄러 캐시 갱신 중 오류 발생", e);
        }

        logger.info("스케줄러 완료: 연속 하락 종목 캐시 갱신 작업 종료");
    }
}
