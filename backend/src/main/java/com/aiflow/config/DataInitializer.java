package com.aiflow.config;

import com.aiflow.model.BizTypeDict;
import com.aiflow.repository.BizTypeDictRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** Initializes stable reference data. Demo users and market assets live in separate runners. */
@Slf4j
@Order(10)
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "aiflow.initializer", name = "reference-data-enabled",
        havingValue = "true", matchIfMissing = true)
public class DataInitializer implements CommandLineRunner {

    private final BizTypeDictRepository bizTypeDictRepository;

    @Override
    @Transactional
    public void run(String... args) {
        BizTypeDict hrAdmin = ensureBizType(null, "hr_admin", "人事行政类",
                "人事、行政相关流程分类", 10);
        BizTypeDict finance = ensureBizType(null, "finance", "财务类",
                "财务相关流程分类", 20);
        BizTypeDict logistics = ensureBizType(null, "logistics", "后勤类",
                "后勤保障相关流程分类", 30);
        BizTypeDict management = ensureBizType(null, "management", "管理类",
                "经营管理相关流程分类", 40);

        ensureBizType(hrAdmin.getId(), "leave", "请假", "员工请假流程", 11);
        ensureBizType(hrAdmin.getId(), "business_trip", "出差", "员工出差流程", 12);
        ensureBizType(finance.getId(), "reimbursement", "报销", "费用报销流程", 21);
        ensureBizType(logistics.getId(), "purchase", "采购", "采购申请流程", 31);
        ensureBizType(logistics.getId(), "repair", "报修", "设备、网络和办公设施报修流程", 32);
        ensureBizType(logistics.getId(), "inspection", "巡检", "巡检上报与复核流程", 33);
        ensureBizType(management.getId(), "work_report", "报备", "日常工作报备流程", 42);
        ensureBizType(management.getId(), "general_approval", "通用审批", "通用业务审批流程", 43);
        ensureBizType(management.getId(), "contract_approval", "合同审批", "合同审批流程", 41);
    }

    private BizTypeDict ensureBizType(Long parentId, String code, String name,
                                      String description, int sortOrder) {
        return bizTypeDictRepository.findByTypeCode(code).orElseGet(() -> {
            LocalDateTime now = LocalDateTime.now();
            BizTypeDict item = BizTypeDict.builder()
                    .parentId(parentId)
                    .typeCode(code)
                    .typeName(name)
                    .description(description)
                    .sortOrder(sortOrder)
                    .enabled(1)
                    .createdAt(now)
                    .updatedAt(now)
                    .deleted(0)
                    .build();
            BizTypeDict saved = bizTypeDictRepository.save(item);
            log.info("Initialized business type {} ({})", name, code);
            return saved;
        });
    }
}
