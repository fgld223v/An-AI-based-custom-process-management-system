package com.aiflow.repository;

import com.aiflow.model.TemplateMarket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TemplateMarketRepository extends JpaRepository<TemplateMarket, Long> {
}
