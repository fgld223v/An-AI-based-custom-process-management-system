package com.aiflow.controller;

import com.aiflow.common.ApiResponse;
import com.aiflow.dto.FormSubmissionDTO;
import com.aiflow.dto.NotificationDTO;
import com.aiflow.dto.ProcessInstanceDTO;
import com.aiflow.dto.ProcessDiagramDTO;
import com.aiflow.dto.RuntimeStateDTO;
import com.aiflow.dto.SaveNodeFormRequest;
import com.aiflow.dto.TimelineDTO;
import com.aiflow.dto.StartProcessPreviewRequest;
import com.aiflow.service.ProcessInstanceService;
import com.aiflow.service.TaskUrgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 流程实例控制器 -- 管理流程实例的创建、提交、查询、催办及运行时状态。
 *
 * <p>端点一览：
 * <ul>
 *   <li>GET    /api/process-instances                     -- 流程实例列表（支持模板/状态/关键字筛选）</li>
 *   <li>GET    /api/process-instances/{id}                -- 流程实例详情</li>
 *   <li>GET    /api/process-instances/{id}/diagram        -- 获取流程图数据（含当前节点高亮）</li>
 *   <li>GET    /api/process-instances/{id}/submissions    -- 该实例下所有节点的表单提交记录</li>
 *   <li>POST   /api/process-instances/draft               -- 创建草稿实例（不启动 Flowable 流程）</li>
 *   <li>POST   /api/process-instances/node-form           -- 保存草稿节点的表单数据</li>
 *   <li>PUT    /api/process-instances/{id}/submit         -- 提交草稿，正式启动 Flowable 流程</li>
 *   <li>GET    /api/process-instances/{id}/runtime-state   -- 查询流程运行时状态（各节点完成情况）</li>
 *   <li>GET    /api/process-instances/{id}/timeline       -- 获取流程时间线（审批流转记录）</li>
 *   <li>POST   /api/process-instances/{id}/urge           -- 催办当前任务</li>
 * </ul>
 *
 * <p>所有端点均需要登录。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/process-instances")
public class ProcessInstanceController {

    private final ProcessInstanceService processInstanceService;
    private final TaskUrgeService taskUrgeService;

    /**
     * 流程实例列表（支持多条件筛选）。
     *
     * <p>GET /api/process-instances -- 查询流程实例列表，可按模板、状态、关键字过滤。
     * 需要登录。
     *
     * @param templateId 模板 ID（可选）
     * @param status     实例状态过滤，如 running / completed（可选）
     * @param keyword    关键字搜索（可选，匹配实例名称或发起人）
     * @return 流程实例列表
     */
    @GetMapping
    public ApiResponse<List<ProcessInstanceDTO>> listInstances(@RequestParam(required = false) Long templateId,
                                                               @RequestParam(required = false) String status,
                                                               @RequestParam(required = false) String keyword) {
        return ApiResponse.success(processInstanceService.listInstances(templateId, status, keyword));
    }

    /**
     * 流程实例详情。
     *
     * <p>GET /api/process-instances/{id} -- 查询指定流程实例的完整信息，
     * 包括基本属性、当前审批节点和表单数据。需要登录。
     *
     * @param id 流程实例 ID
     * @return 流程实例详细信息
     */
    @GetMapping("/{id}")
    public ApiResponse<ProcessInstanceDTO> getInstance(@PathVariable Long id) {
        return ApiResponse.success(processInstanceService.getInstance(id));
    }

    /**
     * 获取流程图数据。
     *
     * <p>GET /api/process-instances/{id}/diagram -- 返回流程图结构数据，
     * 标注当前节点和已完成节点，用于前端高亮渲染。需要登录。
     *
     * @param id 流程实例 ID
     * @return 流程图节点、连线及当前状态信息
     */
    @GetMapping("/{id}/diagram")
    public ApiResponse<ProcessDiagramDTO> getDiagram(@PathVariable Long id) {
        return ApiResponse.success(processInstanceService.getDiagram(id));
    }

    /**
     * 查询实例的表单提交记录。
     *
     * <p>GET /api/process-instances/{id}/submissions -- 返回该实例下所有审批节点的表单提交历史。
     * 需要登录。
     *
     * @param id 流程实例 ID
     * @return 各节点的表单提交记录列表
     */
    @GetMapping("/{id}/submissions")
    public ApiResponse<List<FormSubmissionDTO>> listSubmissions(@PathVariable Long id) {
        return ApiResponse.success(processInstanceService.listSubmissions(id));
    }

    /**
     * 创建流程草稿。
     *
     * <p>POST /api/process-instances/draft -- 创建流程实例的草稿版本，暂不启动 Flowable 流程。
     * 用户可先填写各节点表单，确认后再正式提交。需要登录。
     *
     * @param request 草稿创建请求，包含模板 ID 和初始表单数据
     * @return 创建的草稿实例信息
     */
    @PostMapping("/draft")
    public ApiResponse<ProcessInstanceDTO> createDraft(@RequestBody StartProcessPreviewRequest request) {
        return ApiResponse.success(processInstanceService.createDraft(request));
    }

    /**
     * 保存草稿节点的表单数据。
     *
     * <p>POST /api/process-instances/node-form -- 保存/更新草稿实例中某节点的表单数据，
     * 用于草稿暂存场景。需要登录。
     *
     * @param request 包含实例 ID、节点 key 和表单字段值
     * @return 保存后的表单提交记录
     */
    @PostMapping("/node-form")
    public ApiResponse<FormSubmissionDTO> saveNodeForm(@RequestBody SaveNodeFormRequest request) {
        return ApiResponse.success(processInstanceService.saveNodeForm(request));
    }

    /**
     * 提交流程实例（启动 Flowable 流程）。
     *
     * <p>PUT /api/process-instances/{id}/submit -- 将草稿实例正式提交，
     * 启动 Flowable 流程引擎执行。提交后实例状态从 draft 变为 running。
     * 需要登录。
     *
     * @param id 流程实例 ID
     * @return 提交后的实例信息
     */
    @PutMapping("/{id}/submit")
    public ApiResponse<ProcessInstanceDTO> submitInstance(@PathVariable Long id) {
        return ApiResponse.success(processInstanceService.submitInstance(id));
    }

    /**
     * 查询流程运行时状态。
     *
     * <p>GET /api/process-instances/{id}/runtime-state -- 查询流程各节点的执行状态、
     * 当前处理人、停留时间等运行时信息。需要登录。
     *
     * @param id 流程实例 ID
     * @return 运行时状态 DTO，包含各节点状态列表
     */
    @GetMapping("/{id}/runtime-state")
    public ApiResponse<RuntimeStateDTO> getRuntimeState(@PathVariable Long id) {
        return ApiResponse.success(processInstanceService.getRuntimeState(id));
    }

    /**
     * 获取流程时间线。
     *
     * <p>GET /api/process-instances/{id}/timeline -- 返回流程审批的时间线记录，
     * 包含每个审批节点的操作人、操作时间、审批意见等。需要登录。
     *
     * @param id 流程实例 ID
     * @return 时间线 DTO，包含按时间排序的审批事件列表
     */
    @GetMapping("/{id}/timeline")
    public ApiResponse<TimelineDTO> getTimeline(@PathVariable Long id) {
        return ApiResponse.success(processInstanceService.getTimeline(id));
    }

    /**
     * 催办当前任务。
     *
     * <p>POST /api/process-instances/{id}/urge -- 向当前任务的待办人发送催办通知。
     * 需要登录。
     *
     * @param id 流程实例 ID
     * @return 生成的催办通知信息
     */
    @PostMapping("/{id}/urge")
    public ApiResponse<NotificationDTO> urgeCurrentTask(@PathVariable Long id) {
        return ApiResponse.success(taskUrgeService.urgeCurrentTask(id));
    }
}
