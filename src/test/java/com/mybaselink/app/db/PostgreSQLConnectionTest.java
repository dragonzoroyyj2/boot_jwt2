package com.mybaselink.app.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import org.junit.jupiter.api.Test;

/**
 * 🔹 PostgreSQL JDBC 연결 테스트
 * - user010m 테이블 조회
 * - 컬럼: user_id, user_name, user_password
 */
public class PostgreSQLConnectionTest {

    // ⚠️ 실제 환경에서는 application.properties에서 불러오는 것을 권장
    private final String URL = "jdbc:postgresql://localhost:5432/mybaselink_db";
    private final String USERNAME = "mybaseuser";
    private final String PASSWORD = "mybaseuser1234";

    @Test
    public void connectionTest() {
        // try-with-resources로 자동 자원 종료
        try (Connection con = DriverManager.getConnection(URL, USERNAME, PASSWORD);
             Statement stmt = con.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT user_id, user_name, user_password FROM user010m"
             )) {

            System.out.println("✅ DB 연결 성공: " + con);

            // 결과 출력
            while (rs.next()) {
                String id = rs.getString("user_id");
                String name = rs.getString("user_name");
                String password = rs.getString("user_password");
                
                System.out.println("user_id: " + id 
                        + ", user_name: " + name 
                        + ", user_password: " + password);
            }

        } catch (Exception e) {
            System.err.println("❌ DB 연결 실패 또는 쿼리 오류");
            e.printStackTrace();
        }
    }
}
