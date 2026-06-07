package com.aiflow.controller;

import com.aiflow.common.ApiResponse;
import com.aiflow.service.FlowableProcessService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Flowable流程引擎管理接口
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/flowable")
public class FlowableController {

    private final FlowableProcessService flowableProcessService;

    /**
     * 获取已部署的流程定义列表
     */
    @GetMapping("/process-definitions")
    public ApiResponse<List<String>> listProcessDefinitions() {
        List<String> definitions = flowableProcessService.listDeployedProcessDefinitions();
        return ApiResponse.success(definitions);
    }
}
