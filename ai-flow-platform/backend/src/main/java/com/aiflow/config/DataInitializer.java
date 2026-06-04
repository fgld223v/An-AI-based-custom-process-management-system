package com.aiflow.config;

import com.aiflow.enums.BizTypeEnabled;
import com.aiflow.enums.SystemRole;
import com.aiflow.enums.UserStatus;
import com.aiflow.model.BizTypeDict;
import com.aiflow.model.User;
import com.aiflow.repository.BizTypeDictRepository;
import com.aiflow.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Component
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final BizTypeDictRepository bizTypeDictRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(UserRepository userRepository, 
                          BizTypeDictRepository bizTypeDictRepository,
                          PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.bizTypeDictRepository = bizTypeDictRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) throws Exception {
        // 初始化业务类型数据
        initBizTypeDict();
        
        // 初始化默认超级管理员
        initAdminUser();
    }

    private void initBizTypeDict() {
        if (bizTypeDictRepository.count() > 0) {
            return;
        }

        // 先插入一级分类（父类型）
        List<BizTypeDict> parentTypes = Arrays.asList(
            BizTypeDict.builder()
                .typeCode("hr")
                .typeName("人事行政类")
                .parentId(null)
                .enabled(BizTypeEnabled.ENABLED)
                .sortOrder(1)
                .description("人事管理、行政管理相关业务")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build(),
            BizTypeDict.builder()
                .typeCode("finance")
                .typeName("财务类")
                .parentId(null)
                .enabled(BizTypeEnabled.ENABLED)
                .sortOrder(2)
                .description("财务管理、报销结算相关业务")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build(),
            BizTypeDict.builder()
                .typeCode("logistics")
                .typeName("后勤类")
                .parentId(null)
                .enabled(BizTypeEnabled.ENABLED)
                .sortOrder(3)
                .description("后勤保障、物资管理相关业务")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build(),
            BizTypeDict.builder()
                .typeCode("management")
                .typeName("管理类")
                .parentId(null)
                .enabled(BizTypeEnabled.ENABLED)
                .sortOrder(4)
                .description("企业管理、审批决策相关业务")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build()
        );

        bizTypeDictRepository.saveAll(parentTypes);

        // 再插入二级分类（子类型）
        // 人事行政类子类型 (parent_id = 1)
        List<BizTypeDict> childTypes = Arrays.asList(
            BizTypeDict.builder()
                .typeCode("hr_leave")
                .typeName("请假")
                .parentId(1L)
                .enabled(BizTypeEnabled.ENABLED)
                .sortOrder(1)
                .description("事假、病假、年假等请假申请")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build(),
            BizTypeDict.builder()
                .typeCode("hr_ot")
                .typeName("加班")
                .parentId(1L)
                .enabled(BizTypeEnabled.ENABLED)
                .sortOrder(2)
                .description("加班申请与审批")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build(),
            BizTypeDict.builder()
                .typeCode("hr_attendance")
                .typeName("考勤")
                .parentId(1L)
                .enabled(BizTypeEnabled.ENABLED)
                .sortOrder(3)
                .description("考勤异常处理、打卡补录")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build(),
            // 财务类子类型 (parent_id = 2)
            BizTypeDict.builder()
                .typeCode("finance_reimbursement")
                .typeName("报销")
                .parentId(2L)
                .enabled(BizTypeEnabled.ENABLED)
                .sortOrder(1)
                .description("费用报销申请")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build(),
            // 后勤类子类型 (parent_id = 3)
            BizTypeDict.builder()
                .typeCode("logistics_repair")
                .typeName("报修")
                .parentId(3L)
                .enabled(BizTypeEnabled.ENABLED)
                .sortOrder(1)
                .description("设施设备报修申请")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build()
        );

        bizTypeDictRepository.saveAll(childTypes);

        System.out.println("业务类型数据初始化完成，共插入 " + (parentTypes.size() + childTypes.size()) + " 条记录");
    }

    private void initAdminUser() {
        if (userRepository.findByUsername("admin").isPresent()) {
            return;
        }

        User admin = User.builder()
            .username("admin")
            .passwordHash(passwordEncoder.encode("admin123"))
            .realName("系统管理员")
            .systemRole(SystemRole.SUPER_ADMIN)
            .status(UserStatus.NORMAL)
            .loginCount(0)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

        userRepository.save(admin);
        System.out.println("默认超级管理员创建完成: username=admin, password=admin123");
    }
}
