package com.aiflow.service.impl;

import java.util.Collections;
import java.util.List;

/**
 * BPMN XML 增强结果，包含增强后的 XML 和转换警告信息。
 */
public class BpmnEnhancementResult {

    private final String bpmnXml;
    private final List<String> warnings;

    public BpmnEnhancementResult(String bpmnXml, List<String> warnings) {
        this.bpmnXml = bpmnXml;
        this.warnings = warnings == null ? Collections.emptyList() : List.copyOf(warnings);
    }

    public String bpmnXml() {
        return bpmnXml;
    }

    public List<String> warnings() {
        return warnings;
    }
}
