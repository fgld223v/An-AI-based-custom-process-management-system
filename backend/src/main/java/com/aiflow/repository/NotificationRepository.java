package com.aiflow.repository;

import com.aiflow.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Optional<Notification> findByIdAndDeleted(Long id, Integer deleted);

    @Query("""
            select n from Notification n
            where n.deleted = 0
              and (:receiverId is null or n.receiverId = :receiverId)
              and (:type is null or :type = '' or n.type = :type)
              and (:isRead is null or n.isRead = :isRead)
              and (:keyword is null or :keyword = ''
                   or lower(n.title) like lower(concat('%', :keyword, '%'))
                   or lower(n.content) like lower(concat('%', :keyword, '%')))
            order by n.createdAt desc, n.updatedAt desc
            """)
    List<Notification> listNotifications(@Param("receiverId") Long receiverId,
                                         @Param("type") String type,
                                         @Param("isRead") Boolean isRead,
                                         @Param("keyword") String keyword);

    long countByReceiverIdAndIsReadAndDeleted(Long receiverId, Boolean isRead, Integer deleted);

    boolean existsByTypeAndTargetTypeAndDeleted(String type, String targetType, Integer deleted);

    boolean existsByTypeAndReceiverIdAndTargetTypeAndDeleted(
            String type, Long receiverId, String targetType, Integer deleted);
}
