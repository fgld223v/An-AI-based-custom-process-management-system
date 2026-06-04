package com.aiflow.repository;

import com.aiflow.model.BizTypeDict;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BizTypeDictRepository extends JpaRepository<BizTypeDict, Long> {
    Optional<BizTypeDict> findByTypeCode(String typeCode);
}
