package com.aiflow.service;

import com.aiflow.model.ProcessTemplate;
import com.aiflow.model.TemplateMarket;

import java.util.List;
import java.util.Optional;

/**
 * 模板市场服务接口，提供模板发布到市场、下架、复制等操作。
 */
public interface TemplateMarketService {

    TemplateMarket publishTemplateToMarket(Long templateId,
                                           Long publisherId,
                                           String title,
                                           String description,
                                           String coverUrl,
                                           String tags);

    List<TemplateMarket> listMarketItems();

    Optional<TemplateMarket> findById(Long id);

    /**
     * 从市场下架指定项。
     */
    void withdrawFromMarket(Long marketId);

    ProcessTemplate copyTemplateFromMarket(Long marketId, Long userId, String newTemplateName);
}
