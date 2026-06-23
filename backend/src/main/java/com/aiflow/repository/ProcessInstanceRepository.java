package com.aiflow.repository;

import com.aiflow.enums.ProcessResourceType;
import com.aiflow.model.ProcessInstance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProcessInstanceRepository extends JpaRepository<ProcessInstance, Long> {

    boolean existsByTemplateIdAndDeleted(Long templateId, Integer deleted);

    Optional<ProcessInstance> findByIdAndDeleted(Long id, Integer deleted);

    Optional<ProcessInstance> findByFlowableProcessInstanceIdAndDeleted(String flowableProcessInstanceId, Integer deleted);

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

    @Query("""
            select p from ProcessInstance p, ProcessTemplate t
            where t.id = p.templateId
              and p.deleted = 0
              and t.resourceType = :resourceType
              and t.createdBy = :createdBy
              and (:templateId is null or p.templateId = :templateId)
              and (:status is null or :status = '' or p.status = :status)
              and (:keyword is null or :keyword = ''
                   or lower(p.title) like lower(concat('%', :keyword, '%'))
                   or lower(p.instanceCode) like lower(concat('%', :keyword, '%'))
                   or lower(t.templateName) like lower(concat('%', :keyword, '%'))
                   or lower(t.templateCode) like lower(concat('%', :keyword, '%')))
            order by p.updatedAt desc, p.createdAt desc
            """)
    List<ProcessInstance> listInstancesOwnedByTemplateCreator(
            @Param("createdBy") Long createdBy,
            @Param("resourceType") ProcessResourceType resourceType,
            @Param("templateId") Long templateId,
            @Param("status") String status,
            @Param("keyword") String keyword);

    @Query("""
            select p from ProcessInstance p, ProcessTemplate t
            where t.id = p.templateId
              and p.id = :instanceId
              and p.deleted = 0
              and t.resourceType = :resourceType
              and t.createdBy = :createdBy
            """)
    Optional<ProcessInstance> findOwnedInstance(
            @Param("instanceId") Long instanceId,
            @Param("createdBy") Long createdBy,
            @Param("resourceType") ProcessResourceType resourceType);

    @Query("""
            select p from ProcessInstance p, ProcessTemplate t
            where t.id = p.templateId
              and p.deleted = 0
              and (:templateId is null or p.templateId = :templateId)
              and (:status is null or :status = '' or p.status = :status)
              and (:keyword is null or :keyword = ''
                   or lower(p.title) like lower(concat('%', :keyword, '%'))
                   or lower(p.instanceCode) like lower(concat('%', :keyword, '%'))
                   or lower(t.templateName) like lower(concat('%', :keyword, '%'))
                   or lower(t.templateCode) like lower(concat('%', :keyword, '%')))
            order by p.updatedAt desc, p.createdAt desc
            """)
    List<ProcessInstance> listGlobalProcessInstances(
            @Param("templateId") Long templateId,
            @Param("status") String status,
            @Param("keyword") String keyword);

    @Query("""
            select p from ProcessInstance p, ProcessTemplate t
            where t.id = p.templateId
              and p.id = :instanceId
              and p.deleted = 0
            """)
    Optional<ProcessInstance> findGlobalProcessInstance(@Param("instanceId") Long instanceId);
}
