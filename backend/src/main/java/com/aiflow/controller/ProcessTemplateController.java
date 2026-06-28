package com.aiflow.controller;

import com.aiflow.common.ApiResponse;
import com.aiflow.dto.DtoMapper;
import com.aiflow.dto.ProcessTemplateCreateRequest;
import com.aiflow.dto.ProcessTemplateDTO;
import com.aiflow.dto.ProcessTemplateUpdateRequest;
import com.aiflow.dto.TemplateFormBindingDTO;
import com.aiflow.enums.ProcessResourceType;
import com.aiflow.model.ProcessTemplate;
import com.aiflow.security.SecurityUtils;
import com.aiflow.service.ProcessTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 流程模板控制器 -- 管理流程模板的 CRUD、发布/停用、版本管理及关联表单查询。
 *
 * <p>端点一览：
 * <ul>
 *   <li>GET    /api/process-templates             -- 获取所有流程模板列表</li>
 *   <li>GET    /api/process-templates/{id}        -- 获取单个模板详情（仅系统模板）</li>
 *   <li>GET    /api/process-templates/{id}/form   -- 查询模板绑定的表单</li>
 *   <li>POST   /api/process-templates             -- 创建新模板（自动设为系统模板）</li>
 *   <li>PUT    /api/process-templates/{id}        -- 更新模板</li>
 *   <li>POST   /api/process-templates/{id}/publish    -- 发布模板（生成新版本并部署到 Flowable）</li>
 *   <li>POST   /api/process-templates/{id}/new-version -- 创建模板的下一个草稿版本</li>
 *   <li>POST   /api/process-templates/{id}/unpublish   -- 停用已发布的模板版本</li>
 *   <li>DELETE /api/process-templates/{id}        -- 删除未发布/已停用的模板</li>
 * </ul>
 *
 * <p>所有端点均需要登录。写操作通常需要业务管理员权限。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/process-templates")
public class ProcessTemplateController {

    private final ProcessTemplateService processTemplateService;

    /**
     * 获取所有流程模板列表。
     *
     * <p>GET /api/process-templates -- 返回系统中所有未删除的流程模板。
     * 需要登录。
     *
     * @return 流程模板 DTO 列表
     */
    @GetMapping
    public ApiResponse<List<ProcessTemplateDTO>> listTemplates() {
        // 查询所有模板并转换为 DTO
        List<ProcessTemplateDTO> result = processTemplateService.listTemplates().stream()
                .map(DtoMapper::toProcessTemplateDTO)
                .toList();
        return ApiResponse.success(result);
    }

    /**
     * 获取单个模板详情。
     *
     * <p>GET /api/process-templates/{id} -- 返回指定模板的完整信息，
     * 仅限资源类型为系统模板。需要登录。
     *
     * @param id 模板 ID
     * @return 模板详细信息
     * @throws IllegalArgumentException 模板不存在或不是系统模板
     */
    @GetMapping("/{id}")
    public ApiResponse<ProcessTemplateDTO> getTemplate(@PathVariable Long id) {
        // 校验模板存在且为系统模板
        ProcessTemplate template = getSystemTemplate(id);
        return ApiResponse.success(DtoMapper.toProcessTemplateDTO(template));
    }

    /**
     * 查询模板绑定的表单。
     *
     * <p>GET /api/process-templates/{id}/form -- 返回指定模板版本绑定的表单定义。
     * 需要登录。
     *
     * @param id 模板 ID
     * @return 模板-表单绑定关系 DTO
     */
    @GetMapping("/{id}/form")
    public ApiResponse<TemplateFormBindingDTO> getTemplateBoundForm(@PathVariable Long id) {
        // 先校验模板存在
        getSystemTemplate(id);
        return ApiResponse.success(processTemplateService.getTemplateBoundForm(id));
    }

    /**
     * 创建新模板。
     *
     * <p>POST /api/process-templates -- 创建新的流程模板。
     * 资源类型自动设为 SYSTEM_TEMPLATE，创建人设为当前用户。需要登录。
     *
     * @param request 模板创建请求，包含名称、描述、节点配置等
     * @return 创建成功的模板信息
     */
    @PostMapping
    public ApiResponse<ProcessTemplateDTO> createTemplate(@RequestBody ProcessTemplateCreateRequest request) {
        // 将请求转为实体，并设置资源类型和创建人
        ProcessTemplate template = DtoMapper.toProcessTemplate(request);
        template.setResourceType(ProcessResourceType.SYSTEM_TEMPLATE);
        template.setCreatedBy(SecurityUtils.currentUserId());
        ProcessTemplate saved = processTemplateService.createTemplate(template);
        return ApiResponse.success(DtoMapper.toProcessTemplateDTO(saved));
    }

    /**
     * 更新模板。
     *
     * <p>PUT /api/process-templates/{id} -- 更新模板的节点配置、名称等信息。
     * 仅允许修改未发布或草稿状态的模板。需要登录。
     *
     * @param id      模板 ID
     * @param request 更新请求体
     * @return 更新后的模板信息
     */
    @PutMapping("/{id}")
    public ApiResponse<ProcessTemplateDTO> updateTemplate(@PathVariable Long id,
                                                          @RequestBody ProcessTemplateUpdateRequest request) {
        // 校验模板存在且为系统模板
        getSystemTemplate(id);
        ProcessTemplate saved = processTemplateService.updateTemplate(id, DtoMapper.toProcessTemplate(request));
        return ApiResponse.success(DtoMapper.toProcessTemplateDTO(saved));
    }

    /**
     * 发布模板。
     *
     * <p>POST /api/process-templates/{id}/publish -- 将模板正式发布，
     * 内部会调用 Flowable API 部署流程定义，生成 BPMN XML。
     * 需要登录，通常需要业务管理员权限。
     *
     * @param id 模板 ID
     * @return 发布后的模板信息（状态变为 published）
     */
    @PostMapping("/{id}/publish")
    public ApiResponse<ProcessTemplateDTO> publishTemplate(@PathVariable Long id) {
        getSystemTemplate(id);
        ProcessTemplate saved = processTemplateService.publishTemplate(id);
        return ApiResponse.success(DtoMapper.toProcessTemplateDTO(saved));
    }

    /**
     * 创建模板的新版本。
     *
     * <p>POST /api/process-templates/{id}/new-version -- 基于当前模板创建下一个版本
     * （版本号 +1，状态为草稿），用于模板迭代升级。需要登录。
     *
     * @param id 当前模板 ID
     * @return 新版本模板信息
     */
    @PostMapping("/{id}/new-version")
    public ApiResponse<ProcessTemplateDTO> createNewVersion(@PathVariable Long id) {
        getSystemTemplate(id);
        ProcessTemplate saved = processTemplateService.createNextVersion(id);
        return ApiResponse.success(DtoMapper.toProcessTemplateDTO(saved));
    }

    /**
     * 停用已发布的模板版本。
     *
     * <p>POST /api/process-templates/{id}/unpublish -- 将已发布版本标记为停用状态。
     * 历史部署信息保持不变，不会影响正在运行的实例。需要登录。
     *
     * @param id 模板 ID
     * @return 停用后的模板信息
     */
    @PostMapping("/{id}/unpublish")
    public ApiResponse<ProcessTemplateDTO> unpublishTemplate(@PathVariable Long id) {
        getSystemTemplate(id);
        ProcessTemplate saved = processTemplateService.unpublishTemplate(id);
        return ApiResponse.success(DtoMapper.toProcessTemplateDTO(saved));
    }

    /**
     * 删除模板。
     *
     * <p>DELETE /api/process-templates/{id} -- 删除未发布或已停用的模板版本。
     * 若模板已有运行中的实例或被市场引用，服务层会拒绝删除。需要登录。
     *
     * @param id 模板 ID
     * @return 空成功响应
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteTemplate(@PathVariable Long id) {
        // 校验模板存在且为系统模板
        getSystemTemplate(id);
        processTemplateService.deleteTemplate(id);
        return ApiResponse.success();
    }

    /**
     * 内部校验方法 -- 确保模板存在且资源类型为系统模板。
     *
     * @param id 模板 ID
     * @return 通过校验的模板实体
     * @throws IllegalArgumentException 模板不存在或不是系统模板
     */
    private ProcessTemplate getSystemTemplate(Long id) {
        ProcessTemplate template = processTemplateService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("template not found"));
        if (template.getResourceType() != ProcessResourceType.SYSTEM_TEMPLATE) {
            throw new IllegalArgumentException("system template not found");
        }
        return template;
    }
}
