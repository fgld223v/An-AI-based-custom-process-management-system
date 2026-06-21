package com.aiflow.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NodeConfigItem {

    private String nodeKey;
    private String nodeName;
    private String businessType;
    private String approvalMode;
    private String assignStrategy;
    private String assignValue;
    private String notifyTarget;
    private String notifyChannel;
}
