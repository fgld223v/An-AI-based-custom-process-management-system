package com.aiflow.repository;

import com.aiflow.model.ProcessInstance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProcessInstanceRepository extends JpaRepository<ProcessInstance, Long> {

    Optional<ProcessInstance> findByIdAndDeleted(Long id, Integer deleted);

    List<ProcessInstance> findByTemplateIdAndDeleted(Long templateId, Integer deleted);

    @Query("""
            select instance
            from ProcessInstance instance
            where instance.deleted = 0
              and (:templateId is null or instance.templateId = :templateId)
              and (:status is null or :status = '' or instance.status = :status)
              and (
                :keyword is null
                or :keyword = ''
                or lower(instance.instanceTitle) like lower(concat('%', :keyword, '%'))
                or lower(instance.instanceCode) like lower(concat('%', :keyword, '%'))
              )
            order by instance.updateTime desc, instance.createTime desc
            """)
    List<ProcessInstance> searchInstances(@Param("templateId") Long templateId,
                                          @Param("status") String status,
                                          @Param("keyword") String keyword);
}
