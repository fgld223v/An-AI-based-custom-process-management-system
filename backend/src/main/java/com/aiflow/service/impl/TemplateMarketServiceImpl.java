package com.aiflow.service.impl;

import com.aiflow.enums.MarketType;
import com.aiflow.enums.ProcessResourceType;
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

/**
 * 模板市场服务实现 — 管理流程模板的上架、下架和复制。
 *
 * <p>核心功能：</p>
 * <ul>
 *   <li><b>上架（publish）</b> — 将已发布且标记为系统模板的流程模板发布到模板市场，
 *       包含标题、描述、封面、标签等信息。仅允许 status=PUBLISHED 且 resourceType=SYSTEM_TEMPLATE 的模板上架。</li>
 *   <li><b>浏览（list）</b> — 查询模板市场中所有已上架的模板，按发布时间降序排列。</li>
 *   <li><b>下架（withdraw）</b> — 从模板市场中移除指定模板。</li>
 *   <li><b>复制（copy）</b> — 从模板市场复制模板到当前用户的工作区，复制后 useCount 自增。</li>
 * </ul>
 *
 * <p>所有写操作均在一个事务中完成，确保数据一致性。</p>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class TemplateMarketServiceImpl implements TemplateMarketService {

    private final TemplateMarketRepository templateMarketRepository;
    private final ProcessTemplateRepository processTemplateRepository;
    private final ProcessTemplateService processTemplateService;  // 用于复制模板

    /**
     * 将模板发布到模板市场。
     *
     * <p>前置校验：</p>
     * <ul>
     *   <li>模板必须存在</li>
     *   <li>模板状态必须为 PUBLISHED（已发布）</li>
     *   <li>模板资源类型必须为 SYSTEM_TEMPLATE（系统模板）</li>
     * </ul>
     *
     * @param templateId  模板 ID
     * @param publisherId 发布者用户 ID
     * @param title       市场展示标题
     * @param description 市场描述
     * @param coverUrl    封面图 URL
     * @param tags        标签
     * @return 创建的市场条目
     */
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
        if (template.getResourceType() != ProcessResourceType.SYSTEM_TEMPLATE) {
            throw new IllegalStateException("only system template can be listed in market");
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

    /**
     * 从模板市场复制模板到用户工作区。
     *
     * <p>复制后市场条目的 useCount 自增 1，用于记录模板的受欢迎程度。</p>
     *
     * @param marketId        市场条目 ID
     * @param userId          复制者用户 ID
     * @param newTemplateName 新模板名称
     * @return 复制后的新模板
     */
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
