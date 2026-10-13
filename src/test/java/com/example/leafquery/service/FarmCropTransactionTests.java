package com.example.leafquery.service;

import com.example.leafquery.dto.PhenologyEstimateResponse;
import com.example.leafquery.entity.FarmCrop;
import com.example.leafquery.mapper.FarmCropMapper;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real mapper SQL and Spring transaction proxy, isolated from the application database. */
@SpringJUnitConfig(FarmCropTransactionTests.TestConfiguration.class)
class FarmCropTransactionTests {
    @Autowired FarmCropService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired FailureProbe probe;
    @Autowired AgroZoneService zones;
    @Autowired PhenologyEstimateService phenology;

    @BeforeEach
    void seedIsolatedDatabase() {
        probe.reset();
        reset(zones, phenology);
        when(zones.normalize(anyString(), anyString())).thenAnswer(call -> call.getArgument(0));
        when(phenology.estimate(any(FarmCrop.class), any(LocalDate.class)))
                .thenReturn(new PhenologyEstimateResponse());
        jdbc.update("DELETE FROM user_crop");
        jdbc.update("DELETE FROM `user`");
        jdbc.update("INSERT INTO `user` (user_id) VALUES (1), (2)");
        seedCrop(11L, 1L, "corn", true);
        seedCrop(12L, 1L, "rice", false);
    }

    @Test
    void failedSwitchRestoresOriginalCurrentCrop() {
        probe.failBefore("setActiveCrop");
        assertThrows(RuntimeException.class, () -> service.setActiveCrop(12L, 1L));
        assertEquals(11L, currentCrop(1L));
        assertEquals(1, activeCount(1L));
    }

    @Test
    void failedCreateRemovesPartialNewCropAndRestoresSelection() {
        probe.failBefore("setActiveCrop");
        assertThrows(RuntimeException.class, () -> service.createCrop(crop(1L, "wheat", true)));
        assertEquals(2, cropCount(1L));
        assertEquals(11L, currentCrop(1L));
    }

    @Test
    void failedUpdateRestoresCropFieldsAndSelection() {
        probe.failBefore("setActiveCrop");
        assertThrows(RuntimeException.class, () -> service.updateCrop(12L, crop(1L, "updated-rice", true)));
        assertEquals("rice", jdbc.queryForObject("SELECT crop_name FROM user_crop WHERE crop_id = 12", String.class));
        assertEquals(11L, currentCrop(1L));
    }

    @Test
    void failedFallbackRestoresDeletedCurrentCrop() {
        probe.failBefore("setActiveCrop");
        assertThrows(RuntimeException.class, () -> service.deleteCrop(11L, 1L));
        assertEquals(2, cropCount(1L));
        assertEquals(11L, currentCrop(1L));
    }

    @Test
    void zeroRowActivationIsFailureAndRestoresSelection() {
        probe.zeroRowsFor("setActiveCrop");
        assertThrows(IllegalStateException.class, () -> service.setActiveCrop(12L, 1L));
        assertEquals(11L, currentCrop(1L));
    }

    @Test
    void invalidCropOrOwnerCannotChangeAnotherUsersSelection() {
        assertNull(service.setActiveCrop(999L, 1L));
        assertNull(service.setActiveCrop(11L, 2L));
        assertNull(service.updateCrop(11L, crop(2L, "foreign", true)));
        assertFalse(service.deleteCrop(11L, 2L));
        assertEquals(11L, currentCrop(1L));
        assertEquals(2, cropCount(1L));
    }

    @Test
    void firstCropIsActivatedAndSwitchingOnlyChangesItsOwner() {
        FarmCrop first = service.createCrop(crop(2L, "wheat", false));
        assertTrue(first.getIsActive());
        FarmCrop second = service.createCrop(crop(2L, "rice", false));
        assertFalse(second.getIsActive());
        assertEquals(second.getCropId(), service.setActiveCrop(second.getCropId(), 2L).getCropId());
        assertEquals(1, activeCount(2L));
        assertEquals(11L, currentCrop(1L));
    }

    @Test
    void editingInactiveCropKeepsCurrentSelection() {
        FarmCrop updated = service.updateCrop(12L, crop(1L, "new-rice", false));
        assertEquals("new-rice", updated.getCropName());
        assertEquals(11L, currentCrop(1L));
    }

    @Test
    void deletingCurrentCropSelectsFallbackAndDeletingLastCropLeavesEmptyFarm() {
        assertTrue(service.deleteCrop(11L, 1L));
        assertEquals(12L, currentCrop(1L));
        assertTrue(service.deleteCrop(12L, 1L));
        assertEquals(0, cropCount(1L));
        assertEquals(0, activeCount(1L));
    }

    @Test
    void simultaneousFirstCropCreationsLeaveExactlyOneCurrentCrop() throws Exception {
        probe.pauseAfter("clearActiveByUserId");
        overlap(() -> service.createCrop(crop(2L, "corn", false)),
                () -> service.createCrop(crop(2L, "rice", false)));
        assertEquals(2, cropCount(2L));
        assertEquals(1, activeCount(2L));
    }

    @Test
    void simultaneousSwitchesLeaveExactlyOneCurrentCrop() throws Exception {
        probe.pauseAfter("clearActiveByUserId");
        overlap(() -> service.setActiveCrop(12L, 1L), () -> service.setActiveCrop(11L, 1L));
        assertEquals(1, activeCount(1L));
        assertEquals(11L, currentCrop(1L));
    }

    @Test
    void automaticStageRefreshCannotRestoreAStaleCurrentFlag() throws Exception {
        jdbc.update("UPDATE user_crop SET stage_mode = 'AUTO', stage_evaluated_at = NULL WHERE crop_id = 11");
        PhenologyEstimateResponse estimate = new PhenologyEstimateResponse();
        estimate.setSupported(true);
        estimate.setEstimatedStage("flowering");
        when(phenology.estimate(any(FarmCrop.class), any(LocalDate.class))).thenReturn(estimate);
        probe.pauseAfter("selectByUserId");
        overlap(() -> service.getUserCrops(1L), () -> service.setActiveCrop(12L, 1L));
        assertEquals(1, activeCount(1L));
        assertEquals(12L, currentCrop(1L));
        assertEquals("flowering", jdbc.queryForObject("SELECT stage FROM user_crop WHERE crop_id = 11", String.class));
    }

    private void overlap(Callable<?> firstOperation, Callable<?> secondOperation) throws Exception {
        var workers = Executors.newFixedThreadPool(2);
        try {
            var first = workers.submit(firstOperation);
            assertTrue(probe.paused.await(5, TimeUnit.SECONDS), "First request did not reach its database checkpoint");
            var second = workers.submit(secondOperation);
            try {
                // Old code lets the second request finish while the first is half-written.
                // Fixed code waits on the same user row until the first transaction commits.
                second.get(500, TimeUnit.MILLISECONDS);
            } catch (TimeoutException expectedWhileLocked) {
                // Release the first request below; this is the expected serialized execution.
            } finally {
                probe.resume.countDown();
            }
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
        } finally {
            probe.resume.countDown();
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private FarmCrop crop(Long userId, String name, boolean active) {
        FarmCrop crop = new FarmCrop();
        crop.setUserId(userId);
        crop.setCropName(name);
        crop.setStage("seedling");
        crop.setStageMode("MANUAL");
        crop.setIsActive(active);
        return crop;
    }

    private void seedCrop(Long id, Long userId, String name, boolean active) {
        jdbc.update("INSERT INTO user_crop (crop_id, user_id, crop_name, stage, stage_mode, is_active) VALUES (?, ?, ?, 'seedling', 'MANUAL', ?)",
                id, userId, name, active);
    }

    private int cropCount(Long userId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM user_crop WHERE user_id = ?", Integer.class, userId);
    }

    private int activeCount(Long userId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM user_crop WHERE user_id = ? AND is_active = TRUE", Integer.class, userId);
    }

    private Long currentCrop(Long userId) {
        return jdbc.queryForObject("SELECT crop_id FROM user_crop WHERE user_id = ? AND is_active = TRUE", Long.class, userId);
    }

    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    static class TestConfiguration {
        @Bean DataSource dataSource() {
            DriverManagerDataSource source = new DriverManagerDataSource(
                    "jdbc:h2:mem:farm_crop_transaction_tests;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=3000;NON_KEYWORDS=USER", "sa", "");
            new ResourceDatabasePopulator(new ClassPathResource("farm-crop-test-schema.sql")).execute(source);
            return source;
        }

        @Bean PlatformTransactionManager transactionManager(DataSource source) {
            return new DataSourceTransactionManager(source);
        }

        @Bean JdbcTemplate jdbcTemplate(DataSource source) { return new JdbcTemplate(source); }
        @Bean FailureProbe failureProbe() { return new FailureProbe(); }

        @Bean SqlSessionFactory sqlSessionFactory(DataSource source, FailureProbe probe) throws Exception {
            SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
            factory.setDataSource(source);
            factory.setMapperLocations(new ClassPathResource("mapper/FarmCropMapper.xml"));
            factory.setPlugins(probe);
            return factory.getObject();
        }

        @Bean FarmCropMapper farmCropMapper(SqlSessionFactory factory) {
            return new SqlSessionTemplate(factory).getMapper(FarmCropMapper.class);
        }

        @Bean AgroZoneService agroZoneService() { return mock(AgroZoneService.class); }
        @Bean PhenologyEstimateService phenologyEstimateService() { return mock(PhenologyEstimateService.class); }

        @Bean FarmCropService farmCropService(FarmCropMapper mapper, AgroZoneService zones, PhenologyEstimateService phenology) {
            return new FarmCropService(mapper, zones, phenology);
        }
    }

    @Intercepts({
            @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class}),
            @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class})
    })
    static class FailureProbe implements Interceptor {
        private volatile String failure;
        private volatile String zeroRows;
        private volatile String pause;
        private final AtomicBoolean checkpointUsed = new AtomicBoolean();
        private CountDownLatch paused;
        private CountDownLatch resume;

        void reset() {
            failure = null;
            zeroRows = null;
            pause = null;
            checkpointUsed.set(false);
            paused = new CountDownLatch(1);
            resume = new CountDownLatch(1);
        }

        void failBefore(String method) { failure = method; }
        void zeroRowsFor(String method) { zeroRows = method; }
        void pauseAfter(String method) { pause = method; }

        @Override
        public Object intercept(Invocation call) throws Throwable {
            String id = ((MappedStatement) call.getArgs()[0]).getId();
            String method = id.substring(id.lastIndexOf('.') + 1);
            if (method.equals(failure)) { throw new IllegalStateException("Injected database write failure"); }
            if (method.equals(zeroRows)) { return 0; }
            Object result = call.proceed();
            if (method.equals(pause) && checkpointUsed.compareAndSet(false, true)) {
                paused.countDown();
                if (!resume.await(10, TimeUnit.SECONDS)) { throw new IllegalStateException("Database checkpoint timed out"); }
            }
            return result;
        }
    }
}
