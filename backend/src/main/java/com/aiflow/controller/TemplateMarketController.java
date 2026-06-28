package com.aiflow.controller;

import com.aiflow.common.ApiResponse;
import com.aiflow.dto.DtoMapper;
import com.aiflow.dto.MarketCopyRequest;
import com.aiflow.dto.MarketPublishRequest;
import com.aiflow.dto.ProcessTemplateDTO;
import com.aiflow.dto.TemplateMarketDTO;
import com.aiflow.model.ProcessTemplate;
import com.aiflow.model.TemplateMarket;
import com.aiflow.security.SecurityUtils;
import com.aiflow.service.TemplateMarketService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 模板市场控制器 -- 管理流程模板在市场的发布、浏览、复制和下架操作。
 *
 * <p>模板市场允许用户发布已审核的流程模板供其他业务管理员浏览和复制使用，
 * 促进优秀流程的复用和共享。
 *
 * <p>端点一览：
 * <ul>
 *   <li>GET  /api/template-market                   -- 浏览市场中的模板列表</li>
 *   <li>GET  /api/template-market/{id}              -- 查看市场中的单个模板详情</li>
 *   <li>POST /api/template-market/publish-template   -- 将模板发布到市场</li>
 *   <li>POST /api/template-market/{marketId}/copy    -- 从市场复制模板到自己的模板库（需业务管理员权限）</li>
 *   <li>POST /api/template-market/{marketId}/withdraw -- 将模板从市场下架</li>
 * </ul>
 *
 * <p>除浏览接口外，所有操作均需要登录。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/template-market")
public class TemplateMarketController {

    private final TemplateMarketService templateMarketService;

    /**
     * 浏览市场模板列表。
     *
     * <p>GET /api/template-market -- 返回市场中所有在架的流程模板。
     * 公开访问，无需登录。
     *
     * @return 模板市场 DTO 列表，含标题、描述、标签、封面等展示信息
     */
    @GetMapping
    public ApiResponse<List<TemplateMarketDTO>> listMarketItems() {
        List<TemplateMarketDTO> result = templateMarketService.listMarketItems().stream()
                .map(DtoMapper::toTemplateMarketDTO)
                .toList();
        return ApiResponse.success(result);
    }

    /**
     * 查看市场中的模板详情。
     *
     * <p>GET /api/template-market/{id} -- 返回市场中指定模板的完整信息。
     * 公开访问，无需登录。
     *
     * @param id 市场项 ID
     * @return 模板市场 DTO，含完整的模板节点配置等
     * @throws IllegalArgumentException 市场项不存在
     */
    @GetMapping("/{id}")
    public ApiResponse<TemplateMarketDTO> getMarketItem(@PathVariable Long id) {
        TemplateMarket market = templateMarketService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("market item not found"));
        return ApiResponse.success(DtoMapper.toTemplateMarketDTO(market));
    }

    /**
     * 将模板发布到市场。
     *
     * <p>POST /api/template-market/publish-template -- 将一个已发布的系统模板上架到模板市场，
     * 供其他管理员浏览和复制。需要登录，发布者必须是模板所有者。
     *
     * @param request 发布请求，含模板 ID、市场展示标题/描述/封面/标签
     * @return 发布后的市场项信息
     * @throws IllegalStateException 当前用户未登录
     */
    @PostMapping("/publish-template")
    public ApiResponse<TemplateMarketDTO> publishTemplateToMarket(@RequestBody MarketPublishRequest request) {
        Long currentUserId = SecurityUtils.currentUserId();
        if (currentUserId == null) {
            throw new IllegalStateException("current user is required");
        }
        // 将模板发布到市场
        TemplateMarket market = templateMarketService.publishTemplateToMarket(
                request.getTemplateId(),
                currentUserId,
                request.getTitle(),
                request.getDescription(),
                request.getCoverUrl(),
                request.getTags()
        );
        return ApiResponse.success(DtoMapper.toTemplateMarketDTO(market));
    }

    /**
     * 从市场复制模板。
     *
     * <p>POST /api/template-market/{marketId}/copy -- 将市场中的模板复制到当前用户的
     * 模板库中（创建一份副本），副本为新创建的草稿版本。
     * 需要登录且具备业务管理员（biz_admin）角色。
     *
     * @param marketId 市场项 ID
     * @param request  复制请求，含新模板名称
     * @return 复制后的新模板信息
     * @throws IllegalStateException   当前用户未登录
     * @throws AccessDeniedException   当前用户不是业务管理员
     */
    @PostMapping("/{marketId}/copy")
    public ApiResponse<ProcessTemplateDTO> copyTemplateFromMarket(@PathVariable Long marketId,
                                                                  @RequestBody MarketCopyRequest request) {
        Long currentUserId = SecurityUtils.currentUserId();
        if (currentUserId == null) {
            throw new IllegalStateException("current user is required");
        }
        // 仅业务管理员可复制模板
        if (!"biz_admin".equals(SecurityUtils.currentUserSystemRole())) {
            throw new AccessDeniedException("biz admin role is required");
        }
        // 从市场复制模板到用户模板库
        ProcessTemplate copied = templateMarketService.copyTemplateFromMarket(
                marketId,
                currentUserId,
                request.getNewTemplateName()
        );
        return ApiResponse.success(DtoMapper.toProcessTemplateDTO(copied));
    }

    /**
     * 从市场下架模板。
     *
     * <p>POST /api/template-market/{marketId}/withdraw -- 将模板从市场中下架，
     * 已复制到用户模板库的副本不受影响。需要登录，操作者必须是发布者或超管。
     *
     * @param marketId 市场项 ID
     * @return 空成功响应
     */
    @PostMapping("/{marketId}/withdraw")
    public ApiResponse<Void> withdrawFromMarket(@PathVariable Long marketId) {
        templateMarketService.withdrawFromMarket(marketId);
        return ApiResponse.success();
    }
}
