package com.mybaselink.app.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 🔹 PostgreSQL JDBC 연결 테스트 (application.properties 기반)
 * - user010m 테이블 조회
 * - 컬럼: user_id, user_name, user_password
 */
@SpringBootTest
public class PostgreSQLConnectionTestProperties {

    @Value("${spring.datasource.url}")
    private String URL;

    @Value("${spring.datasource.username}")
    private String USERNAME;

    @Value("${spring.datasource.password}")
    private String PASSWORD;

    @Test
    public void connectionTest() {
        try (Connection con = DriverManager.getConnection(URL, USERNAME, PASSWORD);
             Statement stmt = con.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT user_id, user_name, user_password FROM user010m"
             )) {

            System.out.println("✅ DB 연결 성공: " + con);

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
