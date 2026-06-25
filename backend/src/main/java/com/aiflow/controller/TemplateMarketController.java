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

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/template-market")
public class TemplateMarketController {

    private final TemplateMarketService templateMarketService;

    @GetMapping
    public ApiResponse<List<TemplateMarketDTO>> listMarketItems() {
        List<TemplateMarketDTO> result = templateMarketService.listMarketItems().stream()
                .map(DtoMapper::toTemplateMarketDTO)
                .toList();
        return ApiResponse.success(result);
    }

    @GetMapping("/{id}")
    public ApiResponse<TemplateMarketDTO> getMarketItem(@PathVariable Long id) {
        TemplateMarket market = templateMarketService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("market item not found"));
        return ApiResponse.success(DtoMapper.toTemplateMarketDTO(market));
    }

    @PostMapping("/publish-template")
    public ApiResponse<TemplateMarketDTO> publishTemplateToMarket(@RequestBody MarketPublishRequest request) {
        Long currentUserId = SecurityUtils.currentUserId();
        if (currentUserId == null) {
            throw new IllegalStateException("current user is required");
        }
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

    @PostMapping("/{marketId}/copy")
    public ApiResponse<ProcessTemplateDTO> copyTemplateFromMarket(@PathVariable Long marketId,
                                                                  @RequestBody MarketCopyRequest request) {
        Long currentUserId = SecurityUtils.currentUserId();
        if (currentUserId == null) {
            throw new IllegalStateException("current user is required");
        }
        if (!"biz_admin".equals(SecurityUtils.currentUserSystemRole())) {
            throw new AccessDeniedException("biz admin role is required");
        }
        ProcessTemplate copied = templateMarketService.copyTemplateFromMarket(
                marketId,
                currentUserId,
                request.getNewTemplateName()
        );
        return ApiResponse.success(DtoMapper.toProcessTemplateDTO(copied));
    }

    /** 从市场下架 */
    @PostMapping("/{marketId}/withdraw")
    public ApiResponse<Void> withdrawFromMarket(@PathVariable Long marketId) {
        templateMarketService.withdrawFromMarket(marketId);
        return ApiResponse.success();
    }
}
