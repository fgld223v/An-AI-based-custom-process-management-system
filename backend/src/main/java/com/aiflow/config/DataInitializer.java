package com.aiflow.config;

import com.aiflow.model.BizTypeDict;
import com.aiflow.model.Department;
import com.aiflow.model.SysUser;
import com.aiflow.repository.BizTypeDictRepository;
import com.aiflow.repository.DepartmentRepository;
import com.aiflow.repository.SysUserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Order(10)
@Component
public class DataInitializer implements CommandLineRunner {

    private final SysUserRepository sysUserRepository;
    private final BizTypeDictRepository bizTypeDictRepository;
    private final DepartmentRepository departmentRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(SysUserRepository sysUserRepository,
                           BizTypeDictRepository bizTypeDictRepository,
                           DepartmentRepository departmentRepository,
                           PasswordEncoder passwordEncoder) {
        this.sysUserRepository = sysUserRepository;
        this.bizTypeDictRepository = bizTypeDictRepository;
        this.departmentRepository = departmentRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        Department deptTech = initDepartment(null, "tech", "技术部", "技术研发与运维", 10, null);
        Department deptFinance = initDepartment(null, "finance_dept", "财务部", "财务管理与会计核算", 20, null);
        Department deptHR = initDepartment(null, "hr", "人事行政部", "人力资源与行政管理", 30, null);
        Department deptMarket = initDepartment(null, "market", "市场部", "市场营销与业务拓展", 40, null);

        // 设置部门负责人
        ensureDefaultAdmin(deptTech.getId());
        ensureBizAdmin(deptFinance.getId());
        ensureNormalUser(deptHR.getId(), deptTech.getId());
        ensureNormalUser2(deptMarket.getId(), deptFinance.getId());

        initBizTypes();
    }

    private Department initDepartment(Long parentId, String code, String name, String desc, int sort, Long leaderId) {
        Department existing = departmentRepository.findFirstByDeptCode(code);
        if (existing != null) return existing;
        LocalDateTime now = LocalDateTime.now();
        Department dept = Department.builder()
                .parentId(parentId).deptCode(code).deptName(name)
                .sortOrder(sort).leaderUserId(leaderId).status(1)
                .createdAt(now).updatedAt(now).deleted(0)
                .build();
        Department saved = departmentRepository.save(dept);
        log.info("创建部门: {} (id={})", name, saved.getId());
        return saved;
    }

    /** 超管 — admin/admin123，归属技术部 */
    private void ensureDefaultAdmin(Long deptId) {
        SysUser user = sysUserRepository.findByUsername("admin").orElse(null);
        if (user == null) {
            user = SysUser.builder().username("admin").createdTime(LocalDateTime.now()).build();
        }
        user.setPassword(passwordEncoder.encode("admin123"));
        user.setNickname("系统管理员");
        user.setRole("ADMIN");
        user.setSystemRole("super_admin");
        user.setDepartmentId(deptId);
        user.setEnabled(1);
        user.setDeleted(0);
        user.setUpdatedTime(LocalDateTime.now());
        sysUserRepository.save(user);
        log.info("超管 admin 已就绪 (dept=技术部, id={})", user.getId());
    }

    /** 业务管理员 — bizadmin/bizadmin123，归属财务部，管辖财务类和后勤类业务 */
    private void ensureBizAdmin(Long deptId) {
        SysUser user = sysUserRepository.findByUsername("bizadmin").orElse(null);
        if (user == null) {
            user = SysUser.builder().username("bizadmin").createdTime(LocalDateTime.now()).build();
        }
        user.setPassword(passwordEncoder.encode("bizadmin123"));
        user.setNickname("业务管理员");
        user.setRole("MANAGER");
        user.setSystemRole("biz_admin");
        user.setDepartmentId(deptId);
        user.setManagedBizTypeIds("[2,3]"); // 财务类(id=2) + 后勤类(id=3)
        user.setEnabled(1);
        user.setDeleted(0);
        user.setUpdatedTime(LocalDateTime.now());
        sysUserRepository.save(user);
        log.info("业务管理员 bizadmin 已就绪 (dept=财务部, managedBizTypeIds=[2,3], id={})", user.getId());
    }

    /** 普通用户1 — user1/user123，归属人事行政部，上级是超管 */
    private void ensureNormalUser(Long deptId, Long supervisorId) {
        SysUser user = sysUserRepository.findByUsername("user1").orElse(null);
        if (user == null) {
            user = SysUser.builder().username("user1").createdTime(LocalDateTime.now()).build();
        }
        user.setPassword(passwordEncoder.encode("user123"));
        user.setNickname("普通员工-张三");
        user.setRole("USER");
        user.setSystemRole("normal_user");
        user.setDepartmentId(deptId);
        user.setSupervisorId(supervisorId);
        user.setEnabled(1);
        user.setDeleted(0);
        user.setUpdatedTime(LocalDateTime.now());
        sysUserRepository.save(user);
        log.info("普通用户 user1 已就绪 (dept=人事行政部, supervisor=admin, id={})", user.getId());
    }

    /** 普通用户2 — user2/user123，归属市场部，上级是业务管理员 */
    private void ensureNormalUser2(Long deptId, Long supervisorId) {
        SysUser user = sysUserRepository.findByUsername("user2").orElse(null);
        if (user == null) {
            user = SysUser.builder().username("user2").createdTime(LocalDateTime.now()).build();
        }
        user.setPassword(passwordEncoder.encode("user123"));
        user.setNickname("普通员工-李四");
        user.setRole("USER");
        user.setSystemRole("normal_user");
        user.setDepartmentId(deptId);
        user.setSupervisorId(supervisorId);
        user.setEnabled(1);
        user.setDeleted(0);
        user.setUpdatedTime(LocalDateTime.now());
        sysUserRepository.save(user);
        log.info("普通用户 user2 已就绪 (dept=市场部, supervisor=bizadmin, id={})", user.getId());
    }

    private void initBizTypes() {
        BizTypeDict hrAdmin = initBizType(null, "hr_admin", "人事行政类", "人事、行政相关流程分类", 10);
        BizTypeDict finance = initBizType(null, "finance", "财务类", "财务相关流程分类", 20);
        BizTypeDict logistics = initBizType(null, "logistics", "后勤类", "后勤保障相关流程分类", 30);
        BizTypeDict management = initBizType(null, "management", "管理类", "经营管理相关流程分类", 40);

        initBizType(hrAdmin.getId(), "leave", "请假", "员工请假流程", 11);
        initBizType(hrAdmin.getId(), "business_trip", "出差", "员工出差流程", 12);
        initBizType(finance.getId(), "reimbursement", "报销", "费用报销流程", 21);
        initBizType(logistics.getId(), "purchase", "采购", "采购申请流程", 31);
        initBizType(management.getId(), "contract_approval", "合同审批", "合同审批流程", 41);
    }

    private BizTypeDict initBizType(Long parentId, String typeCode, String typeName, String description, Integer sortOrder) {
        return bizTypeDictRepository.findByTypeCode(typeCode)
                .orElseGet(() -> {
                    LocalDateTime now = LocalDateTime.now();
                    BizTypeDict item = BizTypeDict.builder()
                            .parentId(parentId)
                            .typeCode(typeCode)
                            .typeName(typeName)
                            .description(description)
                            .sortOrder(sortOrder)
                            .enabled(1)
                            .createdAt(now)
                            .updatedAt(now)
                            .deleted(0)
                            .build();
                    return bizTypeDictRepository.save(item);
                });
    }
}
