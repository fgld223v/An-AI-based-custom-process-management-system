package com.aiflow.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NodeConfigItem {

    private String nodeKey;
    private String nodeName;
    private String businessType;

    /** 审批模式：SINGLE（单人）/ ALL（会签）/ ANY（或签），仅 approval 节点有效 */
    private String approvalMode;

    /** 审批人分配策略：DIRECT_SUPERVISOR / DEPARTMENT_MANAGER / ROLE / SPECIFIC_USERS，仅 approval 节点有效 */
    private String assignStrategy;

    /** 抄送目标：APPLICANT / APPROVER / USER，仅 notify 节点有效 */
    private String notifyTarget;

    /** 通知渠道：in_app / email / both，仅 notify 节点有效 */
    private String notifyChannel;
}
