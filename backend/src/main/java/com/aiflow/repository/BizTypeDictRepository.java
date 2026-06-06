package com.aiflow.repository;

import com.aiflow.model.BizTypeDict;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BizTypeDictRepository extends JpaRepository<BizTypeDict, Long> {

    boolean existsByTypeCode(String typeCode);

    Optional<BizTypeDict> findByTypeCode(String typeCode);

    List<BizTypeDict> findByDeletedAndEnabledOrderBySortOrderAsc(Integer deleted, Integer enabled);
}
