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
        Department deptTech = initDepartment(null, "tech", "技术部", 10, null);
        Department deptFinance = initDepartment(null, "finance_dept", "财务部", 20, null);
        Department deptHR = initDepartment(null, "hr", "人事行政部", 30, null);
        Department deptMarket = initDepartment(null, "market", "市场部", 40, null);

        SysUser admin = ensureDefaultAdmin(deptTech.getId());
        SysUser bizAdmin = ensureBizAdmin(deptFinance.getId());
        ensureNormalUser("user1", "普通员工 张三", deptHR.getId(), admin.getId());
        ensureNormalUser("user2", "普通员工 李四", deptMarket.getId(), bizAdmin.getId());

        initBizTypes();
    }

    private Department initDepartment(Long parentId, String code, String name, int sort, Long leaderId) {
        Department dept = departmentRepository.findFirstByDeptCode(code);
        LocalDateTime now = LocalDateTime.now();
        if (dept == null) {
            dept = Department.builder()
                    .parentId(parentId)
                    .deptCode(code)
                    .createdAt(now)
                    .deleted(0)
                    .build();
        }
        dept.setDeptName(name);
        dept.setSortOrder(sort);
        dept.setLeaderUserId(leaderId);
        dept.setStatus(1);
        dept.setUpdatedAt(now);
        Department saved = departmentRepository.save(dept);
        log.info("部门已就绪: {} (id={})", name, saved.getId());
        return saved;
    }

    /** 超级管理员：admin/admin123 */
    private SysUser ensureDefaultAdmin(Long deptId) {
        SysUser user = sysUserRepository.findByUsername("admin").orElse(null);
        if (user == null) {
            user = SysUser.builder().username("admin").createdTime(LocalDateTime.now()).build();
        }
        user.setPassword(passwordEncoder.encode("admin123"));
        user.setNickname("系统管理员");
        user.setRole("ADMIN");
        user.setSystemRole("super_admin");
        user.setDepartmentId(deptId);
        user.setSupervisorId(null);
        user.setManagedBizTypeIds(null);
        user.setEnabled(1);
        user.setDeleted(0);
        user.setUpdatedTime(LocalDateTime.now());
        SysUser saved = sysUserRepository.save(user);
        log.info("超级管理员 admin 已就绪 (dept=技术部, id={})", saved.getId());
        return saved;
    }

    /** 业务管理员：bizadmin/bizadmin123 */
    private SysUser ensureBizAdmin(Long deptId) {
        SysUser user = sysUserRepository.findByUsername("bizadmin").orElse(null);
        if (user == null) {
            user = SysUser.builder().username("bizadmin").createdTime(LocalDateTime.now()).build();
        }
        user.setPassword(passwordEncoder.encode("bizadmin123"));
        user.setNickname("业务管理员");
        user.setRole("MANAGER");
        user.setSystemRole("biz_admin");
        user.setDepartmentId(deptId);
        user.setSupervisorId(null);
        user.setManagedBizTypeIds("[2,3]");
        user.setEnabled(1);
        user.setDeleted(0);
        user.setUpdatedTime(LocalDateTime.now());
        SysUser saved = sysUserRepository.save(user);
        log.info("业务管理员 bizadmin 已就绪 (dept=财务部, managedBizTypeIds=[2,3], id={})", saved.getId());
        return saved;
    }

    /** 普通用户：user1/user123、user2/user123 */
    private SysUser ensureNormalUser(String username, String nickname, Long deptId, Long supervisorId) {
        SysUser user = sysUserRepository.findByUsername(username).orElse(null);
        if (user == null) {
            user = SysUser.builder().username(username).createdTime(LocalDateTime.now()).build();
        }
        user.setPassword(passwordEncoder.encode("user123"));
        user.setNickname(nickname);
        user.setRole("USER");
        user.setSystemRole("normal_user");
        user.setDepartmentId(deptId);
        user.setSupervisorId(supervisorId);
        user.setManagedBizTypeIds(null);
        user.setEnabled(1);
        user.setDeleted(0);
        user.setUpdatedTime(LocalDateTime.now());
        SysUser saved = sysUserRepository.save(user);
        log.info("普通用户 {} 已就绪 (deptId={}, supervisorId={}, id={})", username, deptId, supervisorId, saved.getId());
        return saved;
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
        BizTypeDict item = bizTypeDictRepository.findByTypeCode(typeCode).orElse(null);
        LocalDateTime now = LocalDateTime.now();
        if (item == null) {
            item = BizTypeDict.builder()
                    .typeCode(typeCode)
                    .createdAt(now)
                    .deleted(0)
                    .build();
        }
        item.setParentId(parentId);
        item.setTypeName(typeName);
        item.setDescription(description);
        item.setSortOrder(sortOrder);
        item.setEnabled(1);
        item.setUpdatedAt(now);
        return bizTypeDictRepository.save(item);
    }
}
