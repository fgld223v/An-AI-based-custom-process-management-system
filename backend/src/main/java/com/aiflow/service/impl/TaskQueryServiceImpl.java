package com.aiflow.service.impl;

import com.aiflow.dto.TaskDTO;
import com.aiflow.model.ApprovalRecord;
import com.aiflow.model.ProcessInstance;
import com.aiflow.model.ProcessTemplate;
import com.aiflow.repository.ApprovalRecordRepository;
import com.aiflow.repository.ProcessInstanceRepository;
import com.aiflow.repository.ProcessTemplateRepository;
import com.aiflow.security.SecurityUtils;
import com.aiflow.service.TaskQueryService;
import com.aiflow.service.TaskAuthorizationService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 任务查询服务实现 — 双数据源架构。
 *
 * <p>职责：查询用户的待办和已办任务，并提供单任务详情查询。</p>
 *
 * <ul>
 *   <li><b>待办任务</b> — 通过 TaskService 查询 ACT_RU_TASK（运行中任务表）</li>
 *   <li><b>已办任务</b> — 通过 HistoryService 查询 ACT_HI_TASKINST（历史任务表），
 *       并结合 approval_record 审批记录作为权威审计追踪进行补全</li>
 *   <li><b>权限控制</b> — 所有查询结果仅包含当前用户有权限查看的任务</li>
 * </ul>
 *
 * <p>注意：本类中所有方法均标记为只读事务（readOnly = true），
 * 确保不会对 Flowable 运行时数据产生意外修改。</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TaskQueryServiceImpl implements TaskQueryService {

    /** 任务状态常量：活跃/运行中 */
    private static final String STATUS_ACTIVE = "active";
    /** 任务状态常量：已完成 */
    private static final String STATUS_COMPLETED = "completed";

    // Flowable 服务注入
    private final TaskService taskService;          // 运行中任务查询（ACT_RU_TASK）
    private final HistoryService historyService;    // 历史任务查询（ACT_HI_TASKINST）
    private final RuntimeService runtimeService;    // 流程运行时变量查询

    // 业务数据访问
    private final ProcessInstanceRepository processInstanceRepository;
    private final ProcessTemplateRepository processTemplateRepository;
    private final ObjectMapper objectMapper;
    private final FormBindConfigParser formBindConfigParser;
    private final TaskAuthorizationService taskAuthorizationService;  // 任务权限校验
    private final ApprovalRecordRepository approvalRecordRepository;  // 审批记录（审计追踪）

    // ========================================================================
    // 待办（ACT_RU_TASK）
    // ========================================================================

    /**
     * 查询当前用户的待办任务列表。
     *
     * <p>实现步骤：</p>
     * <ol>
     *   <li>获取当前登录用户的 ID</li>
     *   <li>通过 TaskService 查询分配给当前用户或当前用户为候选人的活跃任务</li>
     *   <li>按创建时间降序排列</li>
     *   <li>关联业务 ProcessInstance，仅保留存在且未删除的实例</li>
     *   <li>转换为 TaskDTO 并返回</li>
     * </ol>
     */
    @Override
    public List<TaskDTO> listMyTasks() {
        // 按当前用户过滤：查询分配给当前用户或候选组的任务
        Long currentUserId = SecurityUtils.currentUserId();
        String userIdStr = currentUserId != null ? String.valueOf(currentUserId) : null;

        // 查询运行中的待办任务：assignee 匹配 或 候选用户匹配
        List<Task> tasks = taskService.createTaskQuery()
                .or()
                    .taskAssignee(userIdStr)
                    .taskCandidateUser(userIdStr)
                .endOr()
                .orderByTaskCreateTime().desc()
                .list();

        // 关联业务实例，过滤掉已删除的实例
        List<TaskDTO> result = new ArrayList<>();
        for (Task task : tasks) {
            ProcessInstance instance = processInstanceRepository
                    .findByFlowableProcessInstanceIdAndDeleted(task.getProcessInstanceId(), 0)
                    .orElse(null);
            if (instance == null) {
                continue;  // 跳过已删除或不存在业务实例的任务
            }
            result.add(toTaskDTO(task, instance));
        }
        return result;
    }

    // ========================================================================
    // 已办（ACT_HI_TASKINST）
    // ========================================================================

    /**
     * 查询当前用户的已办任务列表。
     *
     * <p>双路合并策略：</p>
     * <ol>
     *   <li><b>路径 1 — 历史任务查询</b>：通过 HistoryService 查询 assignee 为当前用户的已结束任务
     *       （ACT_HI_TASKINST），按结束时间降序排列。</li>
     *   <li><b>路径 2 — 审批记录补全</b>：某些通过 CREATE 监听器分配的任务，
     *       其 ASSIGNEE_ 字段在历史表中可能为空。
     *       此时以 approval_record 作为权威审计追踪，通过 taskId 反查历史任务进行补全。</li>
     *   <li><b>去重</b>：使用 LinkedHashMap 按 taskId 去重，保留插入顺序。</li>
     *   <li>关联业务实例并转换为 DTO，缺失 assignee 的用当前用户 ID 回填。</li>
     *   <li>按结束时间降序重新排序。</li>
     * </ol>
     */
    @Override
    public List<TaskDTO> listDoneTasks() {
        Long currentUserId = SecurityUtils.currentUserId();
        if (currentUserId == null) {
            return List.of();
        }
        String userIdStr = String.valueOf(currentUserId);

        List<HistoricTaskInstance> historicTasks = historyService
                .createHistoricTaskInstanceQuery()
                .taskAssignee(userIdStr)
                .finished()
                .orderByHistoricTaskInstanceEndTime().desc()
                .list();

        // 使用 LinkedHashMap 去重并保持顺序
        Map<String, HistoricTaskInstance> tasksById = new LinkedHashMap<>();
        historicTasks.forEach(task -> tasksById.put(task.getId(), task));

        // 路径2：通过审批记录补全缺失的已办任务
        // 原因：某些通过 CREATE 监听器分配的任务，ACT_HI_TASKINST 中 ASSIGNEE_ 为空，
        //       approval_record 才是记录实际操作人的权威审计追踪
        List<ApprovalRecord> approvalRecords = approvalRecordRepository
                .findByApproverIdAndTaskIdIsNotNullOrderByOperatedAtDesc(currentUserId);
        for (ApprovalRecord record : approvalRecords) {
            if (!hasText(record.getTaskId()) || tasksById.containsKey(record.getTaskId())) {
                continue;
            }
            HistoricTaskInstance historicTask = historyService
                    .createHistoricTaskInstanceQuery()
                    .taskId(record.getTaskId())
                    .finished()
                    .singleResult();
            if (historicTask != null) {
                tasksById.put(historicTask.getId(), historicTask);
            }
        }

        List<TaskDTO> result = new ArrayList<>();
        for (HistoricTaskInstance ht : tasksById.values()) {
            ProcessInstance instance = processInstanceRepository
                    .findByFlowableProcessInstanceIdAndDeleted(ht.getProcessInstanceId(), 0)
                    .orElse(null);
            if (instance == null) {
                continue;
            }
            TaskDTO dto = toTaskDTO(ht, instance);
            if (!hasText(dto.getAssignee())) {
                dto.setAssignee(userIdStr);
            }
            result.add(dto);
        }
        result.sort((left, right) -> {
            if (left.getEndTime() == null) return right.getEndTime() == null ? 0 : 1;
            if (right.getEndTime() == null) return -1;
            return right.getEndTime().compareTo(left.getEndTime());
        });
        return result;
    }

    // ========================================================================
    // 单任务查询（ACT_RU_TASK → 不存在则 ACT_HI_TASKINST）
    // ========================================================================

    /**
     * 查询单个任务详情，先查运行中任务，再查历史任务。
     *
     * <p>权限校验：通过 TaskAuthorizationService 校验当前用户是否有权查看该任务。
     * 如果用户曾在审批记录中被记录为该任务的审批人，则跳过权限校验。</p>
     *
     * @param taskId Flowable 任务 ID
     * @return 任务 DTO，含业务实例信息
     * @throws IllegalArgumentException 如果任务不存在或关联的业务实例不存在
     */
    @Override
    public TaskDTO getTask(String taskId) {
        // 步骤1：先查运行中任务（ACT_RU_TASK）
        Task task = taskService.createTaskQuery()
                .taskId(taskId)
                .singleResult();
        if (task != null) {
            // 权限校验：当前用户必须有权查看该运行中任务
            taskAuthorizationService.assertCanView(task);
            ProcessInstance instance = processInstanceRepository
                    .findByFlowableProcessInstanceIdAndDeleted(task.getProcessInstanceId(), 0)
                    .orElseThrow(() -> new IllegalArgumentException("关联的业务流程实例不存在。"));
            return toTaskDTO(task, instance);
        }

        // 步骤2：运行中未找到，回退到历史任务（ACT_HI_TASKINST）
        HistoricTaskInstance historicTask = historyService
                .createHistoricTaskInstanceQuery()
                .taskId(taskId)
                .singleResult();
        if (historicTask == null) {
            throw new IllegalArgumentException("任务不存在。");
        }
        // 权限放宽：如果用户在审批记录中存在，则跳过权限校验（历史审计可见性）
        Long currentUserId = SecurityUtils.currentUserId();
        boolean recordedApprover = currentUserId != null
                && approvalRecordRepository.existsByTaskIdAndApproverId(taskId, currentUserId);
        if (!recordedApprover) {
            taskAuthorizationService.assertCanView(historicTask);
        }

        ProcessInstance instance = processInstanceRepository
                .findByFlowableProcessInstanceIdAndDeleted(historicTask.getProcessInstanceId(), 0)
                .orElseThrow(() -> new IllegalArgumentException("关联的业务流程实例不存在。"));
        TaskDTO dto = toTaskDTO(historicTask, instance);
        // 历史任务可能缺失 assignee，用当前用户 ID 回填
        if (!hasText(dto.getAssignee()) && recordedApprover) {
            dto.setAssignee(String.valueOf(currentUserId));
        }
        return dto;
    }

    // ========================================================================
    // DTO 映射
    // ========================================================================

    /**
     * 从 ACT_RU_TASK 映射 TaskDTO，含多实例信息。
     */
    private TaskDTO toTaskDTO(Task task, ProcessInstance instance) {
        Long formId = resolveFormId(instance.getTemplateId(), task.getTaskDefinitionKey());
        TaskDTO dto = TaskDTO.builder()
                .taskId(task.getId())
                .taskName(task.getName())
                .taskDefinitionKey(task.getTaskDefinitionKey())
                .flowableProcessInstanceId(task.getProcessInstanceId())
                .businessInstanceId(instance.getId())
                .instanceCode(instance.getInstanceCode())
                .instanceTitle(instance.getTitle())
                .assignee(task.getAssignee())
                .createTime(task.getCreateTime() != null
                        ? task.getCreateTime().toInstant()
                            .atZone(java.time.ZoneId.systemDefault())
                            .toLocalDateTime()
                        : null)
                .dueDate(task.getDueDate() != null
                        ? task.getDueDate().toInstant()
                            .atZone(java.time.ZoneId.systemDefault())
                            .toLocalDateTime()
                        : null)
                .endTime(null)
                .status(STATUS_ACTIVE)
                .formId(formId)
                .businessType(resolveBusinessType(instance.getTemplateId(), task.getTaskDefinitionKey()))
                .build();

        // 填充多实例（会签/或签）相关信息
        enrichMultiInstanceInfo(dto, task, instance.getTemplateId());
        return dto;
    }

    /**
     * 从 ACT_HI_TASKINST 映射 TaskDTO。
     */
    private TaskDTO toTaskDTO(HistoricTaskInstance historicTask, ProcessInstance instance) {
        Long formId = resolveFormId(instance.getTemplateId(), historicTask.getTaskDefinitionKey());
        return TaskDTO.builder()
                .taskId(historicTask.getId())
                .taskName(historicTask.getName())
                .taskDefinitionKey(historicTask.getTaskDefinitionKey())
                .flowableProcessInstanceId(historicTask.getProcessInstanceId())
                .businessInstanceId(instance.getId())
                .instanceCode(instance.getInstanceCode())
                .instanceTitle(instance.getTitle())
                .assignee(historicTask.getAssignee())
                .businessType(resolveBusinessType(instance.getTemplateId(), historicTask.getTaskDefinitionKey()))
                .createTime(historicTask.getCreateTime() != null
                        ? historicTask.getCreateTime().toInstant()
                            .atZone(java.time.ZoneId.systemDefault())
                            .toLocalDateTime()
                        : null)
                .dueDate(historicTask.getDueDate() != null
                        ? historicTask.getDueDate().toInstant()
                            .atZone(java.time.ZoneId.systemDefault())
                            .toLocalDateTime()
                        : null)
                .endTime(historicTask.getEndTime() != null
                        ? historicTask.getEndTime().toInstant()
                            .atZone(java.time.ZoneId.systemDefault())
                            .toLocalDateTime()
                        : null)
                .status(STATUS_COMPLETED)
                .formId(formId)
                .build();
    }

    // ================================================================
    // 多实例信息增强
    // ================================================================

    /**
     * 为 TaskDTO 填充多实例（会签/或签）相关信息。
     *
     * <p>填充内容：</p>
     * <ol>
     *   <li>从模板 nodeConfig 解析 approvalMode（SINGLE / ALL / ANY）</li>
     *   <li>如果为多实例模式，从 Flowable 运行时变量中获取：
     *       <ul>
     *         <li>nrOfInstances — 总实例数</li>
     *         <li>nrOfCompletedInstances — 已完成实例数</li>
     *         <li>nrOfActiveInstances — 活跃实例数</li>
     *       </ul>
     *   </li>
     *   <li>获取所有审批人列表（从流程变量 assigneeList_{nodeKey} 中读取）</li>
     * </ol>
     *
     * <p>注意：多实例变量获取失败不会阻塞任务列表查询，保证系统鲁棒性。</p>
     */
    private void enrichMultiInstanceInfo(TaskDTO dto, Task task, Long templateId) {
        // 1. 从模板 nodeConfig 获取审批模式
        String approvalMode = resolveApprovalMode(templateId, task.getTaskDefinitionKey());
        dto.setApprovalMode(approvalMode);

        // 2. 如果是多实例任务（会签 ALL 或 或签 ANY），尝试获取进度信息
        if ("ALL".equals(approvalMode) || "ANY".equals(approvalMode)) {
            try {
                // 多实例的 nrOf* 变量通过执行 ID 获取
                String executionId = task.getExecutionId();
                if (executionId != null) {
                    // 从当前执行读取 Flowable 多实例内置变量
                    Object nrOfInstances = runtimeService.getVariable(executionId, "nrOfInstances");
                    Object nrOfCompleted = runtimeService.getVariable(executionId, "nrOfCompletedInstances");
                    Object nrOfActive = runtimeService.getVariable(executionId, "nrOfActiveInstances");

                    if (nrOfInstances instanceof Number) {
                        dto.setNrOfInstances(((Number) nrOfInstances).intValue());
                    }
                    if (nrOfCompleted instanceof Number) {
                        dto.setNrOfCompletedInstances(((Number) nrOfCompleted).intValue());
                    }
                    if (nrOfActive instanceof Number) {
                        dto.setNrOfActiveInstances(((Number) nrOfActive).intValue());
                    }
                }

                // 3. 获取所有审批人列表 — 从流程变量中读取审批人集合
                String collectionVar = "assigneeList_" + task.getTaskDefinitionKey();
                Object collectionObj = runtimeService.getVariable(
                        task.getProcessInstanceId(), collectionVar);
                if (collectionObj instanceof List<?> list) {
                    dto.setAllAssignees(list.stream()
                            .map(String::valueOf)
                            .collect(Collectors.joining(",")));
                }
            } catch (Exception ignored) {
                // 多实例变量获取失败不阻塞任务列表查询
            }
        }
    }

    /**
     * 从模板 nodeConfig 解析指定节点的 approvalMode。
     */
    private String resolveApprovalMode(Long templateId, String nodeKey) {
        ProcessTemplate template = processTemplateRepository
                .findByIdAndDeleted(templateId, 0)
                .orElse(null);
        if (template == null || !hasText(template.getNodeConfig())) {
            return "SINGLE";
        }
        try {
            Map<String, Map<String, Object>> map = objectMapper.readValue(
                    template.getNodeConfig(),
                    new TypeReference<Map<String, Map<String, Object>>>() {});
            Map<String, Object> config = map.get(nodeKey);
            if (config != null) {
                Object mode = config.get("approvalMode");
                return mode != null ? mode.toString() : "SINGLE";
            }
            // 遍历查找匹配的 nodeKey / nodeId
            for (Map<String, Object> cfg : map.values()) {
                Object nk = cfg.get("nodeKey");
                Object nid = cfg.get("nodeId");
                if (nodeKey.equals(stringValue(nk)) || nodeKey.equals(stringValue(nid))) {
                    Object mode = cfg.get("approvalMode");
                    return mode != null ? mode.toString() : "SINGLE";
                }
            }
        } catch (Exception ignored) {
            // ignore
        }
        return "SINGLE";
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString().trim();
    }

    /**
     * 从模板 nodeConfig 解析指定节点的 businessType（业务类型）。
     *
     * <p>兼容两种 JSON 存储格式：</p>
     * <ul>
     *   <li><b>Map 格式</b>（前端主力格式）：{@code { "NodeId": { "businessType": "approval", ... } }}</li>
     *   <li><b>Array 格式</b>（历史遗留格式）：{@code [ { "nodeKey": "NodeId", "businessType": "approval", ... } ]}</li>
     * </ul>
     *
     * <p>匹配规则：先用 nodeKey 精确匹配 Map key，再遍历 value 中的 nodeKey/nodeId 字段。</p>
     */
    private String resolveBusinessType(Long templateId, String nodeKey) {
        ProcessTemplate template = processTemplateRepository
                .findByIdAndDeleted(templateId, 0)
                .orElse(null);
        if (template == null || !hasText(template.getNodeConfig())) {
            return null;
        }
        try {
            // 先尝试 Map 格式：{ "NodeId": {...}, ... }
            Map<String, Map<String, Object>> map = objectMapper.readValue(
                    template.getNodeConfig(),
                    new TypeReference<Map<String, Map<String, Object>>>() {});
            Map<String, Object> config = map.get(nodeKey);
            if (config != null) {
                return stringValue(config.get("businessType"));
            }
            for (Map.Entry<String, Map<String, Object>> entry : map.entrySet()) {
                Object nk = entry.getValue().get("nodeKey");
                Object nid = entry.getValue().get("nodeId");
                if (nodeKey.equals(stringValue(nk)) || nodeKey.equals(stringValue(nid))) {
                    return stringValue(entry.getValue().get("businessType"));
                }
            }
        } catch (Exception e) {
            // Map 格式失败，尝试 Array 格式：[{ "nodeKey": "...", ... }, ...]
            try {
                List<Map<String, Object>> list = objectMapper.readValue(
                        template.getNodeConfig(),
                        new TypeReference<List<Map<String, Object>>>() {});
                for (Map<String, Object> item : list) {
                    Object nk = item.get("nodeKey");
                    Object nid = item.get("nodeId");
                    if (nodeKey.equals(stringValue(nk)) || nodeKey.equals(stringValue(nid))) {
                        return stringValue(item.get("businessType"));
                    }
                }
            } catch (Exception ignored) { /* ignore */ }
        }
        return null;
    }

    // ========================================================================
    // 辅助方法
    // ========================================================================

    /**
     * 解析任务绑定的表单 ID。
     *
     * <p>优先级：</p>
     * <ol>
     *   <li>节点级别绑定 — 从 formBindConfig 中查找 taskDefinitionKey 对应的 formId</li>
     *   <li>模板顶层回退 — 使用模板的顶层 formId</li>
     * </ol>
     */
    private Long resolveFormId(Long templateId, String taskDefinitionKey) {
        ProcessTemplate template = processTemplateRepository
                .findByIdAndDeleted(templateId, 0)
                .orElse(null);
        if (template == null) {
            return null;
        }
        // 1. 先尝试节点级别表单绑定（formBindConfig）
        if (hasText(template.getFormBindConfig())) {
            try {
                Map<String, Map<String, Object>> bindConfig = objectMapper.readValue(
                        template.getFormBindConfig(),
                        new TypeReference<Map<String, Map<String, Object>>>() {}
                );
                Map<String, Object> nodeBinding = bindConfig.get(taskDefinitionKey);
                if (nodeBinding != null && nodeBinding.get("formId") != null) {
                    Object formIdObj = nodeBinding.get("formId");
                    if (formIdObj instanceof Number) {
                        return ((Number) formIdObj).longValue();
                    }
                }
            } catch (Exception ignored) {
                // formBindConfig 解析失败不阻塞任务列表
            }
        }
        // 2. 回退到模板顶层 formId
        return template.getFormId();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
