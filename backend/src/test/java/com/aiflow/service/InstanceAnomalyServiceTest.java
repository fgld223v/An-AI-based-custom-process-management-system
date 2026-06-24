package com.aiflow.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InstanceAnomalyServiceTest {

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private InstanceAnomalyService service;

    @Test
    void fallsBackWhenPredictionTableIsMissing() {
        Query primaryQuery = mock(Query.class);
        Query fallbackQuery = mock(Query.class);
        List<Long> instanceIds = List.of(10L, 11L);

        when(entityManager.createNativeQuery(argThat(sql ->
                sql != null && sql.contains("FROM bottleneck_prediction bp"))))
                .thenReturn(primaryQuery);
        when(primaryQuery.setParameter("instanceIds", instanceIds)).thenReturn(primaryQuery);
        when(primaryQuery.getResultList())
                .thenThrow(new PersistenceException("Table 'ai_workflow_mvp.bottleneck_prediction' doesn't exist"));

        when(entityManager.createNativeQuery(argThat(sql ->
                sql != null
                        && sql.contains("GROUP BY anomaly.instance_id")
                        && !sql.contains("FROM bottleneck_prediction bp"))))
                .thenReturn(fallbackQuery);
        when(fallbackQuery.setParameter("instanceIds", instanceIds)).thenReturn(fallbackQuery);
        when(fallbackQuery.getResultList()).thenReturn(List.<Object[]>of(new Object[]{10L, "任务超时"}));

        Map<Long, String> result = service.findAnomalies(instanceIds);

        assertThat(result).containsEntry(10L, "任务超时");
    }
}
