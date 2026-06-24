package com.aiflow.service.impl;

import com.aiflow.dto.StatisticsOverviewDTO;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatisticsServiceImplTest {

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private StatisticsServiceImpl service;

    @Test
    void overviewFallsBackWhenPredictionTableIsMissing() {
        Query totalQuery = mock(Query.class);
        Query rateQuery = mock(Query.class);
        Query avgQuery = mock(Query.class);
        Query anomalyPrimaryQuery = mock(Query.class);
        Query anomalyFallbackQuery = mock(Query.class);
        Query statusQuery = mock(Query.class);
        Query bizTypeQuery = mock(Query.class);
        Query todayQuery = mock(Query.class);
        Query pendingQuery = mock(Query.class);

        when(entityManager.createNativeQuery(eq("SELECT COUNT(*) FROM process_instance WHERE deleted = 0")))
                .thenReturn(totalQuery);
        when(totalQuery.getSingleResult()).thenReturn(5L);

        when(entityManager.createNativeQuery(argThat(sql ->
                sql != null
                        && sql.contains("COALESCE(SUM(CASE WHEN status = 'completed' THEN 1 ELSE 0 END), 0)"))))
                .thenReturn(rateQuery);
        when(rateQuery.getSingleResult()).thenReturn(new Object[]{5L, 3L});

        when(entityManager.createNativeQuery(argThat(sql ->
                sql != null && sql.contains("AVG(TIMESTAMPDIFF(SECOND, started_at, ended_at))"))))
                .thenReturn(avgQuery);
        when(avgQuery.getSingleResult()).thenReturn(7200.0);

        when(entityManager.createNativeQuery(argThat(sql ->
                sql != null && sql.contains("FROM bottleneck_prediction bp"))))
                .thenReturn(anomalyPrimaryQuery);
        when(anomalyPrimaryQuery.getSingleResult())
                .thenThrow(new PersistenceException("Table 'ai_workflow_mvp.bottleneck_prediction' doesn't exist"));

        when(entityManager.createNativeQuery(argThat(sql ->
                sql != null
                        && sql.contains("SELECT COUNT(DISTINCT si.instance_id) FROM (")
                        && sql.contains("FROM approval_record ar")
                        && !sql.contains("FROM bottleneck_prediction bp"))))
                .thenReturn(anomalyFallbackQuery);
        when(anomalyFallbackQuery.getSingleResult()).thenReturn(2L);

        when(entityManager.createNativeQuery(argThat(sql ->
                sql != null && sql.contains("GROUP BY pi.status"))))
                .thenReturn(statusQuery);
        when(statusQuery.getResultList()).thenReturn(List.<Object[]>of(new Object[]{"running", 2L}));

        when(entityManager.createNativeQuery(argThat(sql ->
                sql != null && sql.contains("LEFT JOIN biz_type_dict btd ON pi.biz_type_id = btd.id"))))
                .thenReturn(bizTypeQuery);
        when(bizTypeQuery.getResultList()).thenReturn(List.<Object[]>of(new Object[]{1L, "HR", 2L}));

        when(entityManager.createNativeQuery(eq(
                "SELECT COUNT(*) FROM process_instance WHERE deleted=0 AND DATE(created_at)=CURDATE()")))
                .thenReturn(todayQuery);
        when(todayQuery.getSingleResult()).thenReturn(1L);

        when(entityManager.createNativeQuery(argThat(sql ->
                sql != null && sql.contains("JOIN sys_user su ON t.assignee_id = su.id"))))
                .thenReturn(pendingQuery);
        when(pendingQuery.setParameter("userId", 1L)).thenReturn(pendingQuery);
        when(pendingQuery.getSingleResult()).thenReturn(4L);

        StatisticsOverviewDTO overview = service.getOverview();

        assertThat(overview.getTotalInstances()).isEqualTo(5L);
        assertThat(overview.getCompletionRate()).isEqualTo(60.0);
        assertThat(overview.getAvgDurationHours()).isEqualTo(2.0);
        assertThat(overview.getAnomalyCount()).isEqualTo(2L);
        assertThat(overview.getStatusDistribution()).containsEntry("running", 2L);
        assertThat(overview.getBizTypeDistribution()).singleElement().satisfies(item -> {
            assertThat(item.getBizTypeId()).isEqualTo(1L);
            assertThat(item.getBizTypeName()).isEqualTo("HR");
            assertThat(item.getCount()).isEqualTo(2L);
        });
        assertThat(overview.getTodayNewInstances()).isEqualTo(1L);
        assertThat(overview.getPendingTaskCount()).isEqualTo(4L);
    }
}
