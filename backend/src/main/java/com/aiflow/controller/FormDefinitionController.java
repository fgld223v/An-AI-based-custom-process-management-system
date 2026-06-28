package com.aiflow.controller;

import com.aiflow.common.ApiResponse;
import com.aiflow.dto.DtoMapper;
import com.aiflow.dto.FormCreateRequest;
import com.aiflow.dto.FormDefinitionDTO;
import com.aiflow.dto.FormUpdateRequest;
import com.aiflow.model.FormDefinition;
import com.aiflow.service.FormDefinitionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 表单定义控制器 -- 管理流程表单的创建、编辑、发布/停用及查询。
 *
 * <p>端点一览：
 * <ul>
 *   <li>GET   /api/forms              -- 获取所有表单列表</li>
 *   <li>GET   /api/forms/published    -- 获取已发布的表单列表</li>
 *   <li>POST  /api/forms              -- 创建新表单定义</li>
 *   <li>PUT   /api/forms/{id}         -- 更新表单定义</li>
 *   <li>POST  /api/forms/{id}/publish -- 发布表单（表单发布后才能被模板引用）</li>
 *   <li>GET   /api/forms/{id}         -- 获取单个表单定义</li>
 *   <li>POST  /api/forms/{id}/disable -- 停用表单</li>
 * </ul>
 *
 * <p>所有端点均需要登录。写操作通常需要业务管理员权限。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/forms")
public class FormDefinitionController {

    private final FormDefinitionService formDefinitionService;

    /**
     * 获取所有表单定义列表。
     *
     * <p>GET /api/forms -- 返回系统中所有未删除的表单定义。
     * 需要登录。
     *
     * @return 表单定义 DTO 列表
     */
    @GetMapping
    public ApiResponse<List<FormDefinitionDTO>> listForms() {
        List<FormDefinitionDTO> result = formDefinitionService.listForms().stream()
                .map(DtoMapper::toFormDefinitionDTO)
                .toList();
        return ApiResponse.success(result);
    }

    /**
     * 获取已发布的表单列表。
     *
     * <p>GET /api/forms/published -- 返回所有状态为"已发布"的表单定义，
     * 用于模板绑定时选择表单。需要登录。
     *
     * @return 已发布的表单 DTO 列表
     */
    @GetMapping("/published")
    public ApiResponse<List<FormDefinitionDTO>> listPublishedForms() {
        List<FormDefinitionDTO> result = formDefinitionService.listPublishedForms().stream()
                .map(DtoMapper::toFormDefinitionDTO)
                .toList();
        return ApiResponse.success(result);
    }

    /**
     * 创建新的表单定义。
     *
     * <p>POST /api/forms -- 创建新表单，包含字段配置、校验规则等。
     * 需要登录。
     *
     * @param request 表单创建请求，含表单名称、字段列表、JSON 配置等
     * @return 创建成功的表单信息
     */
    @PostMapping
    public ApiResponse<FormDefinitionDTO> createForm(@Valid @RequestBody FormCreateRequest request) {
        // 将请求 DTO 转为实体后交由服务层创建
        FormDefinition saved = formDefinitionService.createForm(DtoMapper.toFormDefinition(request));
        return ApiResponse.success(DtoMapper.toFormDefinitionDTO(saved));
    }

    /**
     * 更新表单定义。
     *
     * <p>PUT /api/forms/{id} -- 修改表单的字段配置、名称等信息。
     * 仅允许修改未发布的表单。需要登录。
     *
     * @param id      表单 ID
     * @param request 更新请求体
     * @return 更新后的表单信息
     */
    @PutMapping("/{id}")
    public ApiResponse<FormDefinitionDTO> updateForm(@PathVariable Long id, @Valid @RequestBody FormUpdateRequest request) {
        FormDefinition saved = formDefinitionService.updateForm(id, DtoMapper.toFormDefinition(request));
        return ApiResponse.success(DtoMapper.toFormDefinitionDTO(saved));
    }

    /**
     * 发布表单。
     *
     * <p>POST /api/forms/{id}/publish -- 将表单定义标记为已发布，
     * 发布后表单可被流程模板绑定使用。需要登录。
     *
     * @param id 表单 ID
     * @return 发布后的表单信息
     */
    @PostMapping("/{id}/publish")
    public ApiResponse<FormDefinitionDTO> publishForm(@PathVariable Long id) {
        FormDefinition saved = formDefinitionService.publishForm(id);
        return ApiResponse.success(DtoMapper.toFormDefinitionDTO(saved));
    }

    /**
     * 获取单个表单定义。
     *
     * <p>GET /api/forms/{id} -- 查询指定 ID 的表单详细信息，
     * 仅返回未删除的有效表单。需要登录。
     *
     * @param id 表单 ID
     * @return 表单详细信息
     * @throws IllegalArgumentException 表单不存在或已删除
     */
    @GetMapping("/{id}")
    public ApiResponse<FormDefinitionDTO> getForm(@PathVariable Long id) {
        FormDefinition form = formDefinitionService.findActiveById(id)
                .orElseThrow(() -> new IllegalArgumentException("form not found"));
        return ApiResponse.success(DtoMapper.toFormDefinitionDTO(form));
    }

    /**
     * 停用表单。
     *
     * <p>POST /api/forms/{id}/disable -- 将表单标记为禁用状态，
     * 已绑定了此表单的模板不受影响，但新绑定将不可选。需要登录。
     *
     * @param id 表单 ID
     * @return 空成功响应
     */
    @PostMapping("/{id}/disable")
    public ApiResponse<Void> disableForm(@PathVariable Long id) {
        formDefinitionService.disableForm(id);
        return ApiResponse.success();
    }
}
