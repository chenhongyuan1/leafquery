package com.example.leafquery.service;

import com.example.leafquery.controller.RecordController;
import com.example.leafquery.mapper.RecordMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Actual request DTO, service and MyBatis SQL against an isolated database. */
class RecordReviewPersistenceTests {
    private JdbcTemplate jdbc;
    private MockMvc mvc;

    @BeforeEach
    void migratedDatabase() throws Exception {
        var source = new DriverManagerDataSource("jdbc:h2:mem:review_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("""
            CREATE TABLE identification_record (
                id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL,
                crop_id BIGINT, crop_name VARCHAR(64), pest_name VARCHAR(128) NOT NULL,
                confidence DOUBLE NOT NULL, location_id VARCHAR(32), city VARCHAR(64),
                region VARCHAR(64), image_url LONGTEXT, create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """);
        jdbc.update("INSERT INTO identification_record (user_id, pest_name, confidence) VALUES (7, '旧记录', 0)");
        new ResourceDatabasePopulator(new ClassPathResource("migrations/20261106-record-review.sql")).execute(source);
        var factory = new SqlSessionFactoryBean();
        factory.setDataSource(source);
        factory.setMapperLocations(new ClassPathResource("mapper/RecordMapper.xml"));
        var mapper = new SqlSessionTemplate(factory.getObject()).getMapper(RecordMapper.class);
        var service = new RecordService();
        ReflectionTestUtils.setField(service, "recordMapper", mapper);
        var controller = new RecordController();
        ReflectionTestUtils.setField(controller, "recordService", service);
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @AfterEach
    void closeDatabase() { if (jdbc != null) jdbc.execute("SHUTDOWN"); }

    @Test
    void reviewAndModelConfidenceSurviveSavingAndReloading() throws Exception {
        mvc.perform(post("/api/record/add").contentType(MediaType.APPLICATION_JSON).content("""
            {"userId":8,"pestName":"稻瘟病","confidence":0.9234,"imageUrl":"/images/test.jpg",
             "yoloUsed":true,"reviewRequired":true,"reviewResult":"【确诊结果】：稻瘟病\\n【建议】：先观察叶片。","reviewError":""}
            """))
            .andExpect(status().isOk());
        mvc.perform(get("/api/record/list").param("userId", "8"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].confidence").value(0.9234))
            .andExpect(jsonPath("$.data[0].yoloUsed").value(true))
            .andExpect(jsonPath("$.data[0].reviewRequired").value(true))
            .andExpect(jsonPath("$.data[0].reviewResult").value("【确诊结果】：稻瘟病\n【建议】：先观察叶片。"))
            .andExpect(jsonPath("$.data[0].reviewError").value(""));
    }

    @Test
    void oldRecordIsPreservedAndUnknownReviewIsNotInvented() throws Exception {
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM identification_record", Integer.class));
        assertNull(jdbc.queryForObject("SELECT review_required FROM identification_record WHERE id=1", Boolean.class));
        mvc.perform(get("/api/record/list").param("userId", "7"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].pestName").value("旧记录"))
            .andExpect(jsonPath("$.data[0].confidence").value(0))
            .andExpect(jsonPath("$.data[0].reviewResult").isEmpty());
    }

    @Test
    void skippedYoloAndFailedReviewRemainDistinguishable() throws Exception {
        mvc.perform(post("/api/record/add").contentType(MediaType.APPLICATION_JSON).content("""
            {"userId":9,"pestName":"未能识别","confidence":0,"yoloUsed":false,
             "reviewRequired":true,"reviewResult":"","reviewError":"复核服务暂不可用"}
            """))
            .andExpect(status().isOk());
        mvc.perform(get("/api/record/list").param("userId", "9"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].yoloUsed").value(false))
            .andExpect(jsonPath("$.data[0].reviewRequired").value(true))
            .andExpect(jsonPath("$.data[0].reviewError").value("复核服务暂不可用"));
    }
}
