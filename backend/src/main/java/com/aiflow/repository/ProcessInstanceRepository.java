package com.aiflow.repository;

import com.aiflow.model.ProcessInstance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProcessInstanceRepository extends JpaRepository<ProcessInstance, Long> {

    Optional<ProcessInstance> findByIdAndDeleted(Long id, Integer deleted);

    @Query("""
            select p from ProcessInstance p
            where p.deleted = 0
              and (:templateId is null or p.templateId = :templateId)
              and (:status is null or :status = '' or p.status = :status)
              and (:keyword is null or :keyword = ''
                   or lower(p.title) like lower(concat('%', :keyword, '%'))
                   or lower(p.instanceCode) like lower(concat('%', :keyword, '%')))
            order by p.updatedAt desc, p.createdAt desc
            """)
    List<ProcessInstance> listInstances(@Param("templateId") Long templateId,
                                        @Param("status") String status,
                                        @Param("keyword") String keyword);
}