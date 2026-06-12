package com.aiflow.service.impl;

import com.aiflow.enums.MarketType;
import com.aiflow.enums.TemplateStatus;
import com.aiflow.model.ProcessTemplate;
import com.aiflow.model.TemplateMarket;
import com.aiflow.repository.ProcessTemplateRepository;
import com.aiflow.repository.TemplateMarketRepository;
import com.aiflow.service.ProcessTemplateService;
import com.aiflow.service.TemplateMarketService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class TemplateMarketServiceImpl implements TemplateMarketService {

    private final TemplateMarketRepository templateMarketRepository;
    private final ProcessTemplateRepository processTemplateRepository;
    private final ProcessTemplateService processTemplateService;

    @Override
    public TemplateMarket publishTemplateToMarket(Long templateId,
                                                  Long publisherId,
                                                  String title,
                                                  String description,
                                                  String coverUrl,
                                                  String tags) {
        requireId(templateId, "templateId must not be null");
        requireText(title, "title must not be blank");

        ProcessTemplate template = processTemplateRepository.findById(templateId)
                .orElseThrow(() -> new IllegalArgumentException("template not found"));
        if (template.getStatus() != TemplateStatus.PUBLISHED) {
            throw new IllegalStateException("only published template can be listed in market");
        }

        LocalDateTime now = LocalDateTime.now();
        TemplateMarket market = TemplateMarket.builder()
                .sourceId(templateId)
                .type(MarketType.TEMPLATE)
                .title(title)
                .description(description)
                .coverUrl(coverUrl)
                .bizTypeId(template.getBizTypeId())
                .publisherId(publisherId)
                .useCount(0L)
                .rating(BigDecimal.ZERO)
                .tags(tags)
                .publishedAt(now)
                .createdAt(now)
                .updatedAt(now)
                .deleted(0)
                .build();
        return templateMarketRepository.save(market);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TemplateMarket> listMarketItems() {
        return templateMarketRepository.findByDeletedOrderByPublishedAtDesc(0);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TemplateMarket> findById(Long id) {
        requireId(id, "id must not be null");
        return templateMarketRepository.findById(id);
    }

    @Override
    public void withdrawFromMarket(Long marketId) {
        requireId(marketId, "marketId must not be null");
        TemplateMarket market = templateMarketRepository.findById(marketId)
                .orElseThrow(() -> new IllegalArgumentException("market item not found"));
        templateMarketRepository.delete(market);
    }

    @Override
    public ProcessTemplate copyTemplateFromMarket(Long marketId, Long userId, String newTemplateName) {
        requireId(marketId, "marketId must not be null");
        requireId(userId, "userId must not be null");

        TemplateMarket market = templateMarketRepository.findById(marketId)
                .orElseThrow(() -> new IllegalArgumentException("market item not found"));
        if (market.getType() != MarketType.TEMPLATE) {
            throw new IllegalStateException("only template market item can be copied");
        }

        ProcessTemplate sourceTemplate = processTemplateRepository.findById(market.getSourceId())
                .orElseThrow(() -> new IllegalArgumentException("source template not found"));
        ProcessTemplate copiedTemplate = processTemplateService.copyTemplate(sourceTemplate, userId, newTemplateName);

        long currentUseCount = market.getUseCount() == null ? 0L : market.getUseCount();
        market.setUseCount(currentUseCount + 1);
        market.setUpdatedAt(LocalDateTime.now());
        templateMarketRepository.save(market);
        return copiedTemplate;
    }

    private void requireId(Long id, String message) {
        if (id == null) {
            throw new IllegalArgumentException(message);
        }
    }

    private void requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
    }
}
