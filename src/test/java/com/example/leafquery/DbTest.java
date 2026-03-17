package com.example.leafquery;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

@SpringBootTest
public class DbTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    public void checkTables() {
        System.out.println("====== CHECKING DB TABLES ======");
        List<String> tables = jdbcTemplate.queryForList("SHOW TABLES", String.class);
        System.out.println("Tables in database: " + tables);

        if (tables.contains("user")) {
            System.out.println("USER TABLE EXISTS! Columns:");
            List<java.util.Map<String, Object>> columns = jdbcTemplate.queryForList("SHOW COLUMNS FROM user");
            for (java.util.Map<String, Object> col : columns) {
                System.out.println(col.get("Field") + " - " + col.get("Type"));
            }
        } else {
            System.out.println("USER TABLE DOES NOT EXIST!");
        }
        System.out.println("================================");
    }
}
