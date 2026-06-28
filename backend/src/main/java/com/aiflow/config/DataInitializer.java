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

/**
 * 业务类型字典数据初始化器。
 * <p>
 * 在应用启动后自动执行，负责向数据库插入稳定的参考数据——
 * 4 个一级业务大类（人事行政、财务、后勤、管理）以及其下的 9 个具体流程编码。
 * 所有插入操作采用"存在则跳过"的幂等策略，可安全重复执行。
 * </p>
 * <p>
 * 通过 {@code aiflow.initializer.reference-data-enabled} 配置项控制是否启用，
 * 默认开启。演示用户和市场资产由其他独立的 Runner 负责初始化。
 * </p>
 */
@Slf4j
@Order(10)
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "aiflow.initializer", name = "reference-data-enabled",
        havingValue = "true", matchIfMissing = true)
public class DataInitializer implements CommandLineRunner {

    private final BizTypeDictRepository bizTypeDictRepository;

    /**
     * 应用启动后执行：逐层初始化 4 个一级分类及其下的子类型。
     * <p>
     * 执行流程：
     * <ol>
     *   <li>创建 4 个顶级业务分类（parentId = null）</li>
     *   <li>在对应父分类下插入具体的业务类型编码</li>
     * </ol>
     * 每一步都通过 {@link #ensureBizType} 保证幂等性。
     * </p>
     *
     * @param args 命令行参数（未使用）
     */
    @Override
    @Transactional
    public void run(String... args) {
        // --- 一级分类：4 个业务大类 ---
        BizTypeDict hrAdmin = ensureBizType(null, "hr_admin", "人事行政类",
                "人事、行政相关流程分类", 10);
        BizTypeDict finance = ensureBizType(null, "finance", "财务类",
                "财务相关流程分类", 20);
        BizTypeDict logistics = ensureBizType(null, "logistics", "后勤类",
                "后勤保障相关流程分类", 30);
        BizTypeDict management = ensureBizType(null, "management", "管理类",
                "经营管理相关流程分类", 40);

        // --- 二级分类：各业务大类下的具体流程类型 ---
        // 人事行政类下属
        ensureBizType(hrAdmin.getId(), "leave", "请假", "员工请假流程", 11);
        ensureBizType(hrAdmin.getId(), "business_trip", "出差", "员工出差流程", 12);
        // 财务类下属
        ensureBizType(finance.getId(), "reimbursement", "报销", "费用报销流程", 21);
        // 后勤类下属
        ensureBizType(logistics.getId(), "purchase", "采购", "采购申请流程", 31);
        ensureBizType(logistics.getId(), "repair", "报修", "设备、网络和办公设施报修流程", 32);
        ensureBizType(logistics.getId(), "inspection", "巡检", "巡检上报与复核流程", 33);
        // 管理类下属
        ensureBizType(management.getId(), "work_report", "报备", "日常工作报备流程", 42);
        ensureBizType(management.getId(), "general_approval", "通用审批", "通用业务审批流程", 43);
        ensureBizType(management.getId(), "contract_approval", "合同审批", "合同审批流程", 41);
    }

    /**
     * 幂等地确保指定编码的业务类型记录存在。
     * <p>
     * 先按 {@code typeCode} 查询数据库，若已存在则直接返回；
     * 否则创建新的 {@link BizTypeDict} 记录并持久化。
     * </p>
     *
     * @param parentId    父分类 ID，可为 null（表示顶级分类）
     * @param code        业务类型唯一编码
     * @param name        业务类型显示名称
     * @param description 业务类型描述
     * @param sortOrder   排序权重（数值越小越靠前）
     * @return 已存在或新创建的 {@link BizTypeDict} 实体
     */
    private BizTypeDict ensureBizType(Long parentId, String code, String name,
                                      String description, int sortOrder) {
        // 按编码查询，存在则返回；不存在则通过 orElseGet 懒创建
        return bizTypeDictRepository.findByTypeCode(code).orElseGet(() -> {
            LocalDateTime now = LocalDateTime.now();
            BizTypeDict item = BizTypeDict.builder()
                    .parentId(parentId)
                    .typeCode(code)
                    .typeName(name)
                    .description(description)
                    .sortOrder(sortOrder)
                    .enabled(1)         // 默认启用
                    .createdAt(now)
                    .updatedAt(now)
                    .deleted(0)         // 未删除
                    .build();
            BizTypeDict saved = bizTypeDictRepository.save(item);
            log.info("Initialized business type {} ({})", name, code);
            return saved;
        });
    }
}
