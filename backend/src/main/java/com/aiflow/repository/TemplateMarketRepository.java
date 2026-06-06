package com.aiflow.repository;

import com.aiflow.model.TemplateMarket;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TemplateMarketRepository extends JpaRepository<TemplateMarket, Long> {

    List<TemplateMarket> findByDeletedOrderByPublishedAtDesc(Integer deleted);
}
