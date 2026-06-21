package com.aiflow.service;

import com.aiflow.model.ProcessTemplate;

import java.util.Collections;
import java.util.List;

/**
 * 发布流程模板的结果，包含已保存的模板和部署时产生的警告信息。
 */
public class PublishResult {

    private final ProcessTemplate template;
    private final List<String> warnings;

    public PublishResult(ProcessTemplate template, List<String> warnings) {
        this.template = template;
        this.warnings = warnings == null ? Collections.emptyList() : List.copyOf(warnings);
    }

    public ProcessTemplate template() {
        return template;
    }

    public List<String> warnings() {
        return warnings;
    }
}
