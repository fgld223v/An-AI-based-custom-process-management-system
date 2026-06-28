package com.aiflow.repository;

import com.aiflow.model.TemplateMarket;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 模板市场Repository：按类型和来源ID查询市场条目。
 */
public interface TemplateMarketRepository extends JpaRepository<TemplateMarket, Long> {

    List<TemplateMarket> findByDeletedOrderByPublishedAtDesc(Integer deleted);

    Optional<TemplateMarket> findByTypeAndSourceIdAndDeleted(
            com.aiflow.enums.MarketType type, Long sourceId, Integer deleted);
}
