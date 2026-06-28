package com.aiflow.service;

import com.aiflow.dto.BusinessProcessInstanceDTO;
import com.aiflow.dto.FormSubmissionDTO;
import com.aiflow.dto.TimelineDTO;

import java.util.List;

/**
 * 业务监控服务接口，提供当前用户相关及全局流程实例的查询能力。
 * "Owned" 方法仅返回当前用户相关的数据，"Global" 方法返回所有数据（管理员视角）。
 */
public interface BusinessMonitoringService {

    /**
     * 查询当前用户相关的流程实例列表。
     */
    List<BusinessProcessInstanceDTO> listOwnedProcessInstances(Long templateId, String status, String keyword);

    /**
     * 查询当前用户相关的单个流程实例。
     */
    BusinessProcessInstanceDTO getOwnedProcessInstance(Long instanceId);

    /**
     * 查询当前用户相关流程的时间线。
     */
    TimelineDTO getOwnedTimeline(Long instanceId);

    /**
     * 查询当前用户相关流程的表单提交记录。
     */
    List<FormSubmissionDTO> listOwnedSubmissions(Long instanceId);

    /**
     * 查询全局所有流程实例列表（管理员视角）。
     */
    List<BusinessProcessInstanceDTO> listGlobalProcessInstances(Long templateId, String status, String keyword);

    /**
     * 查询全局单个流程实例。
     */
    BusinessProcessInstanceDTO getGlobalProcessInstance(Long instanceId);

    /**
     * 查询全局流程的时间线。
     */
    TimelineDTO getGlobalTimeline(Long instanceId);

    /**
     * 查询全局流程的表单提交记录。
     */
    List<FormSubmissionDTO> listGlobalSubmissions(Long instanceId);
}
