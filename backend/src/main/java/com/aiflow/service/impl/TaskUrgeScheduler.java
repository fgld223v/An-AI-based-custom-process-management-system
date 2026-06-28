package com.aiflow.service.impl;

import com.aiflow.model.ProcessInstance;
import com.aiflow.repository.ProcessInstanceRepository;
import com.aiflow.service.TaskUrgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

/**
 * 任务催办定时调度器，按固定频率扫描超时未处理的任务并自动触发催办通知。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskUrgeScheduler {

    private final TaskService taskService;
    private final TaskUrgeService taskUrgeService;
    private final ProcessInstanceRepository processInstanceRepository;

    @Value("${notification.urge-threshold-hours:24}")
    private long urgeThresholdHours;

    @Scheduled(fixedRateString = "${notification.urge-scan-fixed-rate-ms:60000}")
    @Transactional
    public void scanAndUrgeTimeoutTasks() {
        LocalDateTime deadline = LocalDateTime.now().minusHours(urgeThresholdHours);
        List<Task> tasks = taskService.createTaskQuery()
                .taskCreatedBefore(toDate(deadline))
                .active()
                .list();

        int urgedCount = 0;
        for (Task task : tasks) {
            ProcessInstance instance = processInstanceRepository
                    .findByFlowableProcessInstanceIdAndDeleted(task.getProcessInstanceId(), 0)
                    .orElse(null);
            if (instance == null || !"running".equals(instance.getStatus())) {
                continue;
            }
            if (taskUrgeService.autoUrgeTask(task, instance)) {
                urgedCount++;
            }
        }
        if (urgedCount > 0) {
            log.info("Auto urge scan finished. urged={}", urgedCount);
        }
    }

    private Date toDate(LocalDateTime dateTime) {
        return Date.from(dateTime.atZone(ZoneId.systemDefault()).toInstant());
    }
}
